<template>
  <div class="page-container teacher-detail-page">
    <div class="page-header">
      <div>
        <el-button text class="back-btn" @click="goBack">返回列表</el-button>
        <h1 class="page-title">教师画像详情</h1>
        <div class="page-subtitle">查看教师基础档案、任职信息与教学科研画像。</div>
      </div>
    </div>

    <el-skeleton v-if="loading" :rows="8" animated class="detail-content" />

    <el-empty v-else-if="!teacherInfo" class="detail-content" description="未找到教师信息" />

    <el-row v-else :gutter="16" class="detail-content">
      <el-col :xs="24" :lg="8">
        <el-card class="profile-card">
          <div class="profile-header">
            <el-avatar :size="72" :src="teacherInfo.avatar">
              {{ avatarText }}
            </el-avatar>
            <h3>{{ displayValue(teacherInfo.name) }}</h3>
            <p class="subtitle">
              {{ displayValue(teacherInfo.dept) }} ·
              {{ displayValue(teacherInfo.title) }}
            </p>
            <div class="profile-tags">
              <el-tag :type="titleTagType(teacherInfo.title)" effect="plain">
                {{ displayValue(teacherInfo.title) }}
              </el-tag>
              <el-tag :type="statusTagType(teacherInfo.status)" effect="plain">
                {{ displayValue(teacherInfo.status) }}
              </el-tag>
            </div>
          </div>

          <el-divider />

          <div class="info-list">
            <div class="info-item">
              <span class="label">工号</span>
              <span class="value">{{ displayValue(teacherInfo.employeeId) }}</span>
            </div>
            <div class="info-item">
              <span class="label">入职时间</span>
              <span class="value">{{ displayValue(teacherInfo.joinDate) }}</span>
            </div>
            <div class="info-item">
              <span class="label">联系电话</span>
              <span class="value">{{ displayValue(teacherInfo.phone) }}</span>
            </div>
            <div class="info-item">
              <span class="label">电子邮箱</span>
              <span class="value">{{ displayValue(teacherInfo.email) }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="16">
        <el-card class="overview-card">
          <template #header>
            <div class="card-header">
              <span>画像概览</span>
              <span class="card-hint">数据来自教师档案接口</span>
            </div>
          </template>
          <div class="stat-grid">
            <div class="stat-card">
              <div class="stat-value">
                {{ displayValue(teacherInfo.education) }}
              </div>
              <div class="stat-label">最高学历</div>
            </div>
            <div class="stat-card">
              <div class="stat-value">
                {{ displayValue(teacherInfo.major) }}
              </div>
              <div class="stat-label">专业方向</div>
            </div>
            <div class="stat-card">
              <div class="stat-value highlight">
                {{ displayValue(teacherInfo.evaluationScore) }}
              </div>
              <div class="stat-label">教学评分</div>
            </div>
          </div>
        </el-card>

        <el-card class="detail-tabs-card">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="基础档案" name="basic">
              <div class="info-grid">
                <div class="info-cell">
                  <span>姓名</span>
                  <strong>{{ displayValue(teacherInfo.name) }}</strong>
                </div>
                <div class="info-cell">
                  <span>性别</span>
                  <strong>{{ displayValue(teacherInfo.gender) }}</strong>
                </div>
                <div class="info-cell">
                  <span>出生日期</span>
                  <strong>{{ displayValue(teacherInfo.birthDate) }}</strong>
                </div>
                <div class="info-cell">
                  <span>政治面貌</span>
                  <strong>{{ displayValue(teacherInfo.political) }}</strong>
                </div>
                <div class="info-cell wide">
                  <span>毕业院校</span>
                  <strong>{{ displayValue(teacherInfo.school) }}</strong>
                </div>
                <div class="info-cell wide">
                  <span>研究方向</span>
                  <strong>{{ displayValue(teacherInfo.researchArea) }}</strong>
                </div>
              </div>
            </el-tab-pane>

            <el-tab-pane label="教学成果" name="teaching">
              <el-empty v-if="teachingAchievements.length === 0" description="暂无教学成果记录" />
              <el-timeline v-else>
                <el-timeline-item
                  v-for="(item, index) in teachingAchievements"
                  :key="index"
                  :timestamp="item.year"
                  type="primary"
                >
                  <h4>{{ item.title }}</h4>
                  <p>{{ item.description }}</p>
                </el-timeline-item>
              </el-timeline>
            </el-tab-pane>

            <el-tab-pane label="科研项目" name="research">
              <el-table :data="researchProjects" stripe empty-text="暂无科研项目记录">
                <el-table-column prop="name" label="项目名称" min-width="220" />
                <el-table-column prop="level" label="级别" width="120" />
                <el-table-column prop="role" label="角色" width="100" />
                <el-table-column prop="amount" label="经费" width="120">
                  <template #default="{ row }">
                    {{ row.amount ? `${row.amount} 万` : '-' }}
                  </template>
                </el-table-column>
                <el-table-column prop="period" label="周期" width="180" />
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="教学评价" name="evaluation">
              <div class="evaluation-grid">
                <div class="evaluation-card">
                  <strong>{{ displayValue(teacherInfo.evaluationScore) }}</strong>
                  <span>综合评分</span>
                </div>
                <div class="evaluation-card">
                  <strong>{{ displayValue(teacherInfo.evaluationCount) }}</strong>
                  <span>评价次数</span>
                </div>
                <div class="evaluation-card">
                  <strong>{{ satisfactionText }}</strong>
                  <span>满意度</span>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getTeacherDetail } from '@/api/teacher'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const activeTab = ref('basic')
