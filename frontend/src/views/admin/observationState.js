import { computed, getCurrentScope, onScopeDispose, ref } from 'vue'
import { getObservations } from '@/api/observability'
import { formatCampusInstant } from '@/views/agent/agentTime'
export { failureCodeLabel, failureStageLabel, failureHttpLabel } from '@/views/agent/failureState'

const DAY_MS = 24 * 60 * 60 * 1000
const RANGE_DAYS = [1, 7, 30]
const PAGE_SIZES = [20, 50]
const FIELDS = [
  'id',
  'traceId',
  'name',
  'type',
  'startTime',
  'endTime',
  'model',
  'inputTokens',
  'outputTokens',
  'totalTokens',
  'latencyMs',
  'level',
  'agentStatus',
  'iterations',
  'errorCode',
  'errorStage',
  'httpStatus'
]
const NUMBER_FIELDS = [
  'inputTokens',
  'outputTokens',
  'totalTokens',
  'latencyMs',
  'iterations',
  'httpStatus'
]

export const formatObservationTime = (value) => formatCampusInstant(value) || '未记录'

const knownNumber = (value) => {
  if (
    (typeof value !== 'number' && typeof value !== 'string') ||
    (typeof value === 'string' && !value.trim())
  )
    return null
  const number = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(number) && number >= 0 ? number : null
}
export const formatObservationTokens = (value) => {
  const number = knownNumber(value)
  return number == null ? '未记录' : number.toLocaleString('zh-CN')
}
export const formatObservationLatency = (value) => {
  const number = knownNumber(value)
  if (number == null) return '未记录'
  return `${number.toLocaleString('zh-CN', { maximumFractionDigits: 2 })} ms`
}

const LEVEL_LABELS = {
  DEBUG: '调试级别',
  DEFAULT: '默认级别',
  WARNING: '警告级别',
  ERROR: '错误级别'
}
export const observationLevelLabel = (value) => LEVEL_LABELS[value] || '未记录'
export const observationLevelType = (value) =>
  ({ WARNING: 'warning', ERROR: 'danger', DEFAULT: 'info', DEBUG: 'info' })[value] || 'info'

const AGENT_STATUS_LABELS = {
  COMPLETED: '循环完成',
  MAX_ITERATIONS: '超出轮次',
  TOOL_ERROR: '工具失败',
  LLM_ERROR: '模型请求失败',
  PARSE_ERROR: '输出解析失败',
  VALIDATION_ERROR: '输出校验失败',
  DEADLINE_EXCEEDED: '执行超时'
}
export const observationAgentStatusLabel = (value) => AGENT_STATUS_LABELS[value] || '未记录'
export const observationAgentStatusType = (value) =>
  value === 'COMPLETED' ? 'success' : AGENT_STATUS_LABELS[value] ? 'danger' : 'info'
const safeTraceId = (value) =>
  typeof value === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(value) ? value : ''

