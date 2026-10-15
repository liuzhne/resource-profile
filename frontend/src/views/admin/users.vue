<template>
  <div class="glass-panel list-panel">
    <div class="page-head">
      <div>
        <div class="head-title">用户管理</div>
        <div v-if="loading" class="head-sub">正在加载账号…</div>
        <div v-else-if="loadError" class="head-sub">账号列表暂时无法加载</div>
        <div v-else class="head-sub">
          共 <b>{{ total }}</b> 个账号
        </div>
      </div>
      <el-button type="primary" class="head-action" :icon="Plus" @click="editUser()">
        新增用户
      </el-button>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="searchForm.username"
        placeholder="请输入用户名"
        clearable
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
      />
      <el-select v-model="searchForm.status" placeholder="请选择状态" clearable>
        <el-option label="启用" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-select v-model="searchForm.role" placeholder="请选择角色" clearable
        ><el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id"
      /></el-select>
      <el-button class="btn-query" @click="handleSearch">查询</el-button>
      <el-button class="btn-reset" @click="handleReset">重置</el-button>
    </div>

    <p class="read-only-note">
      新增账号需设置至少12个字符的密码。角色或状态变更将撤销该账号已有会话。
    </p>

    <ChartState
      :loading="loading"
      :error="loadError"
      :empty="!loading && !loadError && !userList.length"
      empty-text="没有符合条件的账号"
      height="280px"
      @retry="fetchList"
    >
      <div class="table-scroll">
        <table class="plain-table users-table">
          <thead>
            <tr>
              <th>用户名</th>
              <th>昵称</th>
              <th>账户类型</th>
              <th>邮箱</th>
              <th>电话</th>
              <th>状态</th>
              <th>创建时间</th>
              <th class="col-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, i) in userList" :key="row.id">
              <td class="strong">{{ row.username }}</td>
              <td>
                <span class="name-cell">
                  <span class="avatar-sm" :style="{ background: avatarBg(i) }">
                    {{ initialOf(row.nickname || row.username) }}
                  </span>
                  <span>{{ row.nickname || '—' }}</span>
                </span>
              </td>
              <td>
                <span class="badge" :style="typeStyle(row.userType)">{{
                  typeLabel(row.userType)
                }}</span>
              </td>
              <td>{{ row.email || '—' }}</td>
              <td class="tnum">{{ row.phone || '—' }}</td>
              <td>
                <el-switch
                  :model-value="row.status"
                  :active-value="1"
                  :inactive-value="0"
                  @change="statusChange(row, $event)"
                />
              </td>
              <td class="tnum">{{ displayTime(row.createTime) }}</td>
              <td class="col-right">
                <el-button link @click="editUser(row)">编辑</el-button>
                <el-button link type="danger" @click="removeUser(row)">删除</el-button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </ChartState>

    <div class="table-foot">
      <span class="foot-count">
        第 {{ rangeStart }}–{{ rangeEnd }} 条，共 <b>{{ total }}</b> 条
      </span>
      <el-pagination
        :current-page="currentPage"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        :disabled="loading"
        layout="sizes, prev, pager, next"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
    <el-dialog v-model="editing" title="账号资料" width="560px">
      <el-form label-position="top"
        ><el-form-item label="用户名"
          ><el-input v-model="form.username" maxlength="50"
        /></el-form-item>
        <el-form-item :label="form.id ? '新密码（留空保留原密码）' : '初始密码'"
          ><el-input v-model="form.password" type="password" show-password
        /></el-form-item>
        <el-form-item label="昵称"
          ><el-input v-model="form.nickname" maxlength="50"
        /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item
        ><el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="部门"
          ><el-input v-model="form.deptName" maxlength="100"
        /></el-form-item>
        <el-form-item label="账号类型"
          ><el-select v-model="form.userType"
            ><el-option :value="0" label="管理员" /><el-option :value="1" label="教师" /><el-option
              :value="2"
              label="学生" /></el-select
        ></el-form-item>
        <el-form-item label="角色"
          ><el-select v-model="form.roleIds" multiple
            ><el-option
              v-for="r in roles.filter((r) => r.status === 1)"
              :key="r.id"
              :label="r.name"
              :value="String(r.id)" /></el-select
        ></el-form-item>
        <el-form-item label="状态"
          ><el-switch v-model="form.status" :active-value="1" :inactive-value="0" /></el-form-item
      ></el-form>
      <template #footer
        ><el-button @click="editing = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="submitUser">保存</el-button></template
      >
    </el-dialog>
    <el-divider>待核验账号申请</el-divider>
    <el-table :data="accountRequests"
      ><el-table-column prop="type" label="类型" /><el-table-column
        prop="username"
        label="用户名"
      /><el-table-column prop="contact" label="联系方式" /><el-table-column
        prop="reason"
        label="说明"
      /><el-table-column prop="createTime" label="提交时间" /><el-table-column label="操作"
        ><template #default="{ row }"
          ><el-button link @click="resolveRequest(row, 'RESOLVED')">已核验处理</el-button
          ><el-button link @click="resolveRequest(row, 'REJECTED')">拒绝</el-button></template
        ></el-table-column
      ></el-table
    >
    <p>开通账号或重置密码前，请通过既有校内渠道人工核验身份；处理申请不会自动修改账号。</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getRoles,
  getUserRoles,
  saveUser,
  deleteUser,
  getAccountRequests,
  resolveAccountRequest
} from '@/api/user'
import { Plus, Search } from '@element-plus/icons-vue'
import { avatarBg, initialOf } from '@/utils/avatar'
import ChartState from '@/views/dashboard/components/ChartState.vue'
import { useUserList } from '@/composables/useUserList'

