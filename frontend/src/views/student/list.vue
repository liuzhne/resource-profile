<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">学生画像管理</h1>
        <div class="page-subtitle">
          查询学生基础档案、学业状态和画像关键指标，支持按学院与年级快速筛选。
        </div>
      </div>
      <el-button type="primary">新增学生</el-button>
    </div>

    <div class="toolbar-card search-panel">
      <el-form :model="searchForm" inline class="search-form">
        <el-form-item label="姓名">
          <el-input v-model="searchForm.name" placeholder="请输入姓名" clearable />
        </el-form-item>
        <el-form-item label="学院">
          <el-select
            v-model="searchForm.dept"
            placeholder="请选择学院"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option v-for="d in DEPT_OPTIONS" :key="d" :label="d" :value="d" />
          </el-select>
        </el-form-item>
        <el-form-item label="年级">
          <el-select
            v-model="searchForm.grade"
            placeholder="请选择年级"
            clearable
            style="width: 140px"
          >
            <el-option v-for="g in GRADE_OPTIONS" :key="g" :label="`${g}级`" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="table-card student-table-card">
      <template #header>
        <div class="card-header">
          <div>
            <span>学生列表</span>
            <span class="result-count">共 {{ total }} 条</span>
          </div>
          <el-button text :icon="Refresh" :loading="loading" @click="fetchList">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="studentList" stripe empty-text="暂无学生数据">
        <el-table-column type="index" width="50" />
        <el-table-column prop="studentId" label="学号" min-width="120" />
        <el-table-column prop="name" label="姓名" min-width="110">
          <template #default="{ row }">
            <div class="student-name">{{ row.name || '-' }}</div>
            <div class="student-id">{{ row.studentId || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="学院" min-width="150" />
        <el-table-column prop="majorName" label="专业" min-width="150" />
        <el-table-column prop="grade" label="年级" width="100">
          <template #default="{ row }">{{ row.grade ? `${row.grade}级` : '-' }}</template>
        </el-table-column>
        <el-table-column prop="className" label="班级" width="100" />
        <el-table-column prop="gpa" label="GPA" width="90" align="center">
          <template #default="{ row }">
            <span :class="['gpa-value', gpaClass(row.gpa)]">{{ formatGpa(row.gpa) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="viewDetail(row)">查看</el-button>
            <el-button link>编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        class="pagination"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getStudentList } from '@/api/student'
import { Refresh, Search } from '@element-plus/icons-vue'

const DEPT_OPTIONS = [
  '计算机学院',
  '软件学院',
  '数学学院',
  '物理学院',
  '化学学院',
  '生命科学学院',
  '电子工程学院',
  '心理学院',
  '医学院',
  '文学院',
  '历史学院',
  '哲学学院',
  '教育学院',
  '艺术学院',
  '体育学院',
  '法学院',
  '经济管理学院',
  '外国语学院',
  '马克思主义学院'
]
const GRADE_OPTIONS = ['2024', '2023', '2022', '2021', '2020']

const router = useRouter()
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const searchForm = reactive({ name: '', dept: '', grade: '' })
const studentList = ref([])

const fetchList = async () => {
  loading.value = true
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

const formatGpa = (g) => {
  const n = Number(g)
  return Number.isFinite(n) ? n.toFixed(2) : '-'
}
const gpaTagType = (g) => {
  const n = Number(g)
  if (!Number.isFinite(n) || n === 0) return 'info'
  if (n >= 3.5) return 'success'
  if (n >= 2.5) return 'warning'
  return 'danger'
}
const gpaClass = (g) => `gpa-${gpaTagType(g) || 'normal'}`
const statusLabel = (s) => ({ 0: '退学', 1: '在读', 2: '毕业' })[s] ?? '-'
const statusTagType = (s) => ({ 0: 'danger', 1: 'success', 2: 'info' })[s] ?? 'info'

onMounted(fetchList)
</script>

<style scoped lang="scss">
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .result-count {
    margin-left: 10px;
    color: var(--text-color-muted);
    font-size: 12px;
    font-weight: 400;
  }
}

.search-panel {
  padding: 16px 16px 4px;
  margin-bottom: 14px;
}

.search-form {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
}

.student-table-card {
  :deep(.el-card__body) {
    padding-top: 0;
  }
}

.student-name {
  color: var(--text-color);
  font-weight: 600;
  line-height: 1.4;
}

.student-id {
  color: var(--text-color-muted);
  font-size: 12px;
  line-height: 1.4;
}

.gpa-value {
  display: inline-flex;
  min-width: 44px;
  justify-content: center;
  padding: 3px 8px;
  border-radius: 4px;
  font-weight: 700;
  font-size: 13px;

  &.gpa-success {
    color: #1f7a3a;
    background: #edf8ef;
  }

  &.gpa-warning {
    color: #9a5b13;
    background: #fff7e6;
  }

  &.gpa-danger {
    color: #b42318;
    background: #fff1f0;
  }

  &.gpa-info {
    color: var(--text-color-muted);
    background: var(--bg-color-subtle);
  }
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
