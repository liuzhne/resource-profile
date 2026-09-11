<template>
  <div class="sidebar" :class="{ collapsed: sidebarCollapsed }">
    <!-- Logo -->
    <div class="logo">
      <div class="logo-mark">教</div>
      <div v-show="!sidebarCollapsed" class="brand-copy">
        <span class="title">师生资源画像</span>
        <span class="subtitle">Resource Profile</span>
      </div>
    </div>

    <!-- 菜单 -->
    <el-scrollbar class="menu-scrollbar">
      <el-menu
        :default-active="activeMenu"
        :collapse="sidebarCollapsed"
        :collapse-transition="false"
        router
        background-color="#001529"
        text-color="rgba(255, 255, 255, 0.65)"
        active-text-color="#fff"
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

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/store/modules/app'
import { useUserStore } from '@/store/modules/user'
import SidebarItem from './SidebarItem.vue'
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
  background: #10233f;
  transition: width 0.3s;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;

  &.collapsed {
    width: var(--sidebar-collapsed-width);
  }

  .logo {
    height: var(--header-height);
    display: flex;
    align-items: center;
    justify-content: flex-start;
    gap: 10px;
    padding: 0 18px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);

    .logo-mark {
      width: 32px;
      height: 32px;
      border: 1px solid rgba(255, 255, 255, 0.24);
      border-radius: 8px;
      background: rgba(31, 95, 191, 0.55);
      color: #fff;
      font-size: 17px;
      font-weight: 700;
      line-height: 30px;
      text-align: center;
      flex-shrink: 0;
    }

    .brand-copy {
      display: flex;
      flex-direction: column;
      min-width: 0;
    }

    .title {
      color: #fff;
      font-size: 15px;
      font-weight: 650;
      line-height: 1.25;
      white-space: nowrap;
    }

    .subtitle {
      color: rgba(255, 255, 255, 0.48);
      font-size: 11px;
      line-height: 1.3;
      white-space: nowrap;
    }
  }

  .menu-scrollbar {
    flex: 1;

    :deep(.el-scrollbar__wrap) {
      overflow-x: hidden;
    }
  }

  :deep(.el-menu) {
    border-right: none;
  }
}
</style>
