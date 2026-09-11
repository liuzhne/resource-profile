<template>
  <span>{{ formattedValue }}</span>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'

interface Props {
  start?: number
  end: number
  duration?: number
  decimals?: number
  /** 千分位分隔，对齐原型的 1,286 / 18,432 */
  separator?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  start: 0,
  duration: 2000,
  decimals: 0,
  separator: true
})

const displayValue = ref(props.start)

const formattedValue = computed(() =>
  props.separator
    ? displayValue.value.toLocaleString('en-US', {
        minimumFractionDigits: props.decimals,
        maximumFractionDigits: props.decimals
      })
    : displayValue.value
)
let rafId: number | null = null

const startAnimate = (from: number, to: number) => {
  if (rafId !== null) {
    cancelAnimationFrame(rafId)
  }

  const startTime = performance.now()
  const diff = to - from

  const animate = (currentTime: number) => {
    const elapsed = currentTime - startTime
    const progress = Math.min(elapsed / props.duration, 1)

    // 使用 easeOutQuart 缓动函数
    const easeProgress = 1 - Math.pow(1 - progress, 4)
    const currentValue = from + diff * easeProgress

    displayValue.value = Number(currentValue.toFixed(props.decimals))

    if (progress < 1) {
      rafId = requestAnimationFrame(animate)
    }
  }

  rafId = requestAnimationFrame(animate)
}

onMounted(() => {
  startAnimate(props.start, props.end)
})

watch(
  () => props.end,
  (newEnd) => {
    startAnimate(displayValue.value, newEnd)
  }
)

onUnmounted(() => {
  if (rafId !== null) {
    cancelAnimationFrame(rafId)
  }
})
</script>
