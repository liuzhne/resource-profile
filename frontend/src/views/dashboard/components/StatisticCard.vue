<template>
  <el-card class="statistic-card" shadow="hover">
    <div class="card-content">
      <div class="icon-wrapper" :style="{ backgroundColor: color + '15' }">
        <el-icon :size="32" :style="{ color: color }">
          <component :is="icon" />
        </el-icon>
      </div>
      <div class="statistic-info">
        <div class="title">{{ title }}</div>
        <div class="value" :style="{ color: color }">
          <CountTo :end="value" :duration="2000" />
          <span class="suffix">{{ suffix }}</span>
        </div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import CountTo from './CountTo.vue'

defineProps({
  title: {
    type: String,
    required: true
  },
  value: {
    type: Number,
    required: true
  },
  icon: {
    type: String,
    required: true
  },
  color: {
    type: String,
    required: true
  },
  suffix: {
    type: String,
    default: ''
  }
})
</script>

<style scoped lang="scss">
.statistic-card {
  height: 100%;

  .card-content {
    display: flex;
    align-items: center;
    min-height: 78px;

    .icon-wrapper {
      width: 46px;
      height: 46px;
      border-radius: 6px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 14px;
    }

    .statistic-info {
      flex: 1;

      .title {
        font-size: 13px;
        color: var(--text-color-secondary);
        margin-bottom: 8px;
      }

      .value {
        font-size: 26px;
        font-weight: 700;
        line-height: 1;

        .suffix {
          font-size: 14px;
          margin-left: 4px;
        }
      }
    }
  }
}
</style>
