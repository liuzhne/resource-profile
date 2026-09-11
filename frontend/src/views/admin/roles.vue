<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">角色权限</h1>
        <div class="page-subtitle">
          管理系统角色、授权范围与功能菜单权限，确保不同用户进入对应工作台。
        </div>
      </div>
      <el-button type="primary" :icon="Plus" @click="handleAdd">新增角色</el-button>
    </div>

    <div class="summary-grid">
      <div class="summary-card">
        <span class="summary-label">角色总数</span>
        <strong>{{ roleList.length }}</strong>
        <small>系统内置与业务角色</small>
      </div>
      <div class="summary-card is-primary">
        <span class="summary-label">绑定用户</span>
        <strong>{{ totalBoundUsers }}</strong>
        <small>当前角色覆盖人数</small>
      </div>
      <div class="summary-card is-success">
        <span class="summary-label">业务角色</span>
        <strong>{{ businessRoleCount }}</strong>
        <small>教师、学生与心理教师</small>
      </div>
      <div class="summary-card is-warning">
        <span class="summary-label">敏感权限</span>
        <strong>{{ sensitivePermissionCount }}</strong>
        <small>涉及系统管理菜单</small>
      </div>
    </div>

    <el-card class="table-card role-table-card">
      <template #header>
        <div class="card-header">
          <div>
            <span>角色列表</span>
            <span class="result-count">共 {{ roleList.length }} 类</span>
          </div>
          <span class="card-tip">权限变更后建议重新登录验证菜单可见性</span>
        </div>
      </template>

      <el-table v-loading="loading" :data="roleList" stripe empty-text="暂无角色数据">
        <el-table-column type="index" width="50" />
        <el-table-column prop="name" label="角色" min-width="180">
          <template #default="{ row }">
            <div class="role-name">{{ row.name }}</div>
            <el-tag :type="roleTagType(row.code)" effect="plain" size="small">
              {{ row.code }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="职责说明" min-width="260" />
        <el-table-column prop="scope" label="授权范围" min-width="130">
          <template #default="{ row }">
            <el-tag :type="scopeTagType(row.scope)" effect="plain">
              {{ row.scope }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="permissionCount" label="菜单权限" width="110" align="center">
          <template #default="{ row }">
            <span class="permission-count">{{ row.permissionCount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="userCount" label="绑定用户" width="110" align="center" />
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :icon="Setting" @click="handlePermission(row)"
              >权限设置</el-button
            >
            <el-button link type="primary" :icon="EditPen" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" :icon="Delete" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="permissionDialogVisible"
      class="permission-dialog"
      width="640px"
      :title="permissionDialogTitle"
    >
      <div class="permission-summary">
        <div>
          <span>角色编码</span>
          <strong>{{ selectedRole?.code || '-' }}</strong>
        </div>
        <div>
          <span>绑定用户</span>
          <strong>{{ selectedRole?.userCount ?? '-' }}</strong>
        </div>
        <div>
          <span>授权范围</span>
          <strong>{{ selectedRole?.scope || '-' }}</strong>
        </div>
      </div>

      <div class="permission-tree-wrap">
        <el-tree
          ref="permissionTree"
          :data="permissionData"
          show-checkbox
          default-expand-all
          node-key="id"
          :default-checked-keys="selectedPermissionKeys"
          :props="{ label: 'name', children: 'children' }"
        />
      </div>

      <template #footer>
        <el-button @click="permissionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="savePermission">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="roleDialogVisible"
      class="role-form-dialog"
      width="640px"
      :title="roleDialogTitle"
      destroy-on-close
      @closed="resetRoleForm"
    >
      <el-form
        ref="roleFormRef"
        :model="roleForm"
        :rules="roleRules"
        label-width="92px"
        class="dialog-form"
      >
        <el-form-item label="角色名称" prop="name">
          <el-input v-model.trim="roleForm.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码" prop="code">
          <el-input
            v-model.trim="roleForm.code"
            :disabled="isRoleEditMode"
            placeholder="例如 academic_advisor"
          />
        </el-form-item>
        <el-form-item label="授权范围" prop="scope">
          <el-select v-model="roleForm.scope" style="width: 100%">
            <el-option v-for="item in SCOPE_OPTIONS" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定用户" prop="userCount">
          <el-input-number
            v-model="roleForm.userCount"
            :min="0"
            :max="99999"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="职责说明" prop="description">
          <el-input
            v-model.trim="roleForm.description"
            type="textarea"
            :rows="3"
            maxlength="120"
            show-word-limit
            placeholder="请输入该角色的职责说明"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRoleForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, EditPen, Plus, Setting } from '@element-plus/icons-vue'

const loading = ref(false)
const permissionDialogVisible = ref(false)
const permissionTree = ref(null)
const selectedRole = ref(null)
const selectedPermissionKeys = ref([])
const roleDialogVisible = ref(false)
const roleFormRef = ref(null)
const roleMode = ref('add')
const editingRoleId = ref(null)

const SCOPE_OPTIONS = ['全平台', '学院/班级', '本人', '心理中心', '自定义']

const roleList = ref([
  {
    id: 1,
    name: '系统管理员',
    code: 'admin',
    description: '拥有平台配置、账号管理、画像数据查看等全部权限',
    scope: '全平台',
    permissionCount: 13,
    userCount: 2,
    createTime: '2025-01-01 00:00',
    permissionKeys: [1, 11, 12, 2, 21, 22, 3, 31, 32, 4, 41, 42, 43]
  },
  {
    id: 2,
    name: '教师',
    code: 'teacher',
    description: '查看教师画像、学生基础档案与班级相关分析',
    scope: '学院/班级',
    permissionCount: 6,
    userCount: 128,
    createTime: '2025-01-01 00:00',
    permissionKeys: [2, 21, 22, 3, 31, 32]
  },
  {
    id: 3,
    name: '学生',
    code: 'student',
    description: '查看个人画像、问卷作答与历史评估结果',
    scope: '本人',
    permissionCount: 4,
    userCount: 2456,
    createTime: '2025-01-01 00:00',
    permissionKeys: [3, 32, 4, 44]
  },
  {
    id: 4,
    name: '心理健康教师',
    code: 'mental_teacher',
    description: '管理心理健康问卷、分析报告与预警复核流程',
    scope: '心理中心',
    permissionCount: 7,
    userCount: 5,
    createTime: '2025-01-01 00:00',
    permissionKeys: [3, 31, 32, 4, 41, 42, 43]
  }
])

const permissionData = ref([
  {
    id: 1,
    name: '系统管理',
    children: [
      { id: 11, name: '用户管理' },
      { id: 12, name: '角色权限' },
      { id: 13, name: '系统配置' }
    ]
  },
  {
    id: 2,
    name: '教师画像',
    children: [
      { id: 21, name: '教师列表' },
      { id: 22, name: '教师详情' }
    ]
  },
  {
    id: 3,
    name: '学生画像',
    children: [
      { id: 31, name: '学生列表' },
      { id: 32, name: '学生详情' }
    ]
  },
  {
    id: 4,
    name: '心理健康',
    children: [
      { id: 41, name: '心理概览' },
      { id: 42, name: '问卷管理' },
      { id: 43, name: '分析报告' },
      { id: 44, name: '学生作答' }
    ]
  }
])

const totalBoundUsers = computed(() =>
  roleList.value.reduce((sum, item) => sum + item.userCount, 0)
)
const businessRoleCount = computed(
  () => roleList.value.filter((item) => item.code !== 'admin').length
)
const sensitivePermissionCount = computed(
  () =>
    roleList.value.filter((item) =>
      item.permissionKeys.some((permission) => [1, 11, 12, 13].includes(permission))
    ).length
)
const permissionDialogTitle = computed(() =>
  selectedRole.value ? `权限设置 - ${selectedRole.value.name}` : '权限设置'
)
const isRoleEditMode = computed(() => roleMode.value === 'edit')
const roleDialogTitle = computed(() => (isRoleEditMode.value ? '编辑角色' : '新增角色'))

const roleForm = reactive({
  name: '',
  code: '',
  description: '',
  scope: '自定义',
  userCount: 0
})

const roleCodeValidator = (_rule, value, callback) => {
  if (!value) {
    callback(new Error('请输入角色编码'))
    return
  }
  if (!/^[a-z][a-z0-9_]*$/.test(value)) {
    callback(new Error('编码需以小写字母开头，仅支持小写字母、数字和下划线'))
    return
  }
  const exists = roleList.value.some(
    (item) => item.code === value && item.id !== editingRoleId.value
  )
  if (exists) {
    callback(new Error('角色编码已存在'))
    return
  }
  callback()
}

const roleRules = {
  name: [{ required: true, message: '请输入角色名称', trigger: 'blur' }],
  code: [{ validator: roleCodeValidator, trigger: 'blur' }],
  scope: [{ required: true, message: '请选择授权范围', trigger: 'change' }],
  description: [{ required: true, message: '请输入职责说明', trigger: 'blur' }]
}

const handleAdd = () => {
  roleMode.value = 'add'
  editingRoleId.value = null
  resetRoleForm()
  roleDialogVisible.value = true
  nextTick(() => roleFormRef.value?.clearValidate?.())
}

const handleEdit = (row) => {
  roleMode.value = 'edit'
  editingRoleId.value = row.id
  Object.assign(roleForm, {
    name: row.name,
    code: row.code,
    description: row.description,
    scope: row.scope,
    userCount: row.userCount
  })
  roleDialogVisible.value = true
  nextTick(() => roleFormRef.value?.clearValidate?.())
}

const resetRoleForm = () => {
  Object.assign(roleForm, {
    name: '',
    code: '',
    description: '',
    scope: '自定义',
    userCount: 0
  })
  roleFormRef.value?.clearValidate?.()
}

const submitRoleForm = async () => {
  const valid = await roleFormRef.value?.validate?.().catch(() => false)
  if (!valid) return

  if (isRoleEditMode.value) {
    const target = roleList.value.find((item) => item.id === editingRoleId.value)
    if (target) {
      Object.assign(target, {
        name: roleForm.name,
        description: roleForm.description,
        scope: roleForm.scope,
        userCount: roleForm.userCount
      })
    }
    ElMessage.success('角色信息已更新')
  } else {
    roleList.value.unshift({
      id: Date.now(),
      name: roleForm.name,
      code: roleForm.code,
      description: roleForm.description,
      scope: roleForm.scope,
      permissionCount: 0,
      userCount: roleForm.userCount,
      createTime: formatNow(),
      permissionKeys: []
    })
    ElMessage.success('角色已新增')
  }
  roleDialogVisible.value = false
}

const handleDelete = async (row) => {
  if (row.code === 'admin') {
    ElMessage.warning('系统管理员为内置角色，不能删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除角色「${row.name}」？`, '删除角色', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      customClass: 'app-confirm-dialog',
      showClose: false
    })
    roleList.value = roleList.value.filter((item) => item.id !== row.id)
    ElMessage.success('角色已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      console.error('删除角色失败', error)
    }
  }
}

const handlePermission = (row) => {
  selectedRole.value = row
  selectedPermissionKeys.value = [...row.permissionKeys]
  permissionDialogVisible.value = true
  nextTick(() => {
    permissionTree.value?.setCheckedKeys?.(row.permissionKeys)
  })
}

const savePermission = () => {
  const checkedKeys = permissionTree.value?.getCheckedKeys?.() || []
  if (selectedRole.value) {
    selectedRole.value.permissionKeys = checkedKeys
    selectedRole.value.permissionCount = checkedKeys.length
  }
  permissionDialogVisible.value = false
  ElMessage.success('权限设置已保存')
}

const roleTagType = (code) =>
  ({
    admin: 'danger',
    teacher: 'success',
    student: 'primary',
    mental_teacher: 'warning'
  })[code] || 'info'

const scopeTagType = (scope) =>
  ({
    全平台: 'danger',
    '学院/班级': 'success',
    本人: 'primary',
    心理中心: 'warning',
    自定义: 'info'
  })[scope] || 'info'

const formatNow = () => {
  const date = new Date()
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(
    date.getDate()
  )} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}
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

  &.is-primary strong {
    color: var(--primary-color);
  }

  &.is-success strong {
    color: #2f7d32;
  }

  &.is-warning strong {
    color: #b56a00;
  }
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;

  .result-count {
    margin-left: 10px;
    color: var(--text-color-muted);
    font-size: 12px;
    font-weight: 400;
  }

  .card-tip {
    color: var(--text-color-muted);
    font-size: 13px;
  }
}

.role-table-card {
  :deep(.el-card__body) {
    padding-top: 0;
  }
}

.role-name {
  margin-bottom: 6px;
  color: var(--text-color);
  font-weight: 600;
  line-height: 1.4;
}

.permission-count {
  color: var(--primary-color);
  font-weight: 700;
}

.permission-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;

  > div {
    padding: 12px 14px;
    background: var(--bg-color-subtle);
    border: 1px solid var(--border-color-light);
    border-radius: var(--radius-base);
  }

  span {
    display: block;
    color: var(--text-color-secondary);
    font-size: 12px;
  }

  strong {
    display: block;
    margin-top: 6px;
    color: var(--text-color);
    font-size: 15px;
  }
}

.permission-tree-wrap {
  max-height: 360px;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
}

.dialog-form {
  max-width: 520px;
  margin: 4px auto 0;
}

@media (max-width: 960px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .permission-summary {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
