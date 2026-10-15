export const todoStatusLabel = (status) =>
  ({
    OPEN: '待处理',
    IN_PROGRESS: '处理中',
    FAILED: '失败待检查',
    REJECTED: '待人工复核'
  })[status] ||
  status ||
  '—'

export const formatEventTime = (value) => (value ? String(value).replace('T', ' ') : '—')

export const todoTarget = (item, aiEnabled) => {
  if (item.kind === 'MENTAL_WARNING') return '/mental/overview'
  if (item.kind === 'AGENT_EXCEPTION' && aiEnabled) return '/agent/warning'
  return item.studentId ? `/student/detail/${encodeURIComponent(item.studentId)}` : '/dashboard'
}
