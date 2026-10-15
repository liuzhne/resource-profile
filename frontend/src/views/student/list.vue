<template>
  <div class="glass-panel list-panel">
    <div class="page-head">
      <div>
        <div class="head-title">学生列表</div>
        <div class="head-sub">
          共 <b>{{ total.toLocaleString('en-US') }}</b> 名在册学生
        </div>
      </div>
      <el-button
        v-if="canWrite"
        type="primary"
        class="head-action"
        :icon="Plus"
        @click="profileEditor.open()"
        >新增学生</el-button
      >
    </div>

    <div class="filter-bar">
      <el-input
        v-model="searchForm.name"
        placeholder="请输入姓名"
        clearable
        :prefix-icon="Search"
      />
      <el-select v-model="searchForm.dept" placeholder="请选择学院" clearable filterable>
        <el-option v-for="d in DEPT_OPTIONS" :key="d" :label="d" :value="d" />
      </el-select>
      <el-select v-model="searchForm.grade" placeholder="请选择年级" clearable>
        <el-option v-for="g in GRADE_OPTIONS" :key="g" :label="`${g}级`" :value="g" />
      </el-select>
      <el-button class="btn-query" @click="handleSearch">查询</el-button>
      <el-button class="btn-reset" @click="handleReset">重置</el-button>
    </div>

    <ChartState
      :loading="loading"
      :error="loadError"
      :empty="!loading && !loadError && !studentList.length"
      empty-text="没有符合条件的学生"
      height="280px"
      @retry="fetchList"
    >
      <div class="table-scroll">
        <table class="plain-table student-table">
          <thead>
            <tr>
              <th>学号</th>
              <th>姓名</th>
              <th>学院</th>
              <th>专业</th>
              <th>年级</th>
              <th>班级</th>
              <th>GPA</th>
              <th>状态</th>
              <th class="col-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in studentList" :key="row.id">
              <td class="tnum">{{ row.studentId }}</td>
              <td>
                <span class="name-cell">
                  <span class="avatar-sm" :style="{ background: avatarBg(i + 2) }">
                    {{ initialOf(row.name) }}
                  </span>
                  <span class="strong-text">{{ row.name }}</span>
                </span>
              </td>
              <td>{{ row.deptName || '—' }}</td>
              <td>{{ row.majorName || '—' }}</td>
              <td>{{ row.grade ? `${row.grade}级` : '—' }}</td>
              <td>{{ row.className || '—' }}</td>
              <td>
                <span class="gpa-cell">
                  <span class="gpa-track">
                    <span
                      class="gpa-fill"
                      :style="{ width: gpaPct(row.gpa), background: gpaColor(row.gpa) }"
                    ></span>
                  </span>
                  <span class="gpa-value tnum" :style="{ color: gpaColor(row.gpa) }">
                    {{ formatGpa(row.gpa) }}
                  </span>
                </span>
              </td>
              <td>
                <span class="badge status-badge" :style="statusStyle(row.status)">
                  <i class="status-dot"></i>{{ statusLabel(row.status) }}
                </span>
              </td>
              <td class="col-right">
                <span class="row-action" @click="viewDetail(row)">查看</span>
                <span v-if="canWrite" class="row-action" @click="profileEditor.open(row)"
                  >编辑</span
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="table-foot">
        <span class="foot-count">
          第 {{ rangeStart }}–{{ rangeEnd }} 条，共 <b>{{ total.toLocaleString('en-US') }}</b> 条
        </span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="sizes, prev, pager, next"
          @current-change="fetchList"
          @size-change="handleSearch"
        />
      </div>
    </ChartState>
    <ProfileEditor ref="profileEditor" @saved="refreshAfterSave" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import { getStudentList, getStudentOptions } from '@/api/student'
import ProfileEditor from './ProfileEditor.vue'
import { formatGpa } from './format'
import { useUserStore } from '@/store/modules/user'
import { avatarBg, initialOf } from '@/utils/avatar'
import ChartState from '@/views/dashboard/components/ChartState.vue'

