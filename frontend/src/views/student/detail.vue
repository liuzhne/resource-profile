<template>
  <div>
    <div class="detail-head">
      <span class="back-btn" @click="goBack">
        <el-icon :size="12"><ArrowLeft /></el-icon>返回
      </span>
      <span class="detail-title">学生详情</span>
    </div>

    <ChartState
      :loading="loading"
      :error="loadError"
      :empty="!loading && !loadError && !info.id"
      empty-text="未找到该学生"
      height="320px"
      @retry="fetchDetail"
    >
      <div class="detail-grid">
        <!-- 左：档案卡 -->
        <aside class="glass-panel profile-card">
          <div class="profile-top">
            <span class="avatar-lg student-avatar">{{ initialOf(info.name) }}</span>
            <h3 class="profile-name">{{ info.name || '—' }}</h3>
            <p class="profile-sub">{{ deptMajor }}</p>
            <span class="profile-tag">{{ info.grade ? `${info.grade}级` : '—' }}</span>
          </div>

          <div class="mini-stats">
            <div class="mini-stat">
              <div class="mini-value accent">{{ formatGpa(info.gpa) }}</div>
              <div class="mini-label">GPA</div>
            </div>
            <div class="mini-stat">
              <div class="mini-value">—</div>
              <div class="mini-label">专业排名</div>
            </div>
            <div class="mini-stat">
              <div class="mini-value">{{ info.credits ?? '—' }}</div>
              <div class="mini-label">已修学分</div>
            </div>
          </div>

          <div class="info-rows">
            <div class="info-row">
              <span class="k">学号</span><span class="v tnum">{{ info.studentId || '—' }}</span>
            </div>
            <div class="info-row">
              <span class="k">班级</span><span class="v">{{ info.className || '—' }}</span>
            </div>
            <div class="info-row">
              <span class="k">入学时间</span>
              <span class="v tnum">{{ info.enrollmentDate || '—' }}</span>
            </div>
            <div class="info-row">
              <span class="k">预计毕业</span>
              <span class="v tnum">{{ info.expectedGraduation || '—' }}</span>
            </div>
            <div class="info-row">
              <span class="k">状态</span>
              <span class="v">
                <span class="badge" :style="statusStyle(info.status)">
                  {{ statusLabel(info.status) }}
                </span>
              </span>
            </div>
          </div>
        </aside>

        <!-- 右：分页签内容 -->
        <section class="glass-panel detail-main">
          <div class="detail-tabs">
            <div class="seg-control">
              <button
                v-for="t in tabs"
                :key="t.key"
                type="button"
                class="seg-item"
                :class="{ 'is-active': activeTab === t.key }"
                @click="activeTab = t.key"
              >
                {{ t.label }}
              </button>
            </div>
          </div>

          <div class="detail-tab-body">
            <!-- 学业成绩 -->
            <template v-if="activeTab === 'grades'">
              <div class="section-title">最近学期成绩</div>
              <p class="no-source">
                <el-icon :size="14"><WarningFilled /></el-icon>
                <span>
                  课程成绩暂无数据来源：`student_info` 表与 `/student/{id}` 接口均不含成绩明细，
                  需后端新增成绩表与接口后才能填充。此处保留原型版式。
                </span>
              </p>
            </template>

            <!-- 综合素质 -->
            <template v-else-if="activeTab === 'quality'">
              <div class="section-title">综合素质记录</div>
              <p class="no-source">
                <el-icon :size="14"><WarningFilled /></el-icon>
                <span>
                  竞赛、荣誉、志愿服务等记录暂无数据来源：现有接口未返回该类数据，需后端新增。
                </span>
              </p>
            </template>

            <!-- 心理健康 -->
            <template v-else>
              <div class="section-title">心理健康</div>
              <p class="no-source">
                <el-icon :size="14"><WarningFilled /></el-icon>
                <span>
                  心理测评数据在 `/mental/student/**` 下，需按学生本人 `userId` 查询， 教师侧按学生
                  `id` 查看的接口尚未提供，故此处不展示。
                </span>
              </p>
            </template>
          </div>
        </section>
      </div>
    </ChartState>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft, WarningFilled } from '@element-plus/icons-vue'
import { getStudentDetail } from '@/api/student'
import { initialOf } from '@/utils/avatar'
import ChartState from '@/views/dashboard/components/ChartState.vue'

const router = useRouter()
const route = useRoute()

const tabs = [
  { key: 'grades', label: '学业成绩' },
  { key: 'quality', label: '综合素质' },
  { key: 'mental', label: '心理健康' }
]

const activeTab = ref('grades')
const loading = ref(false)
const loadError = ref(false)
const info = ref({})

const deptMajor = computed(
  () => [info.value.deptName, info.value.majorName].filter(Boolean).join(' · ') || '—'
)

// 改版前本页是写死的假数据（永远显示「张三」），此处接回一直存在但未被调用的 getStudentDetail
const fetchDetail = async () => {
  loading.value = true
  loadError.value = false
  try {
    const res = await getStudentDetail(route.params.id)
    info.value = res.data || {}
  } catch (e) {
    loadError.value = true
    console.error('获取学生详情失败', e)
  } finally {
    loading.value = false
  }
}

const formatGpa = (g) => {
  const n = Number(g)
  return Number.isFinite(n) && n > 0 ? n.toFixed(2) : '—'
}

const statusLabel = (s) => ({ 0: '退学', 1: '在读', 2: '毕业' })[s] ?? '—'

const statusStyle = (s) =>
  ({
    0: { background: 'var(--error-tint)', color: 'var(--error-deep)' },
    1: { background: 'var(--success-tint)', color: 'var(--success-deep)' },
    2: { background: 'var(--fill-grey)', color: 'var(--fill-grey-fg)' }
  })[s] ?? { background: 'var(--fill-grey)', color: 'var(--fill-grey-fg)' }

const goBack = () => {
  router.back()
}

onMounted(fetchDetail)
</script>

<style scoped lang="scss">
.student-avatar {
  background: linear-gradient(135deg, #8fbcff, #5a8ff0);
}
</style>
