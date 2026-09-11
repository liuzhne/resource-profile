<template>
  <div class="page-container">
    <el-page-header title="学生详情" @back="goBack" />

    <el-row :gutter="20" class="detail-content">
      <el-col :xs="24" :lg="8">
        <el-card>
          <div class="profile-header">
            <el-avatar :size="100" :src="studentInfo.avatar" />
            <h3>{{ studentInfo.name }}</h3>
            <p class="subtitle">{{ studentInfo.dept }} · {{ studentInfo.major }}</p>
            <el-tag>{{ studentInfo.grade }}</el-tag>
          </div>

          <el-divider />

          <div class="info-list">
            <div class="info-item">
              <span class="label">学号：</span>
              <span class="value">{{ studentInfo.studentId }}</span>
            </div>
            <div class="info-item">
              <span class="label">入学时间：</span>
              <span class="value">{{ studentInfo.enrollmentDate }}</span>
            </div>
            <div class="info-item">
              <span class="label">联系电话：</span>
              <span class="value">{{ studentInfo.phone }}</span>
            </div>
            <div class="info-item">
              <span class="label">电子邮箱：</span>
              <span class="value">{{ studentInfo.email }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="16">
        <el-card>
          <el-tabs v-model="activeTab">
            <el-tab-pane label="学业成绩" name="grades">
              <el-row :gutter="20" class="grade-stats">
                <el-col :span="8">
                  <div class="stat-card">
                    <div class="stat-value highlight">{{ studentInfo.gpa }}</div>
                    <div class="stat-label">GPA</div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="stat-card">
                    <div class="stat-value">{{ studentInfo.rank }}</div>
                    <div class="stat-label">专业排名</div>
                  </div>
                </el-col>
                <el-col :span="8">
                  <div class="stat-card">
                    <div class="stat-value">{{ studentInfo.credits }}</div>
                    <div class="stat-label">已修学分</div>
                  </div>
                </el-col>
              </el-row>

              <el-divider />

              <h4>最近学期成绩</h4>
              <el-table :data="gradeRecords" stripe style="margin-top: 16px">
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
              <el-timeline>
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
                  studentInfo.lastMentalTest
                }}</el-descriptions-item>
                <el-descriptions-item label="测评结果">
                  <el-tag type="success">{{ studentInfo.mentalStatus }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="辅导员关注" :span="2">{{
                  studentInfo.counselorNotes || '无'
                }}</el-descriptions-item>
              </el-descriptions>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const activeTab = ref('grades')

const studentInfo = ref({
  id: 1,
  studentId: '2025010001',
  name: '张三',
  avatar: '',
  dept: '计算机学院',
  major: '软件工程',
  grade: '2025级',
  enrollmentDate: '2025-09-01',
  phone: '13800138010',
  email: 'zhangsan@edu.edu.cn',
  gpa: 3.8,
  rank: '5/128',
  credits: 45,
  lastMentalTest: '2026-03-15',
  mentalStatus: '良好',
  counselorNotes: ''
})

const gradeRecords = ref([
  {
    semester: '2025-2026-2',
    course: '高等数学',
    credit: 4,
    score: 92,
    gpa: 4.0
  },
  {
    semester: '2025-2026-2',
    course: '程序设计基础',
    credit: 4,
    score: 88,
    gpa: 3.7
  },
  {
    semester: '2025-2026-1',
    course: '大学英语',
    credit: 3,
    score: 85,
    gpa: 3.3
  }
])

const qualityRecords = ref([
  {
    date: '2026-03-20',
    title: '校级编程竞赛一等奖',
    description: '参加校程序设计竞赛，获得一等奖',
    category: '竞赛获奖',
    type: 'success'
  },
  {
    date: '2026-02-15',
    title: '志愿服务时长认定',
    description: '参与社区志愿服务，累计服务时长20小时',
    category: '志愿服务',
    type: 'primary'
  },
  {
    date: '2025-12-10',
    title: '优秀学生干部',
    description: '被评为2025年度优秀学生干部',
    category: '荣誉称号',
    type: 'warning'
  }
])

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

.grade-stats {
  margin-bottom: 20px;

  .stat-card {
    text-align: center;
    padding: 20px;
    background: #f6ffed;
    border-radius: 8px;

    .stat-value {
      font-size: 28px;
      font-weight: 600;
      color: rgba(0, 0, 0, 0.85);

      &.highlight {
        color: #52c41a;
      }
    }

    .stat-label {
      margin-top: 8px;
      color: rgba(0, 0, 0, 0.45);
    }
  }
}
</style>
