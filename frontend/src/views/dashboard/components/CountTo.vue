<template>
  <span>{{ displayValue }}</span>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'

const props = defineProps({
  start: {
    type: Number,
    default: 0
  },
  end: {
    type: Number,
    required: true
  },
  duration: {
    type: Number,
    default: 2000
  },
  decimals: {
    type: Number,
    default: 0
  }
})

const displayValue = ref(props.start)
let rafId = null

const startAnimate = (from, to) => {
  if (rafId !== null) {
    cancelAnimationFrame(rafId)
  }

  const startTime = performance.now()
  const diff = to - from

  const animate = (currentTime) => {
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
