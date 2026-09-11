<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">用户管理</h1>
        <div class="page-subtitle">
          统一维护平台账号、角色归属与启用状态，支撑师生画像系统的访问控制。
        </div>
      </div>
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增用户</el-button>
    </div>

    <div class="summary-grid">
      <div class="summary-card">
        <span class="summary-label">平台账号</span>
        <strong>{{ total }}</strong>
        <small>全部账号</small>
      </div>
      <div class="summary-card is-success">
        <span class="summary-label">启用账号</span>
        <strong>{{ enabledCount }}</strong>
        <small>当前页统计</small>
      </div>
      <div class="summary-card is-primary">
        <span class="summary-label">教师账号</span>
        <strong>{{ teacherCount }}</strong>
        <small>当前页统计</small>
      </div>
      <div class="summary-card is-warning">
        <span class="summary-label">停用账号</span>
        <strong>{{ disabledCount }}</strong>
        <small>当前页统计</small>
      </div>
    </div>

    <div class="toolbar-card search-panel">
      <el-form :model="searchForm" inline class="search-form">
        <el-form-item label="用户名">
          <el-input v-model="searchForm.username" placeholder="请输入用户名" clearable />
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="searchForm.role"
            placeholder="请选择角色"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="item in ROLE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="searchForm.status"
            placeholder="请选择状态"
            clearable
            style="width: 140px"
          >
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="table-card user-table-card">
      <template #header>
        <div class="card-header">
          <div>
            <span>账号列表</span>
            <span class="result-count">共 {{ total }} 条</span>
          </div>
          <el-button text :icon="Refresh" :loading="loading" @click="fetchList">刷新</el-button>
        </div>
      </template>

      <el-table v-loading="loading" :data="userList" stripe empty-text="暂无用户数据">
        <el-table-column type="index" width="50" />
        <el-table-column prop="username" label="账号" min-width="150">
          <template #default="{ row }">
            <div class="user-name">{{ row.username || '-' }}</div>
            <div class="user-meta">{{ row.nickname || '未设置昵称' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="userType" label="角色" min-width="120">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.userType)" effect="plain">
              {{ roleLabel(row.userType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="联系电话" min-width="140">
          <template #default="{ row }">{{ row.phone || '-' }}</template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180">
          <template #default="{ row }">{{ row.email || '-' }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              inline-prompt
              active-text="启用"
              inactive-text="停用"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="170">
          <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="EditPen" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog
      v-model="formDialogVisible"
      class="user-form-dialog"
      width="640px"
      :title="formDialogTitle"
      destroy-on-close
      @closed="resetUserForm"
    >
      <el-form
        ref="formRef"
        :model="userForm"
        :rules="userRules"
        label-width="92px"
        class="dialog-form"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model.trim="userForm.username"
            :disabled="isEditMode"
            placeholder="请输入登录用户名"
          />
        </el-form-item>
        <el-form-item label="姓名昵称" prop="nickname">
          <el-input v-model.trim="userForm.nickname" placeholder="请输入姓名或昵称" />
        </el-form-item>
        <el-form-item label="角色" prop="userType">
          <el-select v-model="userForm.userType" style="width: 100%">
            <el-option
              v-for="item in ROLE_OPTIONS"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="isEditMode ? '重置密码' : '初始密码'" prop="password">
          <el-input
            v-model="userForm.password"
            type="password"
            show-password
            :placeholder="isEditMode ? '留空则不修改密码' : '请输入初始密码'"
          />
        </el-form-item>
        <el-form-item label="联系电话" prop="phone">
          <el-input v-model.trim="userForm.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model.trim="userForm.email" placeholder="请输入邮箱地址" />
        </el-form-item>
        <el-form-item label="账号状态" prop="status">
          <el-radio-group v-model="userForm.status">
            <el-radio-button :label="1">启用</el-radio-button>
            <el-radio-button :label="0">停用</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitUserForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, EditPen, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { createUser, deleteUser, getUserList, updateUser } from '@/api/user'

const ROLE_OPTIONS = [
  { label: '管理员', value: 0 },
  { label: '教师', value: 1 },
  { label: '学生', value: 2 }
]

const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const userList = ref([])
const formRef = ref(null)
const formDialogVisible = ref(false)
const formMode = ref('add')
const editingUserId = ref(null)
const saving = ref(false)

const searchForm = reactive({
  username: '',
  role: '',
  status: ''
})
const userForm = reactive({
  username: '',
  nickname: '',
  userType: 2,
  password: '',
  phone: '',
  email: '',
  status: 1
})

const passwordValidator = (_rule, value, callback) => {
  if (!isEditMode.value && !value) {
    callback(new Error('请输入初始密码'))
    return
  }
  if (value && value.length < 6) {
    callback(new Error('密码长度不少于 6 位'))
    return
  }
  callback()
}

const userRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入姓名昵称', trigger: 'blur' }],
  userType: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [{ validator: passwordValidator, trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入有效邮箱', trigger: 'blur' }]
}

const enabledCount = computed(() => userList.value.filter((item) => item.status === 1).length)
const disabledCount = computed(() => userList.value.filter((item) => item.status === 0).length)
const teacherCount = computed(() => userList.value.filter((item) => item.userType === 1).length)
const isEditMode = computed(() => formMode.value === 'edit')
const formDialogTitle = computed(() => (isEditMode.value ? '编辑用户' : '新增用户'))

const emptyToUndefined = (value) => (value === '' || value === null ? undefined : value)

const fetchList = async () => {
  loading.value = true
  try {
    const res = await getUserList({
      page: currentPage.value,
      size: pageSize.value,
      username: searchForm.username || undefined,
      role: emptyToUndefined(searchForm.role),
      status: emptyToUndefined(searchForm.status)
    })
    const pageData = res.data || {}
    userList.value = pageData.records || []
    total.value = Number(pageData.total) || 0
  } catch (error) {
    console.error('获取用户列表失败', error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  currentPage.value = 1
  fetchList()
}

const handleReset = () => {
  searchForm.username = ''
  searchForm.role = ''
  searchForm.status = ''
  currentPage.value = 1
  fetchList()
}

const handleAdd = () => {
  formMode.value = 'add'
  editingUserId.value = null
  resetUserForm()
  formDialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate?.())
}

const handleEdit = (row) => {
  formMode.value = 'edit'
  editingUserId.value = row.id
  Object.assign(userForm, {
    username: row.username || '',
    nickname: row.nickname || '',
    userType: row.userType ?? 2,
    password: '',
    phone: row.phone || '',
    email: row.email || '',
    status: row.status ?? 1
  })
  formDialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate?.())
}

const resetUserForm = () => {
  Object.assign(userForm, {
    username: '',
    nickname: '',
    userType: 2,
    password: '',
    phone: '',
    email: '',
    status: 1
  })
  formRef.value?.clearValidate?.()
}

const buildUserPayload = () => {
  const payload = {
    username: userForm.username,
    nickname: userForm.nickname,
    userType: userForm.userType,
    phone: userForm.phone || null,
    email: userForm.email || null,
    status: userForm.status
  }
  if (userForm.password) {
    payload.password = userForm.password
  }
  return payload
}

const submitUserForm = async () => {
  const valid = await formRef.value?.validate?.().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (isEditMode.value) {
      await updateUser(editingUserId.value, buildUserPayload())
      ElMessage.success('用户信息已更新')
    } else {
      await createUser(buildUserPayload())
      ElMessage.success('用户已新增')
    }
    formDialogVisible.value = false
    fetchList()
  } finally {
    saving.value = false
  }
}

const handleStatusChange = async (row) => {
  const previousStatus = row.status === 1 ? 0 : 1
  try {
    await updateUser(row.id, { status: row.status })
    ElMessage.success(row.status === 1 ? '账号已启用' : '账号已停用')
  } catch (error) {
    row.status = previousStatus
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除账号「${row.username || '-'}」？`, '删除用户', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      customClass: 'app-confirm-dialog',
      showClose: false
    })
    await deleteUser(row.id)
    ElMessage.success('用户已删除')
    fetchList()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      console.error('删除用户失败', error)
    }
  }
}

const roleLabel = (type) => ({ 0: '管理员', 1: '教师', 2: '学生' })[type] || '未分配'

const roleTagType = (type) => ({ 0: 'danger', 1: 'success', 2: 'primary' })[type] || 'info'

const formatTime = (value) => {
  if (!value) return '-'
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0] = value
    return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(
      2,
      '0'
    )} ${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
  }
  return String(value).replace('T', ' ').slice(0, 16)
}

onMounted(fetchList)
</script>

<style scoped lang="scss">
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 14px;
}

.summary-card {
  padding: 16px 18px;
  background: var(--bg-color-container);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-1);

  .summary-label {
    display: block;
    color: var(--text-color-secondary);
    font-size: 13px;
  }

  strong {
    display: block;
    margin-top: 8px;
    color: var(--text-color);
    font-size: 26px;
    line-height: 1.1;
  }

  small {
    display: block;
    margin-top: 6px;
    color: var(--text-color-muted);
  }

  &.is-success strong {
    color: #2f7d32;
  }

  &.is-primary strong {
    color: var(--primary-color);
  }

  &.is-warning strong {
    color: #b56a00;
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

.user-table-card {
  :deep(.el-card__body) {
    padding-top: 0;
  }
}

.user-name {
  color: var(--text-color);
  font-weight: 600;
  line-height: 1.4;
}

.user-meta {
  color: var(--text-color-muted);
  font-size: 12px;
  line-height: 1.4;
}

.pagination {
  margin-top: 20px;
  justify-content: flex-end;
}

.dialog-form {
  max-width: 520px;
  margin: 4px auto 0;
}

@media (max-width: 960px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
