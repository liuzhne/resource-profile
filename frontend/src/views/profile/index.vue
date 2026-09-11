<template>
  <div class="page-container profile-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">我的画像</h1>
        <div class="page-subtitle">查看当前账号信息、角色权限与基础安全设置。</div>
      </div>
    </div>

    <div class="profile-layout">
      <aside class="profile-card">
        <div class="avatar-section">
          <el-avatar :size="84" :src="profile.avatar">
            {{ avatarText }}
          </el-avatar>
          <h2>{{ profile.name }}</h2>
          <p>{{ roleLabel }}</p>
          <div class="role-tags">
            <el-tag
              v-for="role in profile.roles"
              :key="role"
              :type="roleTagType(role)"
              effect="plain"
            >
              {{ roleText(role) }}
            </el-tag>
          </div>
        </div>

        <el-divider />

        <div class="info-list">
          <div class="info-item">
            <span>用户名</span>
            <strong>{{ displayValue(profile.username) }}</strong>
          </div>
          <div class="info-item">
            <span>邮箱</span>
            <strong>{{ displayValue(profile.email) }}</strong>
          </div>
          <div class="info-item">
            <span>电话</span>
            <strong>{{ displayValue(profile.phone) }}</strong>
          </div>
          <div class="info-item">
            <span>部门</span>
            <strong>{{ displayValue(profile.dept) }}</strong>
          </div>
        </div>
      </aside>

      <main class="profile-main">
        <div class="summary-grid">
          <div class="summary-card">
            <span>账号状态</span>
            <strong>{{ statusText }}</strong>
            <p>来自当前登录态</p>
          </div>
          <div class="summary-card is-primary">
            <span>角色数量</span>
            <strong>{{ profile.roles.length }}</strong>
            <p>影响菜单和数据权限</p>
          </div>
          <div class="summary-card is-muted">
            <span>资料完整度</span>
            <strong>{{ completionRate }}%</strong>
            <p>邮箱、电话、部门等字段</p>
          </div>
        </div>

        <el-card class="settings-card">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="基本设置" name="basic">
              <el-form :model="form" label-width="90px" class="settings-form">
                <el-form-item label="昵称">
                  <el-input v-model="form.nickname" placeholder="请输入昵称" />
                </el-form-item>
                <el-form-item label="邮箱">
                  <el-input v-model="form.email" placeholder="请输入邮箱" />
                </el-form-item>
                <el-form-item label="电话">
                  <el-input v-model="form.phone" placeholder="请输入电话" />
                </el-form-item>
                <el-form-item label="个人简介">
                  <el-input
                    v-model="form.bio"
                    type="textarea"
                    :rows="4"
                    placeholder="补充个人简介"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="handleSaveProfile"> 保存 </el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>

            <el-tab-pane label="安全设置" name="security">
              <el-form :model="passwordForm" label-width="100px" class="settings-form">
                <el-form-item label="原密码">
                  <el-input
                    v-model="passwordForm.oldPassword"
                    type="password"
                    show-password
                    placeholder="请输入原密码"
                  />
                </el-form-item>
                <el-form-item label="新密码">
                  <el-input
                    v-model="passwordForm.newPassword"
                    type="password"
                    show-password
                    placeholder="请输入新密码"
                  />
                </el-form-item>
                <el-form-item label="确认密码">
                  <el-input
                    v-model="passwordForm.confirmPassword"
                    type="password"
                    show-password
                    placeholder="再次输入新密码"
                  />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="handleChangePassword"> 修改密码 </el-button>
                </el-form-item>
              </el-form>
            </el-tab-pane>
          </el-tabs>
        </el-card>
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watchEffect } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const activeTab = ref('basic')

const displayValue = (value) =>
  value === null || value === undefined || value === '' ? '-' : value

const profile = computed(() => {
  const info = userStore.userInfo || {}
  const roles = info.roles || (info.role ? [info.role] : [])
  return {
    name: info.nickname || info.name || info.username || '当前用户',
    username: info.username || '-',
    avatar: info.avatar || '',
    email: info.email || '',
    phone: info.phone || '',
    dept: info.deptName || info.dept || info.department || '',
    status: info.status,
    roles
  }
})

const form = reactive({
  nickname: '',
  email: '',
  phone: '',
  bio: ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

watchEffect(() => {
  form.nickname = profile.value.name === '当前用户' ? '' : profile.value.name
  form.email = profile.value.email
  form.phone = profile.value.phone
})

const avatarText = computed(() => profile.value.name.slice(0, 1))

const roleText = (role) =>
  ({
    admin: '管理员',
    teacher: '教师',
    student: '学生',
    psychologist: '心理教师',
    mental_teacher: '心理教师'
  })[role] || role

const roleTagType = (role) =>
  ({
    admin: 'danger',
    teacher: 'success',
    student: 'primary',
    psychologist: 'warning',
    mental_teacher: 'warning'
  })[role] || 'info'

const roleLabel = computed(() => {
  if (!profile.value.roles.length) return '未分配角色'
  return profile.value.roles.map((role) => roleText(role)).join(' / ')
})

const statusText = computed(() => {
  if (profile.value.status === 0) return '停用'
  if (profile.value.status === 1) return '启用'
  return userStore.isLoggedIn ? '已登录' : '未登录'
})

const completionRate = computed(() => {
  const fields = [
    profile.value.username,
    profile.value.email,
    profile.value.phone,
    profile.value.dept
  ]
  const filled = fields.filter(
    (value) => value !== null && value !== undefined && value !== ''
  ).length
  return Math.round((filled / fields.length) * 100)
})

const handleSaveProfile = () => {
  ElMessage.info('个人资料保存接口暂未接入')
}

const handleChangePassword = () => {
  if (!passwordForm.oldPassword || !passwordForm.newPassword) {
    ElMessage.warning('请输入原密码和新密码')
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  ElMessage.info('密码修改接口暂未接入')
}
</script>

<style scoped lang="scss">
.profile-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 16px;
}

.profile-card,
.settings-card,
.summary-card {
  background: var(--bg-color-container);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-base);
  box-shadow: var(--shadow-1);
}

.profile-card {
  padding: 20px;
}

.avatar-section {
  text-align: center;
  padding: 8px 0;

  h2 {
    margin: 14px 0 6px;
    color: var(--text-color);
    font-size: 20px;
  }

  p {
    margin: 0;
    color: var(--text-color-secondary);
  }
}

.role-tags {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.info-list {
  display: flex;
  flex-direction: column;
}

.info-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 0;
  border-bottom: 1px solid var(--border-color);

  &:last-child {
    border-bottom: none;
  }

  span {
    color: var(--text-color-secondary);
  }

  strong {
    color: var(--text-color);
    text-align: right;
    word-break: break-word;
  }
}

.profile-main {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.summary-card {
  min-height: 112px;
  padding: 18px;

  span {
    display: block;
    color: var(--text-color-secondary);
    font-size: 13px;
  }

  strong {
    display: block;
    margin-top: 10px;
    color: var(--text-color);
    font-size: 26px;
    line-height: 1;
  }

  p {
    margin: 12px 0 0;
    color: var(--text-color-muted);
    font-size: 12px;
  }

  &.is-primary strong {
    color: var(--primary-color);
  }

  &.is-muted strong {
    color: #64748b;
  }
}

.settings-card {
  padding: 0 18px 18px;

  :deep(.el-tabs__header) {
    margin-bottom: 18px;
  }
}

.settings-form {
  max-width: 560px;
}

@media (max-width: 980px) {
  .profile-layout {
    grid-template-columns: 1fr;
  }

  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
