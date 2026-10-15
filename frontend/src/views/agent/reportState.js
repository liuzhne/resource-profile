import { ref, watch, onScopeDispose } from 'vue'
import { getAgentTaskDetail } from '@/api/agent'

export const parseReportObject = (value) => {
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

export const hasPlanContent = (plan) =>
  !!(
    plan.immediate_actions?.some((a) => a.action?.trim()) ||
    plan.long_term_plan?.some((p) => p.goal?.trim() || p.milestones?.length)
  )

// The identity is checked after every request so a slow old-tab response cannot replace this report.
export function useAgentReport(taskId, getDetail = getAgentTaskDetail) {
  const task = ref({})
  const loading = ref(true)
  const loadError = ref(false)
  let sequence = 0
  const fetchDetail = async () => {
    const id = String(taskId.value)
    const request = ++sequence
    loading.value = true
    loadError.value = false
    try {
      const response = await getDetail(id)
      if (request !== sequence || id !== String(taskId.value)) return
      if (!response.data || String(response.data.id) !== id) throw new Error('报告身份不匹配')
      task.value = response.data
    } catch {
      if (request !== sequence || id !== String(taskId.value)) return
      task.value = {}
      loadError.value = true
    } finally {
      if (request === sequence && id === String(taskId.value)) loading.value = false
    }
  }
  const stop = watch(
    taskId,
    () => {
      task.value = {}
      void fetchDetail()
    },
    { immediate: true }
  )
  onScopeDispose(() => {
    ++sequence
    stop()
  })
  return { task, loading, loadError, fetchDetail }
}
