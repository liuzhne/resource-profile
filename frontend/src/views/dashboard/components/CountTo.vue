<template>
  <span>{{ displayValue }}</span>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'

interface Props {
  start?: number
  end: number
  duration?: number
  decimals?: number
}

const props = withDefaults(defineProps<Props>(), {
  start: 0,
  duration: 2000,
  decimals: 0
})

const displayValue = ref(props.start)
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
