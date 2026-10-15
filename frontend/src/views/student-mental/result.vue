<template>
  <div class="page-container">
    <el-alert v-if="loadError" type="error" :closable="false" :title="loadError"
      ><el-button link @click="fetchData">重新加载</el-button></el-alert
    >
    <el-card v-loading="loading">
      <template #header>
        <div class="card-header">
          <el-button link @click="goBack">← 返回</el-button>
          <span class="title">{{ data?.questionnaire?.title || '评估结果' }}</span>
        </div>
      </template>

      <div v-if="data?.assessment" class="result-banner" :class="bannerClass">
        <div v-if="canSeeRaw" class="score">
          <div class="num">{{ data.assessment.score }}</div>
          <div class="label">总分</div>
        </div>
        <div v-if="canSeeLevel" class="level">
          <div class="num">{{ data.assessment.level }}</div>
          <div class="label">评级</div>
        </div>
        <div v-if="canSeeRaw" class="suggestion">
          <div class="title-tag">建议</div>
          <div>{{ data.assessment.suggestion ?? '未提供建议' }}</div>
        </div>
      </div>
      <el-alert
        v-if="data?.assessment && !canSeeRaw"
        title="测评记录已保存。原始分数和专业建议按角色权限提供，此处不将不可见字段解释为零分或无建议。"
        type="info"
        :closable="false"
      />
      <el-alert
        v-if="data && !data.historySnapshotAvailable"
        :title="data.historyNotice || '历史记录缺少提交时题目快照'"
        type="warning"
        :closable="false"
      />

      <el-divider>我的作答</el-divider>
      <div v-for="(item, idx) in answerView" :key="idx" class="q-block">
        <div class="q-title">
          <span class="num">{{ idx + 1 }}.</span>
          {{ item.content }}
          <el-tag size="small">{{ typeLabel(item.questionType) }}</el-tag>
        </div>
        <div class="q-answer">
          <span v-if="item.answerLabels.length">{{ item.answerLabels.join('、') }}</span>
          <span v-else-if="item.scaleValue !== null && item.scaleValue !== undefined">{{
            item.scaleValue
          }}</span>
          <span v-else-if="item.text">{{ item.text }}</span>
          <span v-else style="color: var(--el-text-color-secondary)">未作答</span>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { studentGetMyAssessment } from '@/api/mental'
import { useUserStore } from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const assessmentId = computed(() => Number(route.params.assessmentId))
let loadSequence = 0
const canSeeRaw = computed(() =>
  userStore.userRoles.some((role: string) => ['admin', 'psychologist'].includes(role))
)
const canSeeLevel = computed(() =>
  userStore.userRoles.some((role: string) =>
    ['admin', 'psychologist', 'teacher', 'counselor', 'academic_advisor'].includes(role)
  )
)

const loading = ref(false)
const loadError = ref('')
const data = ref<any>(null)
const questions = ref<any[]>([])

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

const answerView = computed(() => {
  if (!data.value || !questions.value.length) return []
  let answers: any[] = []
  try {
    answers = data.value.answersJson ? JSON.parse(data.value.answersJson) : []
  } catch {
    return []
  }
  const map: Record<number, any> = {}
  answers.forEach((a: any) => (map[a.questionId] = a))

  return questions.value.map((q: any) => {
    const a = map[q.id] || {}
    const opts = parseOpts(q.options)
    const labels = (a.optionIndices || []).map((idx: number) => opts[idx]?.label).filter(Boolean)
    return {
      content: q.content,
      questionType: q.questionType,
      answerLabels: labels,
      text: a.text || '',
      scaleValue: a.scaleValue
    }
  })
})

const bannerClass = computed(() => {
  const lvl = data.value?.assessment?.level
  return (
    (
      {
        正常: 'lvl-good',
        轻度: 'lvl-mild',
        中度: 'lvl-warn',
        重度: 'lvl-danger',
        高危: 'lvl-danger'
      } as any
    )[lvl] || ''
  )
})

const fetchData = async () => {
  const id = assessmentId.value
  const sequence = ++loadSequence
  loading.value = true
  loadError.value = ''
  try {
    const userId = userStore.userInfo?.id
    const res = await studentGetMyAssessment(userId, id)
    if (sequence !== loadSequence || id !== assessmentId.value) return
    data.value = res.data
    questions.value = data.value.questions || []
  } catch (e) {
    if (sequence === loadSequence) loadError.value = (e as any)?.message || '数据加载失败，请重试'
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

const goBack = () => router.push('/student-mental/list')

watch(
  assessmentId,
  () => {
    data.value = null
    questions.value = []
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
.result-banner {
  display: flex;
  gap: 32px;
  padding: 24px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  margin-bottom: 16px;
  align-items: center;

  .score,
  .level {
    text-align: center;
    .num {
      font-size: 36px;
      font-weight: 600;
    }
    .label {
      color: var(--el-text-color-secondary);
      font-size: 13px;
    }
  }
  .suggestion {
    flex: 1;
    .title-tag {
      font-size: 13px;
      color: var(--el-text-color-secondary);
      margin-bottom: 4px;
    }
  }
  &.lvl-good {
    background: #f0f9eb;
    .level .num {
      color: #67c23a;
    }
  }
  &.lvl-mild {
    background: #ecf5ff;
    .level .num {
      color: #409eff;
    }
  }
  &.lvl-warn {
    background: #fdf6ec;
    .level .num {
      color: #e6a23c;
    }
  }
  &.lvl-danger {
    background: #fef0f0;
    .level .num {
      color: #f56c6c;
    }
  }
}
.q-block {
  padding: 10px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}
.q-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 6px;
  .num {
    color: var(--el-color-primary);
    margin-right: 4px;
  }
}
.q-answer {
  padding-left: 20px;
  color: var(--el-text-color-regular);
}
</style>
