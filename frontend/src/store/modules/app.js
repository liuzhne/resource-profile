import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const isMobile = ref(false)

  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  const setSidebarCollapsed = (collapsed) => {
    sidebarCollapsed.value = collapsed
  }

  const setIsMobile = (mobile) => {
    isMobile.value = mobile
  }

  return {
    sidebarCollapsed,
    isMobile,
    toggleSidebar,
    setSidebarCollapsed,
    setIsMobile
  }
})
