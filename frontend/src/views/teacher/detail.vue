<template>
  <div class="page-container">
    <el-page-header title="教师详情" @back="goBack" />

    <el-row :gutter="20" class="detail-content">
      <el-col :xs="24" :lg="8">
        <el-card v-loading="loading">
          <div class="profile-header">
            <el-avatar :size="100" :src="teacherInfo.avatar" />
            <h3>{{ teacherInfo.name }}</h3>
            <p class="subtitle">{{ teacherInfo.dept }} · {{ teacherInfo.title }}</p>
            <el-tag :type="teacherInfo.status === '在职' ? 'success' : 'info'">
              {{ teacherInfo.status }}
            </el-tag>
          </div>

          <el-divider />

          <div class="info-list">
            <div class="info-item">
              <span class="label">工号：</span>
              <span class="value">{{ teacherInfo.employeeId }}</span>
            </div>
            <div class="info-item">
              <span class="label">入职时间：</span>
              <span class="value">{{ teacherInfo.joinDate }}</span>
            </div>
            <div class="info-item">
              <span class="label">联系电话：</span>
              <span class="value">{{ teacherInfo.phone }}</span>
            </div>
            <div class="info-item">
              <span class="label">电子邮箱：</span>
              <span class="value">{{ teacherInfo.email }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="16">
        <el-card v-loading="loading">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="基本信息" name="basic">
              <el-descriptions :column="2" border>
                <el-descriptions-item label="姓名">{{ teacherInfo.name }}</el-descriptions-item>
                <el-descriptions-item label="性别">{{ teacherInfo.gender }}</el-descriptions-item>
                <el-descriptions-item label="出生日期">{{
                  teacherInfo.birthDate
                }}</el-descriptions-item>
                <el-descriptions-item label="政治面貌">{{
                  teacherInfo.political
                }}</el-descriptions-item>
                <el-descriptions-item label="学历" :span="2">{{
                  teacherInfo.education
                }}</el-descriptions-item>
                <el-descriptions-item label="毕业院校" :span="2">{{
                  teacherInfo.school
                }}</el-descriptions-item>
                <el-descriptions-item label="专业方向" :span="2">{{
                  teacherInfo.major
                }}</el-descriptions-item>
                <el-descriptions-item label="研究方向" :span="2">{{
                  teacherInfo.researchArea
                }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>

            <el-tab-pane label="教学成果" name="teaching">
              <el-timeline>
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
              <el-table :data="researchProjects" stripe>
                <el-table-column prop="name" label="项目名称" min-width="200" />
                <el-table-column prop="level" label="级别" width="120" />
                <el-table-column prop="role" label="角色" width="100" />
                <el-table-column prop="amount" label="经费" width="120">
                  <template #default="{ row }"> ¥{{ row.amount }}万 </template>
                </el-table-column>
                <el-table-column prop="period" label="周期" width="180" />
              </el-table>
            </el-tab-pane>

            <el-tab-pane label="教学评价" name="evaluation">
              <div class="evaluation-stats">
                <el-row :gutter="20">
                  <el-col :span="8">
                    <div class="stat-item">
                      <div class="stat-value">{{ teacherInfo.evaluationScore }}</div>
                      <div class="stat-label">综合评分</div>
                    </div>
                  </el-col>
                  <el-col :span="8">
                    <div class="stat-item">
                      <div class="stat-value">{{ teacherInfo.evaluationCount }}</div>
                      <div class="stat-label">评价次数</div>
                    </div>
                  </el-col>
                  <el-col :span="8">
                    <div class="stat-item">
                      <div class="stat-value">{{ teacherInfo.satisfaction }}%</div>
                      <div class="stat-label">满意度</div>
                    </div>
                  </el-col>
                </el-row>
              </div>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { getTeacherDetail } from '@/api/teacher'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const activeTab = ref('basic')
const loading = ref(false)

const teacherInfo = ref({
  name: '',
  avatar: '',
  dept: '',
  title: '',
  status: '',
  employeeId: '',
  joinDate: '',
  phone: '',
  email: '',
  gender: '',
  birthDate: '',
  political: '',
  education: '',
  school: '',
  major: '',
  researchArea: '',
  evaluationScore: '-',
  evaluationCount: '-',
  satisfaction: '-'
})

const teachingAchievements = ref([])
const researchProjects = ref([])

const formatGender = (val) => {
  if (val === 0) return '女'
  if (val === 1) return '男'
  return '-'
}

const formatStatus = (val) => {
  if (val === 0) return '离职'
  if (val === 1) return '在职'
  return '-'
}

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
      return
    }
    teacherInfo.value = {
      ...data,
      dept: data.deptName || '-',
      gender: formatGender(data.gender),
      status: formatStatus(data.status),
      political: '-',
      phone: '-',
      email: '-',
      evaluationScore: '-',
      evaluationCount: '-',
      satisfaction: '-'
    }
  } catch (error) {
    ElMessage.error('获取教师详情失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDetail()
})

const goBack = () => {
  router.back()
}
</script>

<style scoped lang="scss">
.detail-content {
  margin-top: 20px;
}

.profile-header {
  text-align: center;
  padding: 20px 0;

  h3 {
    margin: 16px 0 8px;
    font-size: 20px;
  }

  .subtitle {
    color: rgba(0, 0, 0, 0.45);
    margin-bottom: 12px;
  }
}

.info-list {
  .info-item {
    display: flex;
    padding: 12px 0;
    border-bottom: 1px solid #f0f0f0;

    &:last-child {
      border-bottom: none;
    }

    .label {
      width: 80px;
      color: rgba(0, 0, 0, 0.45);
    }

    .value {
      flex: 1;
      color: rgba(0, 0, 0, 0.85);
    }
  }
}

.evaluation-stats {
  padding: 20px;

  .stat-item {
    text-align: center;
    padding: 20px;
    background: #f6ffed;
    border-radius: 8px;

    .stat-value {
      font-size: 32px;
      font-weight: 600;
      color: #52c41a;
    }

    .stat-label {
      margin-top: 8px;
      color: rgba(0, 0, 0, 0.45);
    }
  }
}
</style>
