<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">教师画像管理</h1>
        <div class="page-subtitle">
          查询教师基础档案、学院归属与职称信息，支持按姓名、学院和职称快速筛选。
        </div>
      </div>
      <el-button type="primary">新增教师</el-button>
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
        <el-form-item label="职称">
          <el-select
            v-model="searchForm.title"
            placeholder="请选择职称"
            clearable
            style="width: 150px"
          >
            <el-option v-for="t in TITLE_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="table-card teacher-table-card">
      <template #header>
        <div class="card-header">
          <div>
            <span>教师列表</span>
            <span class="result-count">共 {{ total }} 条</span>
          </div>
          <el-button text :icon="Refresh" :loading="loading" @click="fetchList">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="teacherList" stripe empty-text="暂无教师数据">
        <el-table-column type="index" width="50" />
        <el-table-column prop="employeeId" label="工号" min-width="120" />
        <el-table-column prop="name" label="姓名" min-width="120">
          <template #default="{ row }">
            <div class="teacher-name">{{ row.name || '-' }}</div>
            <div class="teacher-id">{{ row.employeeId || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="deptName" label="学院" min-width="150" />
        <el-table-column prop="title" label="职称" min-width="100">
          <template #default="{ row }">
            <el-tag :type="titleTagType(row.title)" effect="plain">{{ row.title || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="education" label="学历" min-width="100" />
        <el-table-column prop="major" label="专业方向" min-width="150" />
        <el-table-column prop="joinDate" label="入职日期" min-width="120" />
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
        @current-change="handlePageChange"
        @size-change="handlePageChange"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getTeacherList } from '@/api/teacher'
import { ElMessage } from 'element-plus'
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
const TITLE_OPTIONS = ['教授', '副教授', '讲师', '助教', '研究员']

const router = useRouter()
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const searchForm = reactive({
  name: '',
  dept: '',
  title: ''
})

const teacherList = ref([])

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getTeacherList({
      page: currentPage.value,
      size: pageSize.value,
      name: searchForm.name || undefined,
      dept: searchForm.dept || undefined,
      title: searchForm.title || undefined
    })
    const pageData = res.data
    teacherList.value = pageData.records || []
    total.value = Number(pageData.total) || 0
  } catch (error) {
    ElMessage.error('获取教师列表失败')
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
  searchForm.title = ''
  currentPage.value = 1
  fetchList()
}

const handlePageChange = () => {
  fetchList()
}

const viewDetail = (row) => {
  router.push(`/teacher/detail/${row.id}`)
}

const titleTagType = (title) => {
  if (title === '教授' || title === '研究员') return 'success'
  if (title === '副教授') return 'warning'
  if (title === '讲师') return 'primary'
  return 'info'
}

onMounted(() => {
  fetchList()
})
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

.teacher-table-card {
  :deep(.el-card__body) {
    padding-top: 0;
  }
}

.teacher-name {
  color: var(--text-color);
  font-weight: 600;
  line-height: 1.4;
}

.teacher-id {
  color: var(--text-color-muted);
  font-size: 12px;
  line-height: 1.4;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}
</style>