const {
  loading,
  loadError,
  userList,
  total,
  currentPage,
  pageSize,
  searchForm,
  rangeStart,
  rangeEnd,
  fetchList,
  handleSearch,
  handleReset,
  handlePageChange,
  handleSizeChange
} = useUserList()

const typeLabel = (userType) => ({ 0: '管理员', 1: '教师', 2: '学生' })[userType] || '—'
const displayTime = (value) => (value ? String(value).replace('T', ' ') : '—')
const typeStyle = (userType) =>
  ({
    0: { background: 'var(--error-tint)', color: 'var(--error-deep)' },
    1: { background: 'var(--success-tint)', color: 'var(--success-deep)' },
    2: { background: 'var(--primary-tint)', color: 'var(--primary-color)' }
  })[userType] || { background: 'var(--fill-grey)', color: 'var(--fill-grey-fg)' }

const accountRequests = ref([])
async function loadRequests() {
  accountRequests.value = (await getAccountRequests()).data
}
async function resolveRequest(row, status) {
  try {
    const result = await ElMessageBox.prompt('填写核验结果与处理说明，至少5个字', '处理申请')
    await resolveAccountRequest(row.id, { status, resolution: result.value })
    await loadRequests()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') console.error(e)
  }
}
const roles = ref([]),
  editing = ref(false),
  saving = ref(false)
const form = reactive({
  id: null,
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  deptName: '',
  bio: '',
  userType: 2,
  status: 1,
  roleIds: []
})
async function editUser(row) {
  Object.assign(
    form,
    {
      id: null,
      username: '',
      password: '',
      nickname: '',
      email: '',
      phone: '',
      deptName: '',
      bio: '',
      userType: 2,
      status: 1,
      roleIds: []
    },
    row || {}
  )
  form.password = ''
  if (row) form.roleIds = (await getUserRoles(row.id)).data.map(String)
  editing.value = true
}
async function submitUser() {
  saving.value = true
  try {
    await saveUser(form)
    editing.value = false
    await fetchList()
    ElMessage.success('已保存')
  } finally {
    saving.value = false
  }
}
async function statusChange(row, status) {
  const roleIds = (await getUserRoles(row.id)).data
  await saveUser({ ...row, password: '', roleIds, status })
  await fetchList()
}
async function removeUser(row) {
  try {
    await ElMessageBox.confirm('确认删除未绑定档案的账号？', '删除账号')
    await deleteUser(row.id)
    await fetchList()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') console.error(e)
  }
}
onMounted(async () => {
  await Promise.all([
    fetchList(),
    loadRequests(),
    getRoles().then((res) => {
      roles.value = res.data
    })
  ])
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

.filter-bar {
  :deep(.el-input) {
    width: 190px;
  }

  :deep(.el-select) {
    width: 150px;
  }
}

.read-only-note {
  margin: 0 20px 14px;
  color: var(--text-color-secondary);
  font-size: 12px;
}

.users-table {
  min-width: 900px;
}
</style>
