<template>
  <div class="page-container questionnaire-result-page">
    <div class="page-header">
      <div>
        <el-button link :icon="ArrowLeft" class="back-link" @click="goBack">
          返回问卷列表
        </el-button>
        <h1 class="page-title">{{ questionnaire?.title || '完成情况' }}</h1>
        <div class="page-subtitle">查看学生提交情况、测评等级与重点跟进对象。</div>
      </div>
      <el-tag v-if="questionnaire?.type" effect="plain">
        {{ questionnaire.type }}
      </el-tag>
    </div>

    <div class="result-summary">
      <div class="summary-card">
        <span>已完成</span>
        <strong>{{ rows.length }}</strong>
        <p>当前问卷提交人数</p>
      </div>
      <div v-permission="['psychologist', 'admin']" class="summary-card is-primary">
        <span>平均分</span>
        <strong>{{ avgScore ?? '-' }}</strong>
        <p>仅心理教师与管理员可见</p>
      </div>
      <div class="summary-card is-warning">
        <span>关注等级</span>
        <strong>{{ levelSummary.attention }}</strong>
        <p>轻度或中度学生</p>
      </div>
      <div class="summary-card is-danger">
        <span>高危/重度</span>
        <strong>{{ levelSummary.high }}</strong>
        <p>建议优先人工复核</p>
      </div>
    </div>

    <el-card class="table-card result-table-card">
      <template #header>
        <div class="card-header">
          <div>
            <span>提交明细</span>
            <span class="result-count">共 {{ rows.length }} 条</span>
          </div>
          <span class="header-hint">原始得分按角色权限展示</span>
        </div>
      </template>

      <el-empty v-if="!loading && rows.length === 0" description="暂无学生提交" />
      <el-table v-else v-loading="loading" :data="rows" stripe>
        <el-table-column type="index" label="#" width="56" />
        <el-table-column label="学生" min-width="160">
          <template #default="{ row }">
            <div class="student-name">{{ row.name || '-' }}</div>
            <div class="student-no">{{ row.studentNo || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="学院" min-width="150" show-overflow-tooltip />
        <el-table-column label="班级信息" min-width="150">
          <template #default="{ row }">
            <div>{{ row.grade || '-' }}级</div>
            <div class="student-no">{{ row.className || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="hasRole(['psychologist', 'admin'])"
          prop="score"
          label="得分"
          width="90"
          sortable
          align="center"
        >
          <template #default="{ row }">
            <strong class="score-value">{{ row.score ?? '-' }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="等级" width="110">
          <template #default="{ row }">
            <el-tag :type="levelTagColor(row.level)" effect="plain">
              {{ row.level || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="submitTime" label="提交时间" min-width="180">
          <template #default="{ row }">{{ row.submitTime || '-' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { getCompletionList, getQuestionnaireDetail } from '@/api/mental'
import { usePermission } from '@/directives/permission'

const { hasRole } = usePermission()

const route = useRoute()
const router = useRouter()
const questionnaireId = Number(route.params.id)

const loading = ref(false)
const rows = ref([])
const questionnaire = ref(null)

const avgScore = computed(() => {
  if (rows.value.length === 0) return null
  const sum = rows.value.reduce((acc, row) => acc + (Number(row.score) || 0), 0)
  return Math.round((sum / rows.value.length) * 10) / 10
})

const levelSummary = computed(() => ({
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
    const [meta, completion] = await Promise.all([
      getQuestionnaireDetail(questionnaireId),
      getCompletionList(questionnaireId)
    ])
    questionnaire.value = meta.data || null
    rows.value = completion.data || []
  } catch (e) {
    console.error('加载完成情况失败', e)
  } finally {
    loading.value = false
  }
}

const goBack = () => router.push('/mental/questionnaire')

onMounted(fetchData)
</script>

<style scoped lang="scss">
.questionnaire-result-page {
  .back-link {
    padding: 0;
    margin-bottom: 8px;
  }
}

.result-summary {
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

  &.is-warning strong {
    color: #d9822b;
  }

  &.is-danger strong {
    color: #d64545;
  }
}

.result-table-card {
  :deep(.el-card__body) {
    padding-top: 0;
  }
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  .result-count,
  .header-hint {
    color: var(--text-color-muted);
    font-size: 12px;
    font-weight: 400;
  }

  .result-count {
    margin-left: 10px;
  }
}

.student-name {
  color: var(--text-color);
  font-weight: 650;
  line-height: 1.5;
}

.student-no {
  color: var(--text-color-muted);
  font-size: 12px;
}

.score-value {
  color: var(--primary-color);
}

@media (max-width: 1100px) {
  .result-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .result-summary {
    grid-template-columns: 1fr;
  }
}
</style>
