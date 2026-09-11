<template>
  <!-- 加载中 -->
  <div v-if="loading" v-loading="true" class="state-box" :style="{ height }"></div>

  <!-- 请求失败：给出重试入口，不静默吞掉错误 -->
  <div v-else-if="error" class="state-box" :style="{ height }">
    <el-icon :size="28" class="state-icon"><WarningFilled /></el-icon>
    <p class="state-text">{{ errorText }}</p>
    <el-button text type="primary" size="small" @click="emit('retry')">重新加载</el-button>
  </div>

  <!-- 空数据 -->
  <el-empty v-else-if="empty" :description="emptyText" :image-size="72" :style="{ height }" />

  <slot v-else />
</template>

<script setup lang="ts">
interface Props {
  loading?: boolean
  error?: boolean
  empty?: boolean
  height?: string
  errorText?: string
  emptyText?: string
}

withDefaults(defineProps<Props>(), {
  loading: false,
  error: false,
  empty: false,
  height: '260px',
  errorText: '数据加载失败',
  emptyText: '暂无数据'
})

const emit = defineEmits<{ retry: [] }>()
</script>

<style scoped lang="scss">
.state-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;

  .state-icon {
    color: var(--text-color-disabled);
  }

  .state-text {
    font-size: 13px;
    color: var(--text-color-tertiary);
  }
}

:deep(.el-empty) {
  display: flex;
  justify-content: center;
  padding: 0;
}
</style>
