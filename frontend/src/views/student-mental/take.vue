<template>
  <div class="page-container">
    <el-alert v-if="loadError" type="error" :closable="false" :title="loadError"
      ><el-button link @click="fetchData">重新加载</el-button></el-alert
    >
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <el-button link @click="$router.push('/student-mental/list')">← 返回</el-button>
          <span class="title">{{ questionnaire?.title || '作答' }}</span>
        </div>
      </template>

      <div v-if="questionnaire" class="intro">
        <el-tag size="small">{{ questionnaire.type }}</el-tag>
        <p>{{ questionnaire.description }}</p>
      </div>

      <el-form @submit.prevent>
        <div v-for="(q, idx) in questions" :key="q.id" class="q-block">
          <div class="q-title">
            <span class="num">{{ idx + 1 }}.</span>
            <span>{{ q.content }}</span>
            <el-tag v-if="q.required" type="danger" size="small" effect="plain">必答</el-tag>
            <el-tag size="small">{{ typeLabel(q.questionType) }}</el-tag>
          </div>

          <div class="q-input">
            <!-- 单选 -->
            <el-radio-group
              v-if="q.questionType === 'single_choice'"
              v-model="answers[q.id].singleIndex"
            >
              <el-radio
                v-for="(opt, i) in parseOpts(q.options)"
                :key="i"
                :value="i"
                :label="i"
                style="display: block; margin: 6px 0"
                >{{ opt.label }}</el-radio
              >
            </el-radio-group>

            <!-- 多选 -->
            <el-checkbox-group
              v-else-if="q.questionType === 'multiple_choice'"
              v-model="answers[q.id].multiIndices"
            >
              <el-checkbox
                v-for="(opt, i) in parseOpts(q.options)"
                :key="i"
                :value="i"
                :label="i"
                style="display: block; margin: 6px 0"
                >{{ opt.label }}</el-checkbox
              >
            </el-checkbox-group>

            <!-- 简答 -->
            <el-input
              v-else-if="q.questionType === 'text'"
              v-model="answers[q.id].text"
              type="textarea"
              :rows="3"
              placeholder="请输入..."
            />

            <div v-else-if="q.questionType === 'scale'">
              <span>{{ parseScaleLabels(q.scaleLabels).min }}</span>
              <el-input-number
                v-model="answers[q.id].scaleValue"
                :min="q.scaleMin"
                :max="q.scaleMax"
                :step="1"
                step-strictly
              />
              <span>{{ parseScaleLabels(q.scaleLabels).max }}</span>
              <small>范围：{{ q.scaleMin }} – {{ q.scaleMax }}</small>
            </div>
            <el-alert
              v-else
              type="error"
              :closable="false"
              title="暂不支持此题型，请联系管理员修正问卷"
            />
          </div>
        </div>

        <div class="submit-bar">
          <el-button @click="$router.push('/student-mental/list')">取消</el-button>
          <el-button
            type="primary"
            :disabled="loading || !questions.length || loadedQuestionnaireId !== questionnaireId"
            :loading="submitting"
            @click="handleSubmit"
            >提交答卷</el-button
          >
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { studentGetQuestionnaireForTaking, studentSubmitAnswers } from '@/api/mental'
import { useUserStore } from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const questionnaireId = computed(() => Number(route.params.id))
const loadedQuestionnaireId = ref<number | null>(null)
let loadSequence = 0

const loading = ref(false)
const loadError = ref('')
const submitting = ref(false)
const questionnaire = ref<any>(null)
const questions = ref<any[]>([])
const answers = reactive<Record<number, any>>({})

const typeLabel = (t: string) =>
  (({ single_choice: '单选', multiple_choice: '多选', text: '简答', scale: '量表' }) as any)[t] || t

const parseOpts = (raw: any): any[] => {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    return JSON.parse(raw)
  } catch {
    return []
  }
}
const parseScaleLabels = (raw: any) => {
  try {
    return JSON.parse(raw || '{}')
  } catch {
    return {}
  }
}

const fetchData = async () => {
  const id = questionnaireId.value
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  try {
    const res = await studentGetQuestionnaireForTaking(id)
    if (sequence !== loadSequence || id !== questionnaireId.value) return
    const d = res.data
    questionnaire.value = d.questionnaire
    questions.value = d.questions || []
    questions.value.forEach((q: any) => {
      answers[q.id] = { singleIndex: null, multiIndices: [], text: '', scaleValue: null }
    })
    loadedQuestionnaireId.value = id
  } catch (e) {
    if (sequence === loadSequence) loadError.value = (e as any)?.message || '数据加载失败，请重试'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

const handleSubmit = async () => {
  const id = questionnaireId.value
  if (
    loadedQuestionnaireId.value !== id ||
    loading.value ||
    submitting.value ||
    !questions.value.length
  )
    return
  for (const q of questions.value) {
    if (!q.required) continue
    const a = answers[q.id]
    if (
      q.questionType === 'single_choice' &&
      (a.singleIndex === null || a.singleIndex === undefined)
    ) {
      ElMessage.warning(`第 ${q.sortOrder} 题为必答`)
      return
    }
    if (q.questionType === 'multiple_choice' && (!a.multiIndices || a.multiIndices.length === 0)) {
      ElMessage.warning(`第 ${q.sortOrder} 题为必答`)
      return
    }
    if (q.questionType === 'text' && !a.text?.trim()) {
      ElMessage.warning(`第 ${q.sortOrder} 题为必答`)
      return
    }
    if (
      q.questionType === 'scale' &&
      (!Number.isInteger(a.scaleValue) || a.scaleValue < q.scaleMin || a.scaleValue > q.scaleMax)
    ) {
      ElMessage.warning(`第 ${q.sortOrder} 题需填写范围内的整数`)
      return
    }
  }
  const userId = userStore.userInfo?.id
  if (!userId) {
    ElMessage.error('未登录')
    return
  }
  const payload = {
    userId,
    questionnaireId: id,
    answers: questions.value.map((q: any) => {
      const a = answers[q.id]
      let optionIndices: number[] = []
      if (q.questionType === 'single_choice' && a.singleIndex !== null)
        optionIndices = [a.singleIndex]
      else if (q.questionType === 'multiple_choice') optionIndices = a.multiIndices || []
      return {
        questionId: q.id,
        optionIndices,
        text: a.text || '',
        scaleValue: q.questionType === 'scale' ? a.scaleValue : null
      }
    })
  }
  submitting.value = true
  try {
    const res = await studentSubmitAnswers(payload)
    ElMessage.success('提交成功')
    if (id === questionnaireId.value) router.push(`/student-mental/result/${res.data.id}`)
  } catch (e) {
    console.error('提交失败', e)
  } finally {
    submitting.value = false
  }
}

watch(
  questionnaireId,
  () => {
    questionnaire.value = null
    questions.value = []
    loadedQuestionnaireId.value = null
    for (const key of Object.keys(answers)) delete answers[Number(key)]
    fetchData()
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  align-items: center;
  .title {
    font-size: 16px;
    font-weight: 600;
    margin-left: 8px;
  }
}
.intro {
  margin-bottom: 16px;
  p {
    color: var(--el-text-color-secondary);
    margin: 6px 0 0;
  }
}
.q-block {
  padding: 12px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
  &:last-of-type {
    border-bottom: none;
  }
}
.q-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 8px;
  .num {
    color: var(--el-color-primary);
  }
}
.q-input {
  padding-left: 20px;
}
.submit-bar {
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
