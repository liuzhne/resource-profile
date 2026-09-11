<template>
  <div class="page-container student-mental-list-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">我的心理测评</h1>
        <div class="page-subtitle">查看当前可填写问卷、测评开放时间与已完成记录。</div>
      </div>
      <div class="page-actions">
        <el-button :icon="Refresh" :loading="loading" @click="fetchData"> 刷新 </el-button>
        <el-button
          type="primary"
          plain
          :icon="Clock"
          @click="router.push('/student-mental/history')"
        >
          历史评估
        </el-button>
      </div>
    </div>

    <div class="summary-grid">
      <div class="summary-card">
        <span>问卷总数</span>
        <strong>{{ list.length }}</strong>
        <p>当前账号可见测评</p>
      </div>
      <div class="summary-card is-primary">
        <span>待作答</span>
        <strong>{{ summary.available }}</strong>
        <p>在开放时间内</p>
      </div>
      <div class="summary-card is-success">
        <span>已完成</span>
        <strong>{{ summary.answered }}</strong>
        <p>可查看评估结果</p>
      </div>
      <div class="summary-card is-muted">
        <span>未开放</span>
        <strong>{{ summary.closed }}</strong>
        <p>当前不可作答</p>
      </div>
    </div>

    <div v-loading="loading" class="questionnaire-grid">
      <el-empty v-if="!loading && list.length === 0" description="当前没有可作答的问卷" />

      <article v-for="item in list" v-else :key="item.id" class="questionnaire-card">
        <div class="card-top">
          <el-tag effect="plain">{{ item.type || '-' }}</el-tag>
          <el-tag :type="stateType(item)" effect="plain">
            {{ stateLabel(item) }}
          </el-tag>
        </div>
        <h2>{{ item.title || '未命名问卷' }}</h2>
        <p>{{ item.description || '暂无描述' }}</p>

        <div class="meta-list">
          <div>
            <span>题目数量</span>
            <strong>{{ item.questions || 0 }} 题</strong>
          </div>
          <div>
            <span>开放时间</span>
            <strong>{{ timeRange(item) }}</strong>
          </div>
        </div>

        <div class="card-actions">
          <el-button
            v-if="!item.answered && item.inWindow"
            type="primary"
            :icon="EditPen"
            @click="goTake(item.id)"
          >
            去作答
          </el-button>
          <el-button v-else-if="item.answered" :icon="View" @click="findAndOpenResult(item.id)">
            查看结果
          </el-button>
          <el-button v-else disabled>暂不可作答</el-button>
        </div>
      </article>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Clock, EditPen, Refresh, View } from '@element-plus/icons-vue'
import { studentListQuestionnaires, studentMyHistory } from '@/api/mental'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const userStore = useUserStore()
const list = ref([])
const loading = ref(false)

const summary = computed(() => ({
  available: list.value.filter((item) => !item.answered && item.inWindow).length,
  answered: list.value.filter((item) => item.answered).length,
  closed: list.value.filter((item) => !item.answered && !item.inWindow).length
}))

const fetchData = async () => {
  loading.value = true
  try {
    const userId = userStore.userInfo?.id
    if (!userId) {
      ElMessage.error('未登录或缺少用户信息')
      return
    }
    const res = await studentListQuestionnaires(userId)
    list.value = res.data || []
  } catch (e) {
    console.error('加载问卷失败', e)
  } finally {
    loading.value = false
  }
}

const goTake = (id) => {
  router.push(`/student-mental/take/${id}`)
}

const findAndOpenResult = async (questionnaireId) => {
  try {
    const userId = userStore.userInfo?.id
    const res = await studentMyHistory(userId)
    const found = (res.data || []).find(
      (assessment) => assessment.questionnaireId === questionnaireId
    )
    if (found) {
      router.push(`/student-mental/result/${found.id}`)
    } else {
      ElMessage.warning('未找到该问卷的评估记录')
    }
  } catch (e) {
    console.error(e)
  }
}

const stateLabel = (item) => {
  if (item.answered) return '已作答'
  if (!item.inWindow) return '未开放'
  return '待作答'
}

const stateType = (item) => {
  if (item.answered) return 'success'
  if (!item.inWindow) return 'info'
  return 'primary'
}

const timeRange = (item) => `${item.startTime || '不限'} ~ ${item.endTime || '不限'}`

onMounted(fetchData)
</script>

<style scoped lang="scss">
.student-mental-list-page {
  .page-actions {
    display: flex;
    gap: 10px;
    flex-shrink: 0;
  }
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.summary-card {
  min-height: 112px;
  padding: 18px;
  background: var(--bg-color-container);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-1);

  span {
    display: block;
    color: var(--text-color-secondary);
    font-size: 13px;
  }

  strong {
    display: block;
    margin-top: 10px;
    color: var(--text-color);
    font-size: 28px;
    line-height: 1;
  }

  p {
    margin: 12px 0 0;
    color: var(--text-color-muted);
    font-size: 12px;
  }

  &.is-primary strong {
    color: var(--primary-color);
  }

  &.is-success strong {
    color: #2f9e44;
  }

  &.is-muted strong {
    color: #64748b;
  }
}

.questionnaire-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  min-height: 240px;
}

.questionnaire-card {
  display: flex;
  flex-direction: column;
  min-height: 250px;
  padding: 18px;
  background: var(--bg-color-container);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-1);

  .card-top {
    display: flex;
    justify-content: space-between;
    gap: 10px;
  }

  h2 {
    margin: 16px 0 8px;
    color: var(--text-color);
    font-size: 18px;
    line-height: 1.4;
  }

  p {
    margin: 0;
    color: var(--text-color-regular);
    line-height: 1.6;
    min-height: 46px;
  }
}

.meta-list {
  display: grid;
  grid-template-columns: 90px minmax(0, 1fr);
  gap: 12px;
  margin-top: 18px;

  span {
    display: block;
    color: var(--text-color-muted);
    font-size: 12px;
  }

  strong {
    display: block;
    margin-top: 4px;
    color: var(--text-color);
    font-size: 13px;
    font-weight: 650;
  }
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: auto;
  padding-top: 18px;
}

@media (max-width: 1180px) {
  .questionnaire-grid,
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .questionnaire-grid,
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
