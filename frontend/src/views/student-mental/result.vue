<template>
  <div class="page-container student-result-page">
    <div class="page-header">
      <div>
        <el-button link :icon="ArrowLeft" class="back-link" @click="goBack">
          返回测评列表
        </el-button>
        <h1 class="page-title">
          {{ data?.questionnaire?.title || '评估结果' }}
        </h1>
        <div class="page-subtitle">查看本次心理测评结果与作答明细。</div>
      </div>
      <el-button :icon="Clock" @click="router.push('/student-mental/history')">
        历史评估
      </el-button>
    </div>

    <div v-loading="loading" class="result-layout">
      <section v-if="data?.assessment" :class="['result-panel', bannerClass]">
        <div class="result-metric">
          <span>总分</span>
          <strong>{{ data.assessment.score ?? '-' }}</strong>
        </div>
        <div class="result-metric">
          <span>评级</span>
          <strong>{{ data.assessment.level || '-' }}</strong>
        </div>
        <div class="suggestion-card">
          <span>建议</span>
          <p>{{ data.assessment.suggestion || '暂无建议' }}</p>
        </div>
      </section>

      <el-card class="table-card answer-card">
        <template #header>
          <div class="card-header">
            <div>
              <span>我的作答</span>
              <span class="result-count">共 {{ answerView.length }} 题</span>
            </div>
          </div>
        </template>

        <el-empty v-if="!loading && answerView.length === 0" description="暂无作答明细" />
        <div v-else class="answer-list">
          <article v-for="(item, idx) in answerView" :key="idx" class="answer-item">
            <div class="answer-question">
              <span>{{ idx + 1 }}</span>
              <strong>{{ item.content }}</strong>
              <el-tag size="small" effect="plain">
                {{ typeLabel(item.questionType) }}
              </el-tag>
            </div>
            <div class="answer-content">
              <span v-if="item.answerLabels.length">
                {{ item.answerLabels.join('、') }}
              </span>
              <span v-else-if="item.text">{{ item.text }}</span>
              <span v-else class="muted">未作答</span>
            </div>
          </article>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Clock } from '@element-plus/icons-vue'
import { studentGetMyAssessment, studentGetQuestionnaireForTaking } from '@/api/mental'
import { useUserStore } from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const assessmentId = Number(route.params.assessmentId)

const loading = ref(false)
const data = ref(null)
const questions = ref([])

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

const safeParseAnswers = (raw) => {
  if (!raw) return []
  try {
    return typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch {
    return []
  }
}

const answerView = computed(() => {
  if (!data.value || !questions.value.length) return []
  const answers = safeParseAnswers(data.value.answersJson)
  const map = {}
  answers.forEach((answer) => {
    map[answer.questionId] = answer
  })

  return questions.value.map((question) => {
    const answer = map[question.id] || {}
    const opts = parseOpts(question.options)
    const labels = (answer.optionIndices || []).map((idx) => opts[idx]?.label).filter(Boolean)
    return {
      content: question.content,
      questionType: question.questionType,
      answerLabels: labels,
      text: answer.text || ''
    }
  })
})

const bannerClass = computed(() => {
  const level = data.value?.assessment?.level
  return (
    {
      正常: 'lvl-good',
      轻度: 'lvl-mild',
      中度: 'lvl-warn',
      重度: 'lvl-danger',
      高危: 'lvl-danger'
    }[level] || 'lvl-neutral'
  )
})

const fetchData = async () => {
  loading.value = true
  try {
    const userId = userStore.userInfo?.id
    const res = await studentGetMyAssessment(userId, assessmentId)
    data.value = res.data || {}
    if (data.value.questionnaire?.id) {
      const full = await studentGetQuestionnaireForTaking(data.value.questionnaire.id)
      questions.value = full.data?.questions || []
    }
  } catch (e) {
    console.error('加载结果失败', e)
  } finally {
    loading.value = false
  }
}

const goBack = () => router.push('/student-mental/list')

onMounted(fetchData)
</script>

<style scoped lang="scss">
.student-result-page {
  .back-link {
    padding: 0;
    margin-bottom: 8px;
  }
}

.result-layout {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.result-panel {
  display: grid;
  grid-template-columns: 160px 160px minmax(0, 1fr);
  gap: 14px;
  padding: 18px;
  background: var(--bg-color-container);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-1);

  &.lvl-good {
    border-color: #bbf7d0;
  }

  &.lvl-mild {
    border-color: #bfdbfe;
  }

  &.lvl-warn {
    border-color: #fed7aa;
  }

  &.lvl-danger {
    border-color: #fecaca;
  }
}

.result-metric,
.suggestion-card {
  padding: 14px;
  background: #f8fafc;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);

  span {
    display: block;
    color: var(--text-color-secondary);
    font-size: 13px;
  }

  strong {
    display: block;
    margin-top: 8px;
    color: var(--text-color);
    font-size: 28px;
    line-height: 1;
  }

  p {
    margin: 8px 0 0;
    color: var(--text-color-regular);
    line-height: 1.7;
  }
}

.answer-card {
  :deep(.el-card__body) {
    padding-top: 0;
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .result-count {
    margin-left: 10px;
    color: var(--text-color-muted);
    font-size: 12px;
    font-weight: 400;
  }
}

.answer-list {
  display: flex;
  flex-direction: column;
}

.answer-item {
  padding: 16px 0;
  border-bottom: 1px solid var(--border-color);

  &:last-child {
    border-bottom: none;
  }
}

.answer-question {
  display: flex;
  align-items: flex-start;
  gap: 10px;

  span:first-child {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex: 0 0 auto;
    width: 26px;
    height: 26px;
    color: var(--primary-color);
    background: var(--primary-color-light);
    border-radius: 4px;
    font-weight: 700;
  }

  strong {
    flex: 1;
    color: var(--text-color);
    line-height: 1.6;
  }
}

.answer-content {
  margin-top: 10px;
  padding-left: 36px;
  color: var(--text-color-regular);
  line-height: 1.7;
}

.muted {
  color: var(--text-color-muted);
}

@media (max-width: 900px) {
  .result-panel {
    grid-template-columns: 1fr;
  }
}
</style>
