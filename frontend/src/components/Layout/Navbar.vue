<template>
  <div class="navbar">
    <!-- 左侧：折叠按钮和面包屑 -->
    <div class="left">
      <div class="icon-btn collapse-btn" @click="toggleSidebar">
        <el-icon :size="17">
          <Fold v-if="!sidebarCollapsed" />
          <Expand v-else />
        </el-icon>
      </div>
      <Breadcrumb />
    </div>

    <!-- 右侧：工具区 + 用户 -->
    <div class="right">
      <button
        v-if="canUseDataTools"
        class="icon-btn tool-button"
        aria-label="全局搜索"
        title="搜索师生档案"
        @click="searchVisible = true"
      >
        <el-icon :size="17"><Search /></el-icon>
      </button>
      <el-badge
        v-if="canUseDataTools"
        :value="notificationTotal"
        :max="99"
        :hidden="!notificationTotal"
      >
        <button
          class="icon-btn tool-button"
          aria-label="待跟进通知"
          title="待跟进通知"
          @click="openNotifications"
        >
          <el-icon :size="17"><Bell /></el-icon>
        </button>
      </el-badge>

      <el-dropdown trigger="click">
        <div class="user-info">
          <span class="avatar">{{ avatarText }}</span>
          <span class="username">{{ displayName }}</span>
          <el-icon :size="11"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="goToProfile">
              <el-icon><User /></el-icon> 个人中心
            </el-dropdown-item>
            <el-dropdown-item @click="goToSettings">
              <el-icon><Setting /></el-icon> 系统设置
            </el-dropdown-item>
            <el-dropdown-item divided :disabled="logoutPending" @click="handleLogout">
              <el-icon><SwitchButton /></el-icon> 退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <el-dialog
      v-model="searchVisible"
      title="搜索师生档案"
      width="min(620px, 94vw)"
      destroy-on-close
    >
      <form class="search-form" @submit.prevent="search">
        <el-input
          v-model="searchQuery"
          placeholder="姓名、工号或学号（至少2字）"
          clearable
          maxlength="50"
          @input="invalidateSearch"
        />
        <el-button
          native-type="submit"
          type="primary"
          :loading="searchLoading"
          :disabled="searchQuery.trim().length < 2"
          >搜索</el-button
        >
      </form>
      <ChartState
        :loading="searchLoading"
        :error="searchError"
        :empty="!searchResults.length"
        :empty-text="searchDone ? '没有匹配的师生档案' : '输入关键词开始搜索'"
        height="180px"
        @retry="search"
      >
        <button
          v-for="result in searchResults"
          :key="`${result.type}:${result.id}`"
          class="tool-result"
          type="button"
          @click="openResult(result)"
        >
          <strong>{{ result.name }}</strong
          ><span>{{ result.type === 'teacher' ? '教师' : '学生' }} · {{ result.code }}</span
          ><span>{{ result.deptName || '—' }}</span>
        </button>
      </ChartState>
    </el-dialog>
    <el-dialog
      v-model="notificationsVisible"
      title="待跟进通知"
      width="min(620px, 94vw)"
      destroy-on-close
    >
      <p class="notifications-count">
        共 {{ notificationTotal }} 项，显示最近 {{ notifications.length }} 项
      </p>
      <ChartState
        :loading="notificationsLoading"
        :error="notificationsError"
        :empty="!notifications.length"
        empty-text="暂无待跟进事项"
        height="180px"
        @retry="loadNotifications"
      >
        <button
          v-for="item in notifications"
          :key="`${item.kind}:${item.sourceId}`"
          class="tool-result"
          type="button"
          @click="openNotification(item)"
        >
          <strong>{{ item.title }} #{{ item.sourceId }}</strong
          ><span>{{ todoStatusLabel(item.status) }} · {{ formatEventTime(item.eventTime) }}</span>
        </button>
      </ChartState>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import Breadcrumb from './Breadcrumb.vue'
import ChartState from '@/views/dashboard/components/ChartState.vue'
import { STAFF_VIEW_ROLES } from '@/router/access'
import { searchDirectory, getNotifications } from '@/api/dashboard'
import { todoTarget, todoStatusLabel, formatEventTime } from '@/views/dashboard/todoContract'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const logoutPending = ref(false)

const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const userInfo = computed(() => userStore.userInfo)

const displayName = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')
const avatarText = computed(() => displayName.value.charAt(0) || '?')

const toggleSidebar = () => {
  appStore.toggleSidebar()
}

