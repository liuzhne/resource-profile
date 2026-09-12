<template>
  <div class="sidebar" :class="{ collapsed: sidebarCollapsed }">
    <!-- Logo -->
    <div class="logo">
      <span class="logo-mark">
        <BrandLogo :size="19" />
      </span>
      <span v-show="!sidebarCollapsed" class="logo-text">
        <span class="title">见微</span>
        <span class="subtitle">师生资源画像平台</span>
      </span>
    </div>

    <!-- 菜单 -->
    <el-scrollbar class="menu-scrollbar">
      <el-menu
        :default-active="activeMenu"
        :collapse="sidebarCollapsed"
        :collapse-transition="false"
        router
      >
        <SidebarItem
          v-for="menuRoute in menuRoutes"
          :key="menuRoute.path"
          :item="menuRoute"
          :base-path="menuRoute.path"
        />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import SidebarItem from './SidebarItem.vue'
import BrandLogo from '@/components/BrandLogo.vue'
import { routes } from '@/router'

const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

const sidebarCollapsed = computed(() => appStore.sidebarCollapsed)
const activeMenu = computed(() => route.path)

// userType: 0-管理员 1-教师 2-学生
const currentRole = computed(() => {
  const t = userStore.userInfo?.userType
  if (t === 0) return 'admin'
  if (t === 1) return 'teacher'
  if (t === 2) return 'student'
  return null
})

const matchRole = (meta) => {
  if (!meta?.roles || meta.roles.length === 0) return true
  return currentRole.value && meta.roles.includes(currentRole.value)
}

// 提取 Layout 路由下的 children 作为菜单数据源
const menuRoutes = computed(() => {
  const layoutRoute = routes.find((r) => r.path === '/')
  return (layoutRoute?.children || []).filter((r) => !r.meta?.hidden && matchRole(r.meta))
})
</script>

<style scoped lang="scss">
.sidebar {
  width: var(--sidebar-width);
  background: var(--glass-bg-thin);
  backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  -webkit-backdrop-filter: blur(var(--glass-blur)) saturate(var(--glass-saturate));
  border-right: 1px solid var(--border-color);
  transition: width 0.3s;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  @supports not ((backdrop-filter: blur(1px)) or (-webkit-backdrop-filter: blur(1px))) {
    background: rgba(255, 255, 255, 0.96);
  }

  &.collapsed {
    width: var(--sidebar-collapsed-width);

    .logo {
      justify-content: center;
      padding: 0;
    }
  }

  .logo {
    height: 64px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 0 18px;

    .logo-mark {
      width: 32px;
      height: 32px;
      border-radius: var(--radius-md);
      background: var(--primary-color);
      color: #fff;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      box-shadow: 0 5px 14px -7px rgba(0, 122, 255, 0.6);

      svg {
        display: block;
      }
    }

    .logo-text {
      display: flex;
      flex-direction: column;
      gap: 1px;
      min-width: 0;

      .title {
        font-size: 16px;
        font-weight: 600;
        letter-spacing: 0.06em;
        white-space: nowrap;
        color: var(--text-color);
      }

      .subtitle {
        font-size: 12px;
        letter-spacing: 0.04em;
        white-space: nowrap;
        color: rgba(60, 60, 67, 0.82);
      }
    }
  }

  .menu-scrollbar {
    flex: 1;

    :deep(.el-scrollbar__view) {
      padding: 6px 10px 16px;
    }

    :deep(.el-scrollbar__wrap) {
      overflow-x: hidden;
    }
  }
}
</style>
