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
              <div class="mini-value">
                {{
                  sectionErrors.rank
                    ? '加载失败'
                    : rankData.rank == null
                      ? '—'
                      : `${rankData.rank} / ${rankData.cohortSize}`
                }}
              </div>
              <div class="mini-label" :title="rankData.scope || rankData.reason">
                专业同年级排名
              </div>
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
            <template v-if="activeTab === 'grades'">
              <div class="section-title">课程成绩</div>
              <ChartState
                :error="sectionErrors.academic"
                :empty="!academic.length"
                empty-text="暂无课程成绩"
                @retry="fetchDetail"
              >
                <el-select
                  v-model="selectedTerm"
                  clearable
                  placeholder="全部学期"
                  class="term-filter"
                  ><el-option v-for="term in terms" :key="term" :label="term" :value="term"
                /></el-select>
                <el-table :data="visibleAcademic" stripe>
                  <el-table-column prop="term" label="学期" min-width="125" />
                  <el-table-column prop="courseName" label="课程" min-width="140" />
                  <el-table-column prop="courseCode" label="课程编号" width="115" />
                  <el-table-column prop="credit" label="学分" width="80" />
                  <el-table-column label="成绩" width="90"
                    ><template #default="{ row }">{{
                      row.score ?? '未出分'
                    }}</template></el-table-column
                  >
                  <el-table-column label="绩点" width="90"
                    ><template #default="{ row }">{{
                      formatGpa(row.gradePoint)
                    }}</template></el-table-column
                  >
                </el-table>
              </ChartState>
            </template>
            <template v-else-if="activeTab === 'quality'">
              <ChartState :error="sectionErrors.quality" @retry="fetchDetail"
                ><QualityRecords
                  :student-id="route.params.id"
                  :records="quality"
                  :can-write="canWrite"
                  @changed="fetchDetail"
              /></ChartState>
            </template>
            <template v-else>
              <div class="section-title">心理测评记录</div>
              <ChartState
                :error="sectionErrors.mental"
                :empty="!mental.length"
                empty-text="暂无心理测评记录"
                @retry="fetchDetail"
              >
                <el-table :data="mental" stripe>
                  <el-table-column prop="questionnaireTitle" label="问卷" min-width="170" />
                  <el-table-column prop="questionnaireType" label="类型" width="110" />
                  <el-table-column label="等级" width="110"
                    ><template #default="{ row }">{{ row.level || '—' }}</template></el-table-column
                  >
                  <el-table-column label="测评时间" min-width="145"
                    ><template #default="{ row }">{{
                      (row.createTime || '').replace('T', ' ') || '—'
                    }}</template></el-table-column
                  >
                </el-table>
              </ChartState>
            </template>
          </div>
        </section>
      </div>
    </ChartState>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import {
  getStudentDetail,
  getStudentAcademic,
  getStudentRank,
  getStudentQuality,
  getStudentMentalSummary
} from '@/api/student'
import QualityRecords from './QualityRecords.vue'
import { formatGpa } from './format'
import { useUserStore } from '@/store/modules/user'
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
const user = useUserStore()
const canWrite = computed(
  () =>
    user.userRoles.includes('admin') ||
    (user.userRoles.includes('teacher') &&
      (!Array.isArray(user.userInfo?.permissions) ||
        user.userInfo.permissions.includes('student:write')))
)
const academic = ref([]),
  rankData = ref({}),
  quality = ref([]),
  mental = ref([]),
  selectedTerm = ref('')
const sectionErrors = reactive({ academic: false, rank: false, quality: false, mental: false })
const terms = computed(() => [...new Set(academic.value.map((record) => record.term))])
const visibleAcademic = computed(() =>
  selectedTerm.value
    ? academic.value.filter((record) => record.term === selectedTerm.value)
    : academic.value
)
let fetchVersion = 0

const deptMajor = computed(
  () => [info.value.deptName, info.value.majorName].filter(Boolean).join(' · ') || '—'
)

const fetchDetail = async () => {
  const version = ++fetchVersion
  loading.value = true
  loadError.value = false
  const id = route.params.id
  const responses = await Promise.allSettled([
    getStudentDetail(id),
    getStudentAcademic(id),
    getStudentRank(id),
    getStudentQuality(id),
    getStudentMentalSummary(id)
  ])
  if (version !== fetchVersion) return
  if (responses[0].status === 'fulfilled') info.value = responses[0].value.data || {}
  else {
    info.value = {}
    loadError.value = true
  }
  const destinations = [academic, rankData, quality, mental]
  const keys = ['academic', 'rank', 'quality', 'mental']
  responses.slice(1).forEach((response, index) => {
    sectionErrors[keys[index]] = response.status === 'rejected'
    destinations[index].value =
      response.status === 'fulfilled'
        ? response.value.data || (index === 1 ? {} : [])
        : index === 1
          ? {}
          : []
  })
  selectedTerm.value = terms.value[0] || ''
  loading.value = false
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
watch(() => route.params.id, fetchDetail)
</script>

<style scoped lang="scss">
.term-filter {
  margin: 16px 0;
  width: 220px;
}

.student-avatar {
  background: linear-gradient(135deg, #8fbcff, #5a8ff0);
}
</style>
