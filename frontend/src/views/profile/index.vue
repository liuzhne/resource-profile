<template>
  <div>
    <div class="detail-head">
      <span class="detail-title">我的画像</span>
    </div>

    <div class="detail-grid">
      <!-- 左：账号卡 -->
      <aside class="glass-panel profile-card">
        <div class="profile-top">
          <span class="avatar-lg me-avatar">{{ initialOf(displayName) }}</span>
          <h3 class="profile-name">{{ displayName || '—' }}</h3>
          <p class="profile-sub">{{ roleLabel }}</p>
          <span class="profile-tag">{{ userInfo?.username || '—' }}</span>
        </div>

        <div class="info-rows account-rows">
          <div class="info-row">
            <span class="k">用户名</span><span class="v">{{ userInfo?.username || '—' }}</span>
          </div>
          <div class="info-row">
            <span class="k">昵称</span><span class="v">{{ userInfo?.nickname || '—' }}</span>
          </div>
          <div class="info-row">
            <span class="k">角色</span><span class="v">{{ roleLabel }}</span>
          </div>
          <div class="info-row">
            <span class="k">邮箱</span><span class="v">{{ userInfo?.email || '—' }}</span>
          </div>
          <div class="info-row">
            <span class="k">电话</span><span class="v">{{ userInfo?.phone || '—' }}</span>
          </div>
          <div class="info-row">
            <span class="k">部门</span><span class="v">{{ userInfo?.deptName || '—' }}</span>
          </div>
        </div>

        <p class="no-source">
          <el-icon :size="14"><WarningFilled /></el-icon>
          <span>账号资料由本人维护，部门信息由管理员维护。</span>
        </p>
      </aside>

      <!-- 右：设置 -->
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
          <template v-if="activeTab === 'basic'">
            <div class="section-title">基本设置</div>
            <el-form :model="form" label-position="top" class="settings-form">
              <el-form-item label="昵称">
                <el-input v-model="form.nickname" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="form.email" />
              </el-form-item>
              <el-form-item label="电话">
                <el-input v-model="form.phone" />
              </el-form-item>
              <el-form-item label="个人简介">
                <el-input v-model="form.bio" type="textarea" :rows="4" />
              </el-form-item>
            </el-form>
            <el-button type="primary" class="submit-btn" :loading="saving" @click="saveProfile"
              >保存</el-button
            >
            <p class="no-source form-note">
              <el-icon :size="14"><WarningFilled /></el-icon>
              <span>请填写有效邮箱和联系电话。</span>
            </p>
          </template>

          <template v-else>
            <div class="section-title">安全设置</div>
            <el-form :model="passwordForm" label-position="top" class="settings-form">
              <el-form-item label="原密码">
                <el-input v-model="passwordForm.oldPassword" type="password" show-password />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="passwordForm.newPassword" type="password" show-password />
              </el-form-item>
              <el-form-item label="确认新密码">
                <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
              </el-form-item>
            </el-form>
            <el-button type="primary" class="submit-btn" :loading="saving" @click="savePassword"
              >修改密码</el-button
            >
            <p class="no-source form-note">
              <el-icon :size="14"><WarningFilled /></el-icon>
              <span>新密码至少12个字符。修改后需重新登录。</span>
            </p>
          </template>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { WarningFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { initialOf } from '@/utils/avatar'
import { updateProfile, changePassword } from '@/api/auth'

const userStore = useUserStore()

const tabs = [
  { key: 'basic', label: '基本设置' },
  { key: 'security', label: '安全设置' }
]

const activeTab = ref(useRoute().query.tab === 'security' ? 'security' : 'basic')

// 改版前本页是写死的假数据，此处改读 store 里的真实登录用户
const userInfo = computed(() => userStore.userInfo)
const displayName = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')

const roleLabel = computed(
  () => ({ 0: '系统管理员', 1: '教师', 2: '学生' })[userInfo.value?.userType] || '—'
)

const form = reactive({
  nickname: userInfo.value?.nickname || '',
  email: userInfo.value?.email || '',
  phone: userInfo.value?.phone || '',
  bio: userInfo.value?.bio || ''
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const saving = ref(false)
const saveProfile = async () => {
  saving.value = true
  try {
    await updateProfile(form)
    await userStore.getUserInfoAction()
    ElMessage.success('资料已保存')
  } finally {
    saving.value = false
  }
}
const savePassword = async () => {
  if (passwordForm.newPassword !== passwordForm.confirmPassword)
    return ElMessage.warning('两次新密码不一致')
  saving.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    })
    ElMessage.success('密码已修改，请重新登录')
    await userStore.clearSession()
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.me-avatar {
  background: linear-gradient(135deg, #a2c9ff, #7aa7f5);
}

.account-rows {
  margin-top: 22px;
}

.no-source {
  margin-top: 16px;
}

.settings-form {
  max-width: 460px;

  :deep(.el-form-item__label) {
    font-size: 12px;
    font-weight: 500;
    color: var(--text-color-secondary);
    padding-bottom: 6px;
    line-height: 1;
  }
}

.submit-btn {
  height: 38px;
  padding: 0 20px;
  font-size: 14px;
  font-weight: 600;
  border-radius: var(--radius-md);
  border: none;
}

.form-note {
  margin-top: 16px;
  max-width: 460px;
}
</style>
