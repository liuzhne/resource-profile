<template>
  <div class="page-container student-history-page">
    <div class="page-header">
      <div>
        <el-button
          link
          :icon="ArrowLeft"
          class="back-link"
          @click="router.push('/student-mental/list')"
        >
          返回测评列表
        </el-button>
        <h1 class="page-title">我的评估历史</h1>
        <div class="page-subtitle">查看过往心理测评记录与结果建议。</div>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="fetchData"> 刷新 </el-button>
    </div>

    <div class="history-summary">
      <div class="summary-card">
        <span>评估次数</span>
        <strong>{{ rows.length }}</strong>
        <p>当前账号历史记录</p>
      </div>
      <div class="summary-card is-success">
        <span>正常</span>
        <strong>{{ levelSummary.good }}</strong>
        <p>结果为正常等级</p>
      </div>
      <div class="summary-card is-warning">
        <span>需关注</span>
        <strong>{{ levelSummary.attention }}</strong>
        <p>轻度或中度记录</p>
      </div>
      <div class="summary-card is-danger">
        <span>高危/重度</span>
        <strong>{{ levelSummary.high }}</strong>
        <p>建议联系辅导员</p>
      </div>
    </div>

    <el-card class="table-card history-table-card">
      <template #header>
        <div class="card-header">
          <div>
            <span>历史记录</span>
            <span class="result-count">共 {{ rows.length }} 条</span>
          </div>
        </div>
      </template>

      <el-empty v-if="!loading && rows.length === 0" description="暂无评估记录" />
      <el-table v-else v-loading="loading" :data="rows" stripe>
        <el-table-column type="index" width="56" />
        <el-table-column prop="questionnaireId" label="问卷" min-width="120">
          <template #default="{ row }">
            <div class="questionnaire-id">问卷 #{{ row.questionnaireId }}</div>
            <div class="time-text">{{ row.createTime || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="得分" width="100" align="center">
          <template #default="{ row }">
            <strong class="score-value">{{ row.score ?? '-' }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="120">
          <template #default="{ row }">
            <el-tag :type="levelTagColor(row.level)" effect="plain">
              {{ row.level || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="suggestion" label="建议" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">{{ row.suggestion || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :icon="View"
              @click="router.push(`/student-mental/result/${row.id}`)"
            >
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Refresh, View } from '@element-plus/icons-vue'
import { studentMyHistory } from '@/api/mental'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const rows = ref([])

const levelSummary = computed(() => ({
  good: rows.value.filter((row) => row.level === '正常').length,
  attention: rows.value.filter((row) => ['轻度', '中度'].includes(row.level)).length,
  high: rows.value.filter((row) => ['重度', '高危'].includes(row.level)).length
}))

const levelTagColor = (level) =>
  ({
    正常: 'success',
    轻度: 'primary',
    中度: 'warning',
    重度: 'danger',
    高危: 'danger'
  })[level] || 'info'

const fetchData = async () => {
  loading.value = true
  try {
    const res = await studentMyHistory(userStore.userInfo?.id)
    rows.value = res.data || []
  } catch (e) {
    console.error('加载历史失败', e)
  } finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>

<style scoped lang="scss">
.student-history-page {
  .back-link {
    padding: 0;
    margin-bottom: 8px;
  }
}

.history-summary {
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

  &.is-success strong {
    color: #2f9e44;
  }

  &.is-warning strong {
    color: #d9822b;
  }

  &.is-danger strong {
    color: #d64545;
  }
}

.history-table-card {
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

.questionnaire-id {
  color: var(--text-color);
  font-weight: 650;
  line-height: 1.5;
}

.time-text {
  color: var(--text-color-muted);
  font-size: 12px;
}

.score-value {
  color: var(--primary-color);
}

@media (max-width: 1100px) {
  .history-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .history-summary {
    grid-template-columns: 1fr;
  }
}
</style>
