<template>
  <div class="page-container">
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

            <!-- 量表（按文本回退处理，仅展示，不计分） -->
            <el-input v-else v-model="answers[q.id].text" placeholder="（量表题，请输入数字）" />
          </div>
        </div>

        <div class="submit-bar">
          <el-button @click="$router.push('/student-mental/list')">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交答卷</el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { studentGetQuestionnaireForTaking, studentSubmitAnswers } from '@/api/mental'
import { useUserStore } from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const questionnaireId = Number(route.params.id)

const loading = ref(false)
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

const fetchData = async () => {
  loading.value = true
  try {
    const res = await studentGetQuestionnaireForTaking(questionnaireId)
    const d = res.data
    questionnaire.value = d.questionnaire
    questions.value = d.questions || []
    questions.value.forEach((q: any) => {
      answers[q.id] = { singleIndex: null, multiIndices: [], text: '' }
    })
  } catch (e) {
    console.error('加载题目失败', e)
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
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
  }
  const userId = userStore.userInfo?.id
  if (!userId) {
    ElMessage.error('未登录')
    return
  }
  const payload = {
    userId,
    questionnaireId,
    answers: questions.value.map((q: any) => {
      const a = answers[q.id]
      let optionIndices: number[] = []
      if (q.questionType === 'single_choice' && a.singleIndex !== null)
        optionIndices = [a.singleIndex]
      else if (q.questionType === 'multiple_choice') optionIndices = a.multiIndices || []
      return {
        questionId: q.id,
        optionIndices,
        text: a.text || ''
      }
    })
  }
  submitting.value = true
  try {
    const res = await studentSubmitAnswers(payload)
    ElMessage.success('提交成功')
    router.push(`/student-mental/result/${res.data.id}`)
  } catch (e) {
    console.error('提交失败', e)
  } finally {
    submitting.value = false
  }
}

onMounted(fetchData)
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
