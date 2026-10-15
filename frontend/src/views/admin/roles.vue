<template>
  <el-card>
    <template #header
      ><div class="card-header">
        <span>角色权限管理</span><el-button type="primary" @click="edit()">新增角色</el-button>
      </div></template
    >
    <p>
      角色限定数据敏感级别和操作上限；管理权限只能在角色支持的范围内配置。修改后相关账号需重新登录。
    </p>
    <el-table v-loading="loading" :data="rows" stripe>
      <el-table-column prop="name" label="角色名称" /><el-table-column prop="code" label="编码" />
      <el-table-column prop="description" label="描述" /><el-table-column prop="status" label="状态"
        ><template #default="{ row }">{{
          row.status === 1 ? '启用' : '停用'
        }}</template></el-table-column
      >
      <el-table-column prop="userCount" label="账号数" /><el-table-column
        prop="createTime"
        label="创建时间"
      />
      <el-table-column label="操作" width="220"
        ><template #default="{ row }"
          ><el-button link @click="permission(row)">权限设置</el-button
          ><el-button link @click="edit(row)">编辑</el-button
          ><el-button link type="danger" @click="remove(row)">删除</el-button></template
        ></el-table-column
      >
    </el-table>
    <el-dialog v-model="editing" title="角色资料" width="520px">
      <el-form label-position="top"
        ><el-form-item label="名称"><el-input v-model="form.name" maxlength="50" /></el-form-item>
        <el-form-item label="授权身份"
          ><el-select v-model="form.code" :disabled="!!form.id"
            ><el-option
              v-for="r in codes"
              :key="r.code"
              :value="r.code"
              :label="r.name" /></el-select
        ></el-form-item>
        <el-form-item label="描述"
          ><el-input v-model="form.description" maxlength="255"
        /></el-form-item>
        <el-form-item label="状态"
          ><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item
      ></el-form>
      <template #footer
        ><el-button @click="editing = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save">保存</el-button></template
      >
    </el-dialog>
    <el-dialog v-model="permissionsOpen" title="权限设置" width="600px">
      <p>当前角色：{{ activeRole?.name }}。灰色选项超出此角色的操作范围，不能分配。</p>
      <el-checkbox-group v-model="selected"
        ><el-checkbox
          v-for="p in catalog"
          :key="p.id"
          :value="String(p.id)"
          :label="String(p.id)"
          :disabled="activeRole?.code === 'admin' || !isSupportedPermission(p, activeRole?.code)"
          >{{ p.name }} ({{ p.code }})<span v-if="!isSupportedPermission(p, activeRole?.code)">
            · 此角色不支持</span
          ></el-checkbox
        ></el-checkbox-group
      >
      <p v-if="activeRole?.code === 'admin'">
        管理员固定保留全部维护权限；以下勾选显示固定权限，不能按此目录缩减。
      </p>
      <p v-if="activeRole?.code === 'student'">
        此目录用于教职工管理操作。学生本人档案、测评作答和历史记录由登录身份与本人校验控制，不受此目录开关影响。
      </p>
      <template #footer
        ><el-button @click="permissionsOpen = false">取消</el-button
        ><el-button
          v-if="activeRole?.code !== 'admin' && activeRole?.code !== 'student'"
          type="primary"
          :loading="saving"
          @click="savePermissions"
          >保存</el-button
        ></template
      >
    </el-dialog>
  </el-card>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRoles,
  saveRole,
  deleteRole,
  getPermissions,
  getRolePermissions,
  saveRolePermissions
} from '@/api/user'
import { isSupportedPermission, supportedSelection } from './rolePermissionContract'
const rows = ref([]),
  catalog = ref([]),
  selected = ref([]),
  roleId = ref(null),
  activeRole = ref(null),
  loading = ref(false),
  saving = ref(false),
  editing = ref(false),
  permissionsOpen = ref(false)
const codes = [
  { code: 'admin', name: '管理员' },
  { code: 'teacher', name: '教师' },
  { code: 'student', name: '学生' },
  { code: 'psychologist', name: '心理教师' },
  { code: 'counselor', name: '辅导员' },
  { code: 'academic_advisor', name: '学业导师' }
]
const form = reactive({ id: null, name: '', code: '', description: '', status: 1 })
async function load() {
  loading.value = true
  try {
    rows.value = (await getRoles()).data
  } finally {
    loading.value = false
  }
}
function edit(row) {
  Object.assign(form, { id: null, name: '', code: '', description: '', status: 1 }, row || {})
  editing.value = true
}
async function save() {
  if (!form.name || !form.code) return ElMessage.warning('请填写名称和授权身份')
  saving.value = true
  try {
    await saveRole(form)
    editing.value = false
    await load()
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}
async function remove(row) {
  try {
    await ElMessageBox.confirm('确认删除未使用的角色？', '删除角色')
    await deleteRole(row.id)
    await load()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') console.error(e)
  }
}
async function permission(row) {
  const [all, ids] = await Promise.all([getPermissions(), getRolePermissions(row.id)])
  catalog.value = all.data
  selected.value = supportedSelection(catalog.value, row.code, ids.data)
  roleId.value = row.id
  activeRole.value = { code: row.code, name: row.name }
  permissionsOpen.value = true
}
async function savePermissions() {
  saving.value = true
  try {
    await saveRolePermissions(
      roleId.value,
      supportedSelection(catalog.value, activeRole.value.code, selected.value)
    )
    permissionsOpen.value = false
    ElMessage.success('权限已保存，相关账号请重新登录')
  } finally {
    saving.value = false
  }
}
onMounted(load)
</script>
<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.el-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
