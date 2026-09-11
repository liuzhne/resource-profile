<template>
  <div>
    <div class="detail-head">
      <span class="back-btn" @click="goBack">
        <el-icon :size="12"><ArrowLeft /></el-icon>返回
      </span>
      <span class="detail-title">教师详情</span>
    </div>

    <ChartState
      :loading="loading"
      :error="loadError"
      :empty="!loading && !loadError && !teacherInfo.employeeId"
      empty-text="未找到该教师"
      height="320px"
      @retry="fetchDetail"
    >
      <div class="detail-grid">
        <!-- 左：档案卡 -->
        <aside class="glass-panel profile-card">
          <div class="profile-top">
            <span class="avatar-lg teacher-avatar">{{ initialOf(teacherInfo.name) }}</span>
            <h3 class="profile-name">{{ teacherInfo.name || '—' }}</h3>
            <p class="profile-sub">{{ deptTitle }}</p>
            <span class="profile-tag" :style="statusStyle">{{ teacherInfo.status }}</span>
          </div>

          <!-- 评教三项后端均未提供，统一显示占位 -->
          <div class="mini-stats">
            <div class="mini-stat">
              <div class="mini-value accent">{{ teacherInfo.evaluationScore }}</div>
              <div class="mini-label">综合评分</div>
            </div>
            <div class="mini-stat">
              <div class="mini-value">{{ teacherInfo.evaluationCount }}</div>
              <div class="mini-label">评价次数</div>
            </div>
            <div class="mini-stat">
              <div class="mini-value">{{ teacherInfo.satisfaction }}</div>
              <div class="mini-label">满意度</div>
            </div>
          </div>

          <div class="info-rows">
            <div class="info-row">
              <span class="k">工号</span>
              <span class="v tnum">{{ teacherInfo.employeeId || '—' }}</span>
            </div>
            <div class="info-row">
              <span class="k">入职时间</span>
              <span class="v tnum">{{ teacherInfo.joinDate || '—' }}</span>
            </div>
            <div class="info-row">
              <span class="k">联系电话</span><span class="v">{{ teacherInfo.phone }}</span>
            </div>
            <div class="info-row">
              <span class="k">电子邮箱</span><span class="v">{{ teacherInfo.email }}</span>
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
            <!-- 基本信息：字段均来自 /teacher/{id} -->
            <template v-if="activeTab === 'basic'">
              <div class="section-title">基本信息</div>
              <div class="basic-grid">
                <div v-for="f in basicFields" :key="f.k" class="basic-item">
                  <span class="k">{{ f.k }}</span>
                  <span class="v">{{ f.v || '—' }}</span>
                </div>
              </div>
              <p v-if="hasMissingBasic" class="no-source missing-note">
                <el-icon :size="14"><WarningFilled /></el-icon>
                <span>「政治面貌」在 `teacher_info` 表中无对应字段，接口不返回该项。</span>
              </p>
            </template>

            <!-- 教学成果 -->
            <template v-else-if="activeTab === 'teaching'">
              <div class="section-title">教学成果</div>
              <p class="no-source">
                <el-icon :size="14"><WarningFilled /></el-icon>
                <span
                  >教学成果暂无数据来源：`teacher_info` 表与 `/teacher/{id}`
                  接口均不含该类记录，需后端新增表与接口。</span
                >
              </p>
            </template>

            <!-- 科研项目 -->
            <template v-else-if="activeTab === 'research'">
              <div class="section-title">科研项目</div>
              <p class="no-source">
                <el-icon :size="14"><WarningFilled /></el-icon>
                <span>科研项目暂无数据来源，同上。</span>
              </p>
            </template>

            <!-- 教学评价 -->
            <template v-else>
              <div class="section-title">教学评价</div>
              <p class="no-source">
                <el-icon :size="14"><WarningFilled /></el-icon>
                <span
                  >评教数据（综合评分 / 评价次数 /
                  满意度）暂无数据来源，左侧三项统计同样为占位。</span
                >
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
import { getTeacherDetail } from '@/api/teacher'
import { initialOf } from '@/utils/avatar'
import ChartState from '@/views/dashboard/components/ChartState.vue'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()

const tabs = [
  { key: 'basic', label: '基本信息' },
  { key: 'teaching', label: '教学成果' },
  { key: 'research', label: '科研项目' },
  { key: 'eval', label: '教学评价' }
]

const activeTab = ref('basic')
const loading = ref(false)
const loadError = ref(false)

const teacherInfo = ref({
  name: '',
  dept: '',
  title: '',
  status: '—',
  employeeId: '',
  joinDate: '',
  phone: '—',
  email: '—',
  gender: '—',
  birthDate: '',
  political: '—',
  education: '',
  school: '',
  major: '',
  researchArea: '',
  evaluationScore: '—',
  evaluationCount: '—',
  satisfaction: '—'
})

const deptTitle = computed(
  () =>
    [teacherInfo.value.dept, teacherInfo.value.title].filter((v) => v && v !== '-').join(' · ') ||
    '—'
)

const statusStyle = computed(() =>
  teacherInfo.value.status === '在职'
    ? { background: 'var(--success-tint)', color: 'var(--success-deep)' }
    : { background: 'var(--fill-grey)', color: 'var(--fill-grey-fg)' }
)

const basicFields = computed(() => [
  { k: '姓名', v: teacherInfo.value.name },
  { k: '性别', v: teacherInfo.value.gender },
  { k: '出生日期', v: teacherInfo.value.birthDate },
  { k: '政治面貌', v: teacherInfo.value.political },
  { k: '学历', v: teacherInfo.value.education },
  { k: '毕业院校', v: teacherInfo.value.school },
  { k: '专业方向', v: teacherInfo.value.major },
  { k: '研究方向', v: teacherInfo.value.researchArea }
])

const hasMissingBasic = computed(() => teacherInfo.value.political === '—')

const formatGender = (val) => {
  if (val === 0) return '女'
  if (val === 1) return '男'
  return '—'
}

const formatStatus = (val) => {
  if (val === 0) return '离职'
  if (val === 1) return '在职'
  return '—'
}

const fetchDetail = async () => {
  const id = route.params.id
  if (!id) {
    ElMessage.error('教师ID不存在')
    return
  }
  loading.value = true
  loadError.value = false
  try {
    const res = await getTeacherDetail(id)
    const data = res.data
    if (!data) {
      ElMessage.warning('未找到该教师信息')
      return
    }
    teacherInfo.value = {
      ...data,
      dept: data.deptName || '—',
      gender: formatGender(data.gender),
      status: formatStatus(data.status),
      // 以下字段 teacher_info 表均无，保持占位而非编造
      political: '—',
      phone: '—',
      email: '—',
      evaluationScore: '—',
      evaluationCount: '—',
      satisfaction: '—'
    }
  } catch (error) {
    loadError.value = true
    ElMessage.error('获取教师详情失败')
  } finally {
    loading.value = false
  }
}

const goBack = () => {
  router.back()
}

onMounted(() => {
  fetchDetail()
})
</script>

<style scoped lang="scss">
.teacher-avatar {
  background: linear-gradient(135deg, #ffc98f, #f5a25a);
}

.basic-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0 24px;

  .basic-item {
    display: flex;
    justify-content: space-between;
    gap: 12px;
    padding: 11px 0;
    border-bottom: 1px solid rgba(60, 60, 67, 0.08);
    font-size: 13px;

    .k {
      color: var(--text-color-secondary);
      flex-shrink: 0;
    }

    .v {
      color: var(--text-color);
      text-align: right;
      overflow-wrap: anywhere;
    }
  }
}

.missing-note {
  margin-top: 14px;
}
</style>
