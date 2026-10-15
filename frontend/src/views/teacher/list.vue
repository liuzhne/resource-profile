<template>
  <div class="glass-panel list-panel">
    <div class="page-head">
      <div>
        <div class="head-title">教师列表</div>
        <div class="head-sub">
          共 <b>{{ total }}</b> 名在册教师
        </div>
      </div>
      <el-button
        v-if="canWrite"
        type="primary"
        class="head-action"
        :icon="Plus"
        @click="profileEditor.open()"
        >新增教师</el-button
      >
    </div>

    <div class="filter-bar">
      <el-input
        v-model="searchForm.name"
        placeholder="请输入姓名"
        clearable
        :prefix-icon="Search"
      />
      <el-select v-model="searchForm.dept" placeholder="请选择学院" clearable>
        <el-option v-for="d in DEPT_OPTIONS" :key="d" :label="d" :value="d" />
      </el-select>
      <el-select v-model="searchForm.title" placeholder="请选择职称" clearable>
        <el-option v-for="t in TITLE_OPTIONS" :key="t" :label="t" :value="t" />
      </el-select>
      <el-button class="btn-query" @click="handleSearch">查询</el-button>
      <el-button class="btn-reset" @click="handleReset">重置</el-button>
    </div>

    <ChartState
      :loading="loading"
      :error="loadError"
      :empty="!loading && !loadError && !teacherList.length"
      empty-text="没有符合条件的教师"
      height="280px"
      @retry="fetchList"
    >
      <div class="table-scroll">
        <table class="plain-table teacher-table">
          <thead>
            <tr>
              <th>工号</th>
              <th>姓名</th>
              <th>学院</th>
              <th>职称</th>
              <th>学历</th>
              <th>专业方向</th>
              <th>入职日期</th>
              <th class="col-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in teacherList" :key="row.id">
              <td class="tnum">{{ row.employeeId }}</td>
              <td>
                <span class="name-cell">
                  <span class="avatar-sm" :style="{ background: avatarBg(i) }">
                    {{ initialOf(row.name) }}
                  </span>
                  <span class="strong-text">{{ row.name }}</span>
                </span>
              </td>
              <td>{{ row.deptName || '—' }}</td>
              <td>
                <span v-if="row.title" class="badge">{{ row.title }}</span>
                <span v-else>—</span>
              </td>
              <td>{{ row.education || '—' }}</td>
              <td>{{ row.major || '—' }}</td>
              <td class="tnum">{{ row.joinDate || '—' }}</td>
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
          第 {{ rangeStart }}–{{ rangeEnd }} 条，共 <b>{{ total }}</b> 条
        </span>
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="sizes, prev, pager, next"
          @current-change="handlePageChange"
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
import { getTeacherList, getTeacherOptions } from '@/api/teacher'
import ProfileEditor from './ProfileEditor.vue'
import { useUserStore } from '@/store/modules/user'
import { avatarBg, initialOf } from '@/utils/avatar'
import ChartState from '@/views/dashboard/components/ChartState.vue'

const DEPT_OPTIONS = ref([])
const TITLE_OPTIONS = ref([])
const profileEditor = ref(null)
const user = useUserStore()
const canWrite = computed(() => user.userRoles.some((role) => ['admin'].includes(role)))
const fetchOptions = async () => {
  const data = (await getTeacherOptions()).data
  DEPT_OPTIONS.value = data.departments || []
  TITLE_OPTIONS.value = data.titles || []
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

const searchForm = reactive({
  name: '',
  dept: '',
  title: ''
})

const teacherList = ref([])

const rangeStart = computed(() =>
  total.value === 0 ? 0 : (currentPage.value - 1) * pageSize.value + 1
)
const rangeEnd = computed(() => Math.min(currentPage.value * pageSize.value, total.value))

const fetchList = async () => {
  loading.value = true
  loadError.value = false
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
    loadError.value = true
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

onMounted(() => {
  fetchList()
  fetchOptions().catch(() => ElMessage.error('获取教师筛选选项失败'))
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

.teacher-table {
  min-width: 880px;
}

.strong-text {
  color: var(--text-color);
  font-weight: 500;
}

.filter-bar {
  :deep(.el-input) {
    width: 190px;
  }

  :deep(.el-select) {
    width: 150px;
  }
}
</style>