interface DirectoryItem {
  type: string
  id: string | number
  name: string
  code: string
  deptName?: string
}
interface NotificationItem {
  kind: string
  sourceId: string | number
  studentId?: string | number
  title: string
  status: string
  eventTime?: string
}
const canUseDataTools = computed(
  () =>
    userStore.userRoles.some((role) => STAFF_VIEW_ROLES.includes(role)) &&
    (userStore.userRoles.includes('admin') ||
      !Array.isArray(userInfo.value?.permissions) ||
      userInfo.value.permissions.includes('data:read'))
)
const searchVisible = ref(false),
  searchQuery = ref(''),
  searchLoading = ref(false),
  searchError = ref(false),
  searchDone = ref(false)
const searchResults = ref<DirectoryItem[]>([])
const notificationsVisible = ref(false),
  notificationsLoading = ref(false),
  notificationsError = ref(false),
  notificationTotal = ref(0)
const notifications = ref<NotificationItem[]>([])
let searchVersion = 0
const invalidateSearch = () => {
  searchVersion++
  searchLoading.value = false
  searchResults.value = []
  searchDone.value = false
  searchError.value = false
}
const search = async () => {
  const query = searchQuery.value.trim()
  if (query.length < 2) return
  const version = ++searchVersion
  searchLoading.value = true
  searchError.value = false
  searchDone.value = true
  try {
    const response = await searchDirectory(query)
    if (version === searchVersion) searchResults.value = response.data || []
  } catch {
    if (version === searchVersion) {
      searchResults.value = []
      searchError.value = true
    }
  } finally {
    if (version === searchVersion) searchLoading.value = false
  }
}
const openResult = (result: DirectoryItem) => {
  searchVisible.value = false
  router.push(
    `/${result.type === 'teacher' ? 'teacher' : 'student'}/detail/${encodeURIComponent(result.id)}`
  )
}
const loadNotifications = async () => {
  if (!canUseDataTools.value) return
  notificationsLoading.value = true
  notificationsError.value = false
  try {
    const response = await getNotifications()
    notificationTotal.value = Number(response.data?.total) || 0
    notifications.value = response.data?.items || []
  } catch {
    notifications.value = []
    notificationTotal.value = 0
    notificationsError.value = true
  } finally {
    notificationsLoading.value = false
  }
}
const openNotifications = () => {
  notificationsVisible.value = true
  loadNotifications()
}
const openNotification = (item: NotificationItem) => {
  notificationsVisible.value = false
  router.push(todoTarget(item, import.meta.env.VITE_AI_ENABLED === 'true'))
}
watch(
  canUseDataTools,
  (allowed) => {
    if (allowed) loadNotifications()
    else {
      notifications.value = []
      notificationTotal.value = 0
    }
  },
  { immediate: true }
)

const goToProfile = () => {
  router.push('/profile')
}

const goToSettings = () => {
  router.push({ path: '/profile', query: { tab: 'security' } })
}

const handleLogout = async () => {
  if (logoutPending.value) return
  logoutPending.value = true
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await userStore.logoutAction()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error('退出登录失败，请检查网络后重试')
    }
  } finally {
    logoutPending.value = false
  }
}
</script>

<style scoped lang="scss">
.tool-button {
  border: 0;
  background: transparent;
}
.search-form {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.tool-result {
  display: flex;
  flex-direction: column;
  gap: 5px;
  width: 100%;
  text-align: left;
  border: 0;
  background: transparent;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  font: inherit;
}
.tool-result:hover {
  background: var(--fill-hover);
}
.tool-result span,
.notifications-count {
  color: var(--text-color-secondary);
  font-size: 13px;
}
.notifications-count {
  margin-bottom: 12px;
}

.navbar {
  height: var(--header-height);
  flex-shrink: 0;
  background: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  gap: 12px;

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(255, 255, 255, 0.96);
  }

  .left {
    display: flex;
    align-items: center;
    gap: 14px;
    min-width: 0;

    .collapse-btn {
      width: 32px;
      height: 32px;
      border-radius: 9px;
    }
  }

  .right {
    display: flex;
    align-items: center;
    gap: 6px;

    .user-info {
      display: flex;
      align-items: center;
      gap: 9px;
      cursor: pointer;
      padding: 4px 10px 4px 4px;
      border-radius: var(--radius-pill);
      margin-left: 4px;
      color: var(--text-color-tertiary);
      transition: background 0.16s;

      &:hover {
        background: var(--fill-hover);
      }

      .avatar {
        width: 30px;
        height: 30px;
        border-radius: 50%;
        background: linear-gradient(135deg, #a2c9ff, #7aa7f5);
        color: #fff;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        font-size: 12px;
        font-weight: 600;
      }

      .username {
        color: var(--text-color);
        font-size: 14px;
        font-weight: 500;
        white-space: nowrap;
      }
    }
  }
}
</style>
