<template>
  <div class="page-container take-assessment-page">
    <div class="page-header">
      <div>
        <el-button
          link
          :icon="ArrowLeft"
          class="back-link"
          @click="router.push('/student-mental/list')"
        >
          返回问卷列表
        </el-button>
        <h1 class="page-title">{{ questionnaire?.title || '心理测评' }}</h1>
        <div class="page-subtitle">
          {{ questionnaire?.description || '请完成当前心理测评问卷。' }}
        </div>
      </div>
      <div class="progress-card">
        <span>作答进度</span>
        <strong>{{ answeredCount }}/{{ questions.length }}</strong>
      </div>
    </div>

    <div v-loading="loading" class="assessment-layout">
      <aside class="assessment-aside">
        <div class="aside-card">
          <div class="aside-label">问卷信息</div>
          <div class="aside-title">{{ questionnaire?.type || '-' }}</div>
          <div class="aside-meta">共 {{ questions.length }} 题 · 必答 {{ requiredCount }} 题</div>
          <el-progress :percentage="progressPercent" :stroke-width="8" :show-text="false" />
          <div class="progress-text">{{ progressPercent }}% 已完成</div>
        </div>
      </aside>

      <main class="question-list">
        <el-empty v-if="!loading && questions.length === 0" description="暂无可作答题目" />

        <section
          v-for="(question, idx) in questions"
          v-else
          :key="question.id"
          class="question-card"
        >
          <div class="question-head">
            <div>
              <span class="question-index">{{ idx + 1 }}</span>
              <strong>{{ question.content }}</strong>
            </div>
            <div class="question-tags">
              <el-tag v-if="question.required" type="danger" size="small" effect="plain">
                必答
              </el-tag>
              <el-tag size="small" effect="plain">
                {{ typeLabel(question.questionType) }}
              </el-tag>
            </div>
          </div>

          <div class="question-input">
            <el-radio-group
              v-if="question.questionType === 'single_choice'"
              v-model="answers[question.id].singleIndex"
              class="option-group"
            >
              <el-radio
                v-for="(opt, i) in parseOpts(question.options)"
                :key="i"
                :value="i"
                :label="i"
              >
                {{ opt.label }}
              </el-radio>
            </el-radio-group>

            <el-checkbox-group
              v-else-if="question.questionType === 'multiple_choice'"
              v-model="answers[question.id].multiIndices"
              class="option-group"
            >
              <el-checkbox
                v-for="(opt, i) in parseOpts(question.options)"
                :key="i"
                :value="i"
                :label="i"
              >
                {{ opt.label }}
              </el-checkbox>
            </el-checkbox-group>

            <el-input
              v-else-if="question.questionType === 'text'"
              v-model="answers[question.id].text"
              type="textarea"
              :rows="4"
              placeholder="请输入"
            />

            <el-input v-else v-model="answers[question.id].text" placeholder="请输入数字或文字" />
          </div>
        </section>

        <div class="submit-bar">
          <el-button @click="router.push('/student-mental/list')"> 取消 </el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            提交答卷
          </el-button>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { studentGetQuestionnaireForTaking, studentSubmitAnswers } from '@/api/mental'
import { useUserStore } from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const questionnaireId = Number(route.params.id)

const loading = ref(false)
const submitting = ref(false)
const questionnaire = ref(null)
const questions = ref([])
const answers = reactive({})

const typeLabel = (type) =>
  ({
    single_choice: '单选',
    multiple_choice: '多选',
    text: '简答',
    scale: '量表'
  })[type] ||
  type ||
  '-'

const parseOpts = (raw) => {
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  try {
    return JSON.parse(raw)
  } catch {
    return []
  }
}

const requiredCount = computed(() => questions.value.filter((question) => question.required).length)

const isAnswered = (question) => {
  const answer = answers[question.id]
  if (!answer) return false
  if (question.questionType === 'single_choice') {
    return answer.singleIndex !== null && answer.singleIndex !== undefined
  }
  if (question.questionType === 'multiple_choice') {
    return Array.isArray(answer.multiIndices) && answer.multiIndices.length > 0
  }
  return !!answer.text?.trim()
}

const answeredCount = computed(
  () => questions.value.filter((question) => isAnswered(question)).length
)

const progressPercent = computed(() => {
  if (!questions.value.length) return 0
  return Math.round((answeredCount.value / questions.value.length) * 100)
})

const fetchData = async () => {
  loading.value = true
  try {
    const res = await studentGetQuestionnaireForTaking(questionnaireId)
    const data = res.data || {}
    questionnaire.value = data.questionnaire || null
    questions.value = data.questions || []
    questions.value.forEach((question) => {
      answers[question.id] = { singleIndex: null, multiIndices: [], text: '' }
    })
  } catch (e) {
    console.error('加载题目失败', e)
  } finally {
    loading.value = false
  }
}

const handleSubmit = async () => {
  for (const question of questions.value) {
    if (!question.required) continue
    if (!isAnswered(question)) {
      ElMessage.warning(`第 ${question.sortOrder || question.id} 题为必答`)
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
    answers: questions.value.map((question) => {
      const answer = answers[question.id]
      let optionIndices = []
      if (question.questionType === 'single_choice' && answer.singleIndex !== null) {
        optionIndices = [answer.singleIndex]
      } else if (question.questionType === 'multiple_choice') {
        optionIndices = answer.multiIndices || []
      }
      return {
        questionId: question.id,
        optionIndices,
        text: answer.text || ''
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
.take-assessment-page {
  .back-link {
    padding: 0;
    margin-bottom: 8px;
  }
}

.progress-card {
  min-width: 150px;
  padding: 14px 16px;
  background: var(--bg-color-container);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-1);

  span {
    display: block;
    color: var(--text-color-secondary);
    font-size: 12px;
  }

  strong {
    display: block;
    margin-top: 8px;
    color: var(--primary-color);
    font-size: 22px;
  }
}

.assessment-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
}

.assessment-aside {
  position: sticky;
  top: 0;
  align-self: start;
}

.aside-card {
  padding: 18px;
  background: var(--bg-color-container);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-1);
}

.aside-label {
  color: var(--text-color-secondary);
  font-size: 13px;
}

.aside-title {
  margin-top: 8px;
  color: var(--text-color);
  font-size: 18px;
  font-weight: 700;
}

.aside-meta,
.progress-text {
  margin-top: 10px;
  color: var(--text-color-muted);
  font-size: 12px;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.question-card {
  padding: 18px;
  background: var(--bg-color-container);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-1);
}

.question-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;

  > div:first-child {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    min-width: 0;
  }

  strong {
    color: var(--text-color);
    line-height: 1.6;
  }
}

.question-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
  width: 28px;
  height: 28px;
  color: var(--primary-color);
  background: var(--primary-color-light);
  border-radius: 4px;
  font-weight: 700;
}

.question-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.question-input {
  margin-top: 14px;
  padding-left: 38px;
}

.option-group {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 8px;
}

.submit-bar {
  position: sticky;
  bottom: 0;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 14px 0 0;
  background: var(--bg-color);
}

@media (max-width: 900px) {
  .assessment-layout {
    grid-template-columns: 1fr;
  }

  .assessment-aside,
  .submit-bar {
    position: static;
  }
}

@media (max-width: 720px) {
  .page-header,
  .question-head {
    flex-direction: column;
  }

  .question-input {
    padding-left: 0;
  }
}
</style>
