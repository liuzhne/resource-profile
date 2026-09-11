<template>
  <div class="glass-card statistic-card">
    <div class="card-head">
      <span class="title">{{ title }}</span>
      <span class="icon-chip" :style="{ backgroundColor: tint, color: color }">
        <el-icon :size="17">
          <component :is="icon" />
        </el-icon>
      </span>
    </div>

    <div class="value-row">
      <span class="value tnum">
        <CountTo :end="value" :duration="1600" />
      </span>
      <span v-if="suffix" class="suffix">{{ suffix }}</span>
    </div>

    <!-- 环比：后端 /data/dashboard/statistics 暂无该字段，有值才渲染 -->
    <div v-if="delta" class="delta-row">
      <span class="delta tnum" :style="{ color: deltaColor }">{{ delta }}</span>
      <span class="delta-label">较上月</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import CountTo from './CountTo.vue'

interface Props {
  title: string
  value: number
  icon: string
  /** 图标前景色，用 -deep 变体保证浅底对比度 */
  color: string
  /** 图标胶囊底色 */
  tint: string
  suffix?: string
  delta?: string
  deltaColor?: string
}

withDefaults(defineProps<Props>(), {
  suffix: '',
  delta: '',
  deltaColor: 'var(--success-deep)'
})
</script>

<style scoped lang="scss">
.statistic-card {
  padding: 18px;

  .card-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 14px;

    .title {
      font-size: 13px;
      color: var(--text-color-secondary);
    }
  }

  .value-row {
    display: flex;
    align-items: baseline;
    gap: 4px;

    // 原型里数值统一为深色，颜色信息只由图标胶囊承载
    .value {
      font-size: 34px;
      font-weight: 600;
      line-height: 1;
      color: var(--text-color);
    }

    .suffix {
      font-size: 13px;
      color: rgba(60, 60, 67, 0.65);
    }
  }

  .delta-row {
    margin-top: 10px;
    display: flex;
    align-items: center;
    gap: 5px;
    font-size: 12px;

    .delta {
      font-weight: 600;
    }

    .delta-label {
      color: rgba(60, 60, 67, 0.65);
    }
  }
}
</style>
