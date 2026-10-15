// Agent timestamps represent UTC instants. Legacy Agent APIs omitted the Z suffix;
// this compatibility rule is limited to Agent records, never general campus dates.
const UTC_LOCAL = /^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?$/
const OFFSET_TIME = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/
const formatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23'
})

export const formatCampusInstant = (value, legacyAgentUtc = false) => {
  if (typeof value !== 'string') return ''
  const instant = legacyAgentUtc && UTC_LOCAL.test(value) ? `${value.replace(' ', 'T')}Z` : value
  if (!OFFSET_TIME.test(instant)) return ''
  const date = new Date(instant)
  if (!Number.isFinite(date.getTime())) return ''
  const parts = Object.fromEntries(
    formatter.formatToParts(date).map((part) => [part.type, part.value])
  )
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute}:${parts.second}`
}

export const formatAgentTime = (value) => formatCampusInstant(value, true)