const loading = ref(false)
const teacherInfo = ref(null)
const teachingAchievements = ref([])
const researchProjects = ref([])

const avatarText = computed(() =>
  teacherInfo.value?.name ? teacherInfo.value.name.slice(0, 1) : '师'
)

const satisfactionText = computed(() => {
  const value = teacherInfo.value?.satisfaction
  return value === '-' || value === undefined || value === null ? '-' : `${value}%`
})

const displayValue = (value) =>
  value === null || value === undefined || value === '' ? '-' : value

const formatGender = (value) => {
  if (value === 0) return '女'
  if (value === 1) return '男'
  return displayValue(value)
}

const formatStatus = (value) => {
  if (value === 0) return '离职'
  if (value === 1) return '在职'
  return displayValue(value)
}

const titleTagType = (title) => {
  if (title === '教授' || title === '研究员') return 'success'
  if (title === '副教授') return 'warning'
  if (title === '讲师') return 'primary'
  return 'info'
}

const statusTagType = (status) =>
  status === '在职' ? 'success' : status === '离职' ? 'info' : 'info'

const fetchDetail = async () => {
  const id = route.params.id
  if (!id) {
    ElMessage.error('教师ID不存在')
    return
  }
  loading.value = true
  try {
    const res = await getTeacherDetail(id)
    const data = res.data
    if (!data) {
      ElMessage.warning('未找到该教师信息')
      teacherInfo.value = null
      return
    }
    teacherInfo.value = {
      ...data,
      dept: data.deptName || data.dept || '-',
      gender: formatGender(data.gender),
      status: formatStatus(data.status),
      political: data.political || '-',
      phone: data.phone || '-',
      email: data.email || '-',
      evaluationScore: data.evaluationScore || '-',
      evaluationCount: data.evaluationCount || '-',
      satisfaction: data.satisfaction || '-'
    }
    teachingAchievements.value = data.teachingAchievements || []
    researchProjects.value = data.researchProjects || []
  } catch (error) {
    teacherInfo.value = null
    ElMessage.error('获取教师详情失败')
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.back()
}

onMounted(fetchDetail)
</script>

<style scoped lang="scss">
.detail-content {
  margin-top: 8px;
}

.profile-header {
  text-align: center;
  padding: 10px 0 6px;

  h3 {
    margin: 14px 0 6px;
    color: var(--text-color);
    font-size: 19px;
    font-weight: 700;
  }

  .subtitle {
    color: var(--text-color-secondary);
    margin-bottom: 12px;
    line-height: 1.5;
  }

  .profile-tags {
    display: flex;
    justify-content: center;
    gap: 8px;
  }
}

.info-list {
  .info-item {
    display: flex;
    justify-content: space-between;
    gap: 16px;
    padding: 12px 0;
    border-bottom: 1px solid var(--border-color);

    &:last-child {
      border-bottom: none;
    }

    .label {
      color: var(--text-color-secondary);
      white-space: nowrap;
    }

    .value {
      color: var(--text-color);
      text-align: right;
      word-break: break-word;
    }
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;

  .card-hint {
    color: var(--text-color-muted);
    font-size: 12px;
  }
}

.stat-grid,
.evaluation-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stat-card,
.evaluation-card {
  min-height: 92px;
  padding: 16px;
  background: #f8fafc;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);

  .stat-value,
  strong {
    display: block;
    color: var(--text-color);
    font-size: 20px;
    font-weight: 700;
    line-height: 1.3;
  }

  .highlight,
  strong {
    color: var(--primary-color);
  }

  .stat-label,
  span {
    display: block;
    margin-top: 8px;
    color: var(--text-color-muted);
    font-size: 12px;
  }
}

.overview-card {
  margin-bottom: 14px;
}

.detail-tabs-card {
  :deep(.el-card__body) {
    padding-top: 8px;
  }
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.info-cell {
  min-height: 78px;
  padding: 14px;
  background: #f8fafc;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);

  &.wide {
    grid-column: span 2;
  }

  span {
    display: block;
    color: var(--text-color-secondary);
    font-size: 12px;
  }

  strong {
    display: block;
    margin-top: 8px;
    color: var(--text-color);
    font-weight: 650;
  }
}

@media (max-width: 720px) {
  .stat-grid,
  .evaluation-grid,
  .info-grid {
    grid-template-columns: 1fr;
  }

  .info-cell.wide {
    grid-column: auto;
  }
}
</style>