const DEPT_OPTIONS = ref([])
const GRADE_OPTIONS = ref([])
const profileEditor = ref(null)
const user = useUserStore()
const canWrite = computed(
  () =>
    user.userRoles.includes('admin') ||
    (user.userRoles.includes('teacher') &&
      (!Array.isArray(user.userInfo?.permissions) ||
        user.userInfo.permissions.includes('student:write')))
)
const fetchOptions = async () => {
  const data = (await getStudentOptions()).data
  DEPT_OPTIONS.value = data.departments || []
  GRADE_OPTIONS.value = data.grades || []
}
const refreshAfterSave = async () => {
  currentPage.value = 1
  await Promise.all([fetchList(), fetchOptions()])
}

const router = useRouter()
const loading = ref(false)
const loadError = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const searchForm = reactive({ name: '', dept: '', grade: '' })
const studentList = ref([])

const rangeStart = computed(() =>
  total.value === 0 ? 0 : (currentPage.value - 1) * pageSize.value + 1
)
const rangeEnd = computed(() => Math.min(currentPage.value * pageSize.value, total.value))

const fetchList = async () => {
  loading.value = true
  loadError.value = false
  try {
    const res = await getStudentList({
      page: currentPage.value,
      size: pageSize.value,
      name: searchForm.name || undefined,
      dept: searchForm.dept || undefined,
      grade: searchForm.grade || undefined
    })
    const pageData = res.data
    studentList.value = pageData.records || []
    total.value = Number(pageData.total) || 0
  } catch (e) {
    loadError.value = true
    console.error('获取学生列表失败', e)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchList()
}

const handleReset = () => {
  searchForm.name = ''
  searchForm.dept = ''
  searchForm.grade = ''
  currentPage.value = 1
  fetchList()
}

const viewDetail = (row) => {
  router.push(`/student/detail/${row.id}`)
}

// 阈值沿用改版前的 3.5 / 2.5，与原型一致
const gpaColor = (g) => {
  const n = Number(g)
  if (!Number.isFinite(n) || n === 0) return 'var(--fill-grey-fg)'
  if (n >= 3.5) return 'var(--success-deep)'
  if (n >= 2.5) return 'var(--warning-deep)'
  return 'var(--error-deep)'
}

// GPA 满分按 4.0 折算进度条
const gpaPct = (g) => {
  const n = Number(g)
  if (!Number.isFinite(n)) return '0%'
  return Math.max(0, Math.min(100, Math.round((n / 4) * 100))) + '%'
}

const statusLabel = (s) => ({ 0: '退学', 1: '在读', 2: '毕业' })[s] ?? '—'

const statusStyle = (s) =>
  ({
    0: { background: 'var(--error-tint)', color: 'var(--error-deep)' },
    1: { background: 'var(--success-tint)', color: 'var(--success-deep)' },
    2: { background: 'var(--fill-grey)', color: 'var(--fill-grey-fg)' }
  })[s] ?? { background: 'var(--fill-grey)', color: 'var(--fill-grey-fg)' }

onMounted(() => {
  fetchList()
  fetchOptions().catch(() => ElMessage.error('获取学生筛选选项失败'))
})
</script>

<style scoped lang="scss">
.list-panel {
  display: flex;
  flex-direction: column;
}

.head-action {
  height: 34px;
  padding: 0 15px;
  font-size: 13px;
  font-weight: 600;
  border-radius: var(--radius-md);
  border: none;
  box-shadow: 0 5px 14px -8px rgba(0, 122, 255, 0.7);
}

.student-table {
  min-width: 1000px;
}

.strong-text {
  color: var(--text-color);
  font-weight: 500;
}

.gpa-cell {
  display: flex;
  align-items: center;
  gap: 9px;

  .gpa-track {
    flex: 1;
    max-width: 64px;
    height: 5px;
    border-radius: 3px;
    background: rgba(120, 120, 128, 0.16);
    overflow: hidden;
    display: block;
  }

  .gpa-fill {
    display: block;
    height: 100%;
    border-radius: 3px;
  }

  .gpa-value {
    font-weight: 600;
  }
}

.status-badge {
  gap: 6px;
  padding: 0 9px;

  .status-dot {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: currentColor;
    display: block;
  }
}

.filter-bar {
  :deep(.el-input) {
    width: 190px;
  }

  :deep(.el-select) {
    width: 170px;
  }
}
</style>
