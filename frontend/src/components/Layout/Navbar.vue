<template>
  <div class="navbar">
    <!-- 左侧：折叠按钮和面包屑 -->
    <div class="left">
      <div class="collapse-btn" @click="toggleSidebar">
        <el-icon :size="18">
          <Fold v-if="!sidebarCollapsed" />
          <Expand v-else />
        </el-icon>
      </div>
      <Breadcrumb />
    </div>

    <!-- 右侧：用户相关 -->
    <div class="right">
      <div class="workbench-meta">
        <span>综合管理工作台</span>
        <span class="dot"></span>
        <span>校级数据视图</span>
      </div>
      <el-dropdown trigger="click" @command="handleUserCommand">
        <div class="user-info">
          <el-avatar :size="32" :src="userInfo?.avatar">
            <el-icon><User /></el-icon>
          </el-avatar>
          <span class="username">{{ userInfo?.nickname || userInfo?.username }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon> 个人中心
            </el-dropdown-item>
            <el-dropdown-item command="settings">
              <el-icon><Setting /></el-icon> 系统设置
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><SwitchButton /></el-icon> 退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import Breadcrumb from './Breadcrumb.vue'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const userInfo = computed(() => userStore.userInfo)

const toggleSidebar = () => {
  appStore.toggleSidebar()
}

const goToProfile = () => {
  router.push('/profile')
}

const goToSettings = () => {
  // 可以跳转到系统设置页面
}

const handleUserCommand = (command) => {
  if (command === 'profile') {
    goToProfile()
    return
  }
  if (command === 'settings') {
    goToSettings()
    return
  }
  if (command === 'logout') {
    handleLogout()
  }
}

const handleLogout = () => {
  ElMessageBox.confirm('退出后需要重新登录平台账号。', '确认退出登录？', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
    customClass: 'app-confirm-dialog',
    distinguishCancelAndClose: true,
    showClose: false
  }).then(() => {
    userStore.logout()
  })
}
</script>

<style scoped lang="scss">
.navbar {
  height: var(--header-height);
  background: #fff;
  border-bottom: 1px solid var(--border-color);
  box-shadow: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;

  .left {
    display: flex;
    align-items: center;
    min-width: 0;

    .collapse-btn {
      width: 32px;
      height: 32px;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      border-radius: var(--radius-small);
      transition: background 0.3s;

      &:hover {
        background: var(--bg-color-subtle);
      }
    }
  }

  .right {
    display: flex;
    align-items: center;
    gap: 18px;

    .workbench-meta {
      display: flex;
      align-items: center;
      gap: 8px;
      color: var(--text-color-secondary);
      font-size: 13px;
      white-space: nowrap;

      .dot {
        width: 4px;
        height: 4px;
        border-radius: 50%;
        background: var(--border-color-strong);
      }
    }

    .user-info {
      display: flex;
      align-items: center;
      cursor: pointer;
      padding: 4px 8px;
      border-radius: var(--radius-small);
      transition: background 0.3s;

      &:hover {
        background: var(--bg-color-subtle);
      }

      .username {
        margin: 0 8px;
        color: var(--text-color);
      }
    }
  }
}

@media (max-width: 900px) {
  .navbar {
    padding: 0 14px;

    .right .workbench-meta {
      display: none;
    }
  }
}
</style>
