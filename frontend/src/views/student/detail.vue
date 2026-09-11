<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <el-button text class="back-btn" @click="goBack">返回列表</el-button>
        <h1 class="page-title">学生画像详情</h1>
        <div class="page-subtitle">查看学生基础档案、学业状态与关注记录。</div>
      </div>
    </div>

    <el-skeleton v-if="loading" :rows="8" animated class="detail-content" />

    <el-empty v-else-if="!studentInfo" class="detail-content" description="未找到学生信息" />

    <el-row v-else :gutter="16" class="detail-content">
      <el-col :xs="24" :lg="8">
        <el-card class="profile-card">
          <div class="profile-header">
            <el-avatar :size="72" :src="studentInfo.avatar">
              {{ avatarText }}
            </el-avatar>
            <h3>{{ displayValue(studentInfo.name) }}</h3>
            <p class="subtitle">
              {{ displayValue(studentInfo.deptName) }} ·
              {{ displayValue(studentInfo.majorName) }}
            </p>
            <div class="profile-tags">
              <el-tag>{{ gradeLabel(studentInfo.grade) }}</el-tag>
              <el-tag :type="statusTagType(studentInfo.status)" effect="plain">
                {{ statusLabel(studentInfo.status) }}
              </el-tag>
            </div>
          </div>

          <el-divider />

          <div class="info-list">
            <div class="info-item">
              <span class="label">学号：</span>
              <span class="value">{{ displayValue(studentInfo.studentId) }}</span>
            </div>
            <div class="info-item">
              <span class="label">入学时间：</span>
              <span class="value">{{ displayValue(studentInfo.enrollmentDate) }}</span>
            </div>
            <div class="info-item">
              <span class="label">班级：</span>
              <span class="value">{{ displayValue(studentInfo.className) }}</span>
            </div>
            <div class="info-item">
              <span class="label">预计毕业：</span>
              <span class="value">{{ displayValue(studentInfo.expectedGraduation) }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="16">
        <el-card class="overview-card">
          <template #header>
            <div class="card-header">
              <span>画像概览</span>
              <span class="card-hint">数据来自学生档案接口</span>
            </div>
          </template>
          <el-row :gutter="12" class="grade-stats">
            <el-col :xs="24" :sm="8">
              <div class="stat-card">
                <div class="stat-value highlight">
                  {{ formatGpa(studentInfo.gpa) }}
                </div>
                <div class="stat-label">GPA</div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="8">
              <div class="stat-card">
                <div class="stat-value">
                  {{ displayValue(studentInfo.credits) }}
                </div>
                <div class="stat-label">已修学分</div>
              </div>
            </el-col>
            <el-col :xs="24" :sm="8">
              <div class="stat-card">
                <div class="stat-value">
                  {{ displayValue(studentInfo.className) }}
                </div>
                <div class="stat-label">行政班级</div>
              </div>
            </el-col>
          </el-row>
        </el-card>

        <el-card class="detail-tabs-card">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="学业成绩" name="grades">
              <div class="tab-section-title">最近学期成绩</div>
              <el-table
                :data="gradeRecords"
                stripe
                empty-text="暂无成绩记录"
                style="margin-top: 12px"
              >
                <el-table-column prop="semester" label="学期" />
                <el-table-column prop="course" label="课程" />
                <el-table-column prop="credit" label="学分" width="80" />
                <el-table-column prop="score" label="成绩" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.score >= 90 ? 'success' : row.score >= 60 ? '' : 'danger'">
                      {{ row.score }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="gpa" label="绩点" width="80" />
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="综合素质" name="quality">
              <el-empty v-if="qualityRecords.length === 0" description="暂无综合素质记录" />
              <el-timeline v-else>
                <el-timeline-item
                  v-for="(item, index) in qualityRecords"
                  :key="index"
                  :timestamp="item.date"
                  :type="item.type"
                >
                  <h4>{{ item.title }}</h4>
                  <p>{{ item.description }}</p>
                  <el-tag size="small">{{ item.category }}</el-tag>
                </el-timeline-item>
              </el-timeline>
            </el-tab-pane>

            <el-tab-pane label="心理健康" name="mental">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="最近测评">{{
                  displayValue(studentInfo.lastMentalTest)
                }}</el-descriptions-item>
                <el-descriptions-item label="测评结果">
                  <el-tag type="info">{{ displayValue(studentInfo.mentalStatus) }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="辅导员关注" :span="2">{{
                  studentInfo.counselorNotes || '暂无记录'
                }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getStudentDetail } from '@/api/student'

const router = useRouter()
const route = useRoute()
const activeTab = ref('grades')
const loading = ref(false)
const studentInfo = ref(null)
const gradeRecords = ref([])
const qualityRecords = ref([])

const avatarText = computed(() => {
  return studentInfo.value?.name ? studentInfo.value.name.slice(0, 1) : '学'
})

const displayValue = (value) => {
  return value === null || value === undefined || value === '' ? '-' : value
}

const gradeLabel = (grade) => {
  return grade ? `${grade}级` : '-'
}

const formatGpa = (gpa) => {
  const n = Number(gpa)
  return Number.isFinite(n) ? n.toFixed(2) : '-'
}

const statusLabel = (status) => ({ 0: '退学', 1: '在读', 2: '毕业' })[status] ?? '-'

const statusTagType = (status) => ({ 0: 'danger', 1: 'success', 2: 'info' })[status] ?? 'info'

const fetchDetail = async () => {
  loading.value = true
  try {
    const res = await getStudentDetail(route.params.id)
    studentInfo.value = res.data || null
  } catch (e) {
    console.error('获取学生详情失败', e)
    studentInfo.value = null
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
    gap: 10px;
    padding: 11px 0;
    border-bottom: 1px solid var(--border-color);

    &:last-child {
      border-bottom: none;
    }

    .label {
      width: 82px;
      color: var(--text-color-secondary);
      flex-shrink: 0;
    }

    .value {
      flex: 1;
      color: var(--text-color);
      word-break: break-word;
    }
  }
}

.overview-card {
  margin-bottom: 14px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  .card-hint {
    color: var(--text-color-muted);
    font-size: 12px;
    font-weight: 400;
  }
}

.grade-stats {
  .stat-card {
    min-height: 92px;
    padding: 16px;
    border: 1px solid var(--border-color);
    border-radius: var(--radius-base);
    background: var(--bg-color-subtle);

    .stat-value {
      color: var(--text-color);
      font-size: 24px;
      font-weight: 700;
      line-height: 1.2;
      word-break: break-word;

      &.highlight {
        color: var(--success-color);
      }
    }

    .stat-label {
      margin-top: 8px;
      color: var(--text-color-secondary);
      font-size: 13px;
    }
  }
}

.detail-tabs-card {
  .tab-section-title {
    color: var(--text-color);
    font-weight: 650;
  }
}

.back-btn {
  margin-left: -8px;
  margin-bottom: 4px;
}

@media (max-width: 768px) {
  .grade-stats {
    .el-col + .el-col {
      margin-top: 10px;
    }
  }
}
</style>
