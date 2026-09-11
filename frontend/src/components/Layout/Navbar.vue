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
      <!-- 全局搜索与通知：原型有版式，后端暂无对应接口，先占位 -->
      <div class="icon-btn" title="全局搜索" @click="notReady">
        <el-icon :size="17"><Search /></el-icon>
      </div>
      <div class="icon-btn" title="通知" @click="notReady">
        <el-icon :size="17"><Bell /></el-icon>
      </div>

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
            <el-dropdown-item divided @click="handleLogout">
              <el-icon><SwitchButton /></el-icon> 退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import Breadcrumb from './Breadcrumb.vue'

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const userInfo = computed(() => userStore.userInfo)

const displayName = computed(() => userInfo.value?.nickname || userInfo.value?.username || '')
const avatarText = computed(() => displayName.value.charAt(0) || '?')

const toggleSidebar = () => {
  appStore.toggleSidebar()
}

const notReady = () => {
  ElMessage.info('该功能暂未开放')
}

const goToProfile = () => {
  router.push('/profile')
}

const goToSettings = () => {
  // 可以跳转到系统设置页面
}

const handleLogout = () => {
  ElMessageBox.confirm('确定要退出登录吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    userStore.logout()
  })
}
</script>

<style scoped lang="scss">
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