const ERROR_MESSAGES = {
  400: '查询条件无效，请刷新后重试。',
  403: '仅管理员可查看追踪数据。',
  429: '追踪服务请求过于频繁，请稍后重试。',
  502: '追踪服务暂时不可用，请稍后重试。',
  503: '追踪服务尚未启用或配置不可用，请联系管理员。',
  504: '读取追踪数据超时，请稍后重试。'
}
const observationErrorMessage = (error) => {
  const code = Number(error?.code)
  const status = Number(error?.response?.status)
  if (ERROR_MESSAGES[code]) return ERROR_MESSAGES[code]
  if (ERROR_MESSAGES[status]) return ERROR_MESSAGES[status]
  if (error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT') return ERROR_MESSAGES[504]
  return '追踪数据暂时无法加载，请稍后重试。'
}

export const useObservations = (
  load = getObservations,
  now = () => new Date(),
  initialTraceId = ''
) => {
  const rangeDays = ref(1)
  const pageSize = ref(20)
  const from = ref('')
  const to = ref('')
  const records = ref([])
  const loading = ref(false)
  const loadError = ref(false)
  const errorMessage = ref('')
  const pageIndex = ref(0)
  const hasMore = ref(false)
  const nextCursor = ref('')
  const cursors = ref([''])
  const traceId = ref(safeTraceId(initialTraceId))
  let requestSequence = 0
  let disposed = false
  const currentPage = computed(() => pageIndex.value + 1)
  const canPrevious = computed(() => !loading.value && pageIndex.value > 0)
  const canNext = computed(() => !loading.value && !loadError.value && hasMore.value)

  const fetchPage = async () => {
    if (disposed) return
    const sequence = ++requestSequence
    const requestFrom = from.value
    const requestTo = to.value
    const params = { from: requestFrom, to: requestTo, limit: pageSize.value }
    if (traceId.value) params.traceId = traceId.value
    const cursor = cursors.value[pageIndex.value]
    if (cursor) params.cursor = cursor
    loading.value = true
    loadError.value = false
    errorMessage.value = ''
    records.value = []
    hasMore.value = false
    nextCursor.value = ''
    try {
      const response = await load(params)
      if (disposed || sequence !== requestSequence) return
      const page = response?.data
      if (
        !Array.isArray(page?.records) ||
        page.records.length > params.limit ||
        typeof page.hasMore !== 'boolean' ||
        new Date(page.from).getTime() !== new Date(requestFrom).getTime() ||
        new Date(page.to).getTime() !== new Date(requestTo).getTime() ||
        (page.hasMore && (typeof page.nextCursor !== 'string' || !page.nextCursor)) ||
        !page.records.every(
          (record) =>
            record &&
            typeof record === 'object' &&
            !Array.isArray(record) &&
            typeof record.id === 'string' &&
            record.id.length > 0
        ) ||
        new Set(page.records.map((record) => record.id)).size !== page.records.length
      )
        throw new Error('Invalid observation page')
      // Only the documented operational DTO is retained; prompt/output/arbitrary metadata are discarded.
      records.value = page.records.map((record) =>
        Object.fromEntries(
          FIELDS.map((field) => [
            field,
            NUMBER_FIELDS.includes(field)
              ? knownNumber(record[field])
              : typeof record[field] === 'string'
                ? record[field]
                : null
          ])
        )
      )
      hasMore.value = page.hasMore
      nextCursor.value = page.hasMore ? page.nextCursor : ''
    } catch (error) {
      if (disposed || sequence !== requestSequence) return
      loadError.value = true
      errorMessage.value = observationErrorMessage(error)
    } finally {
      if (!disposed && sequence === requestSequence) loading.value = false
    }
  }

  const refresh = () => {
    if (disposed) return
    const end = now()
    to.value = end.toISOString()
    from.value = new Date(end.getTime() - rangeDays.value * DAY_MS).toISOString()
    pageIndex.value = 0
    cursors.value = ['']
    return fetchPage()
  }
  const changeRange = (days) => {
    const value = Number(days)
    if (!RANGE_DAYS.includes(value) || value === rangeDays.value) return
    rangeDays.value = value
    return refresh()
  }
  const changeSize = (size) => {
    const value = Number(size)
    if (!PAGE_SIZES.includes(value) || value === pageSize.value) return
    pageSize.value = value
    return refresh()
  }
  const changeTraceId = (value) => {
    const next = safeTraceId(value)
    if (next === traceId.value) return
    traceId.value = next
    return refresh()
  }
  const nextPage = () => {
    if (!canNext.value) return
    cursors.value = [...cursors.value.slice(0, pageIndex.value + 1), nextCursor.value]
    pageIndex.value++
    return fetchPage()
  }
  const previousPage = () => {
    if (!canPrevious.value) return
    pageIndex.value--
    return fetchPage()
  }
  const dispose = () => {
    disposed = true
    requestSequence++
  }
  if (getCurrentScope()) onScopeDispose(dispose)

  return {
    rangeDays,
    pageSize,
    from,
    to,
    traceId,
    records,
    loading,
    loadError,
    errorMessage,
    currentPage,
    hasMore,
    canPrevious,
    canNext,
    refresh,
    changeRange,
    changeSize,
    changeTraceId,
    nextPage,
    previousPage,
    retry: fetchPage
  }
}
