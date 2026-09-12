<template>
  <svg viewBox="0 0 32 32" :width="size" :height="size" aria-hidden="true">
    <circle cx="16" cy="16" r="3.4" fill="currentColor" />
    <path
      d="M16 8A8 8 0 0 1 16 24"
      fill="none"
      stroke="currentColor"
      stroke-width="2.6"
      stroke-linecap="round"
    />
    <path
      d="M16 29A13 13 0 0 1 16 3"
      fill="none"
      stroke="currentColor"
      stroke-width="2.2"
      stroke-linecap="round"
      opacity="0.65"
    />
  </svg>
</template>

<script setup lang="ts">
// 「见微」品牌标识：同心圆，由外向内收束 —— 侧边栏与登录页共用
withDefaults(defineProps<{ size?: number }>(), { size: 21 })
</script>

<style scoped>
svg {
  display: block;
}
</style>
