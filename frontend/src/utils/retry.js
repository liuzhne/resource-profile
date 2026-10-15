// Replay only reads. A timeout or HTTP 429 never proves that a write was not applied.
export const DEFAULT_TIMEOUT = 15000
export const DEFAULT_BUDGET = 30000
export const MAX_RETRY = 2
export const RETRY_DELAYS = [250, 750]
const READ_METHODS = new Set(['get', 'head', 'options'])
const STANDARD = Object.freeze({
  timeout: DEFAULT_TIMEOUT,
  budget: DEFAULT_BUDGET,
  maxRetry: MAX_RETRY,
  delays: RETRY_DELAYS
})
const RENDER = Object.freeze({
  timeout: 60000,
  budget: 240000,
  maxRetry: 6,
  delays: [5000, 15000, 30000, 45000, 60000, 60000]
})
const positive = (value) =>
  typeof value === 'number' && Number.isFinite(value) && value > 0 && value <= 2147483647

export const resolveRequestProfile = (env = {}) =>
  env.VITE_REQUEST_PROFILE === 'render' ? RENDER : STANDARD
export const serviceWarmupEnabled = (env = {}) =>
  env.VITE_REQUEST_PROFILE === 'render' && env.VITE_ENABLE_SERVICE_WARMUP === 'true'

export const loginRequestOptions = (env = {}) => ({
  timeout: env.VITE_REQUEST_PROFILE === 'render' ? 180000 : DEFAULT_TIMEOUT,
  requestBudgetMs: env.VITE_REQUEST_PROFILE === 'render' ? 240000 : DEFAULT_BUDGET,
  retry: false
})

export const isReadRequest = (config) =>
  config?.method == null ||
  (typeof config.method === 'string' && READ_METHODS.has(config.method.toLowerCase()))

export const isCanceledRequest = (error) =>
  error?.code === 'ERR_CANCELED' || error?.name === 'AbortError' || error?.__CANCEL__ === true

export const isTransientFailure = (error) => {
  if (isCanceledRequest(error) || error?.code === 'ERR_REQUEST_BUDGET') return false
  if (error?.response) return [429, 502, 503, 504].includes(error.response.status)
  return ['ECONNABORTED', 'ETIMEDOUT', 'ERR_NETWORK'].includes(error?.code)
}

export const resolveRequestPolicy = (config, profile = STANDARD) => {
  const binary = ['blob', 'arraybuffer'].includes(config.responseType)
  const suppliedTimeout = positive(config.timeout) ? config.timeout : profile.timeout
  // Blob APIs currently specify no timeout; preserve a full minute for actual PDF/CSV downloads.
  const timeout = binary && suppliedTimeout === profile.timeout ? 60000 : suppliedTimeout
  const longOperation = binary || timeout > profile.timeout
  const budget = positive(config.requestBudgetMs)
    ? config.requestBudgetMs
    : binary
      ? Math.max(90000, timeout)
      : Math.max(profile.budget, timeout)
  return {
    timeout,
    budget,
    delays: profile.delays,
    maxRetry:
      config.retry === false || !isReadRequest(config) || (longOperation && config.retry !== true)
        ? 0
        : profile.maxRetry
  }
}

export const shouldRetry = (error, config, policy = resolveRequestPolicy(config || {})) =>
  !!config &&
  config.retry !== false &&
  isReadRequest(config) &&
  (config.__retryCount || 0) < policy.maxRetry &&
  !config.signal?.aborted &&
  isTransientFailure(error)

export const retryDelay = (attempt, policy = STANDARD) =>
  policy.delays[attempt - 1] ?? policy.delays[policy.delays.length - 1]

export const retryAfterDelay = (error, attempt, policy, wallNow = Date.now()) => {
  const headers = error?.response?.headers
  const raw = headers?.get?.('Retry-After') ?? headers?.['retry-after']
  let requested = 0
  if (typeof raw === 'string' && /^\d+(?:\.\d+)?$/.test(raw.trim())) requested = Number(raw) * 1000
  else if (typeof raw === 'string') {
    const date = Date.parse(raw)
    if (Number.isFinite(date)) requested = Math.max(0, date - wallNow)
  }
  return Math.max(retryDelay(attempt, policy), requested)
}

const policyError = (code) => {
  const error = new Error(code === 'ERR_CANCELED' ? '请求已取消' : '请求超过总时间预算')
  error.code = code
  if (code === 'ERR_CANCELED') {
    error.name = 'CanceledError'
    error.__CANCEL__ = true
  }
  return error
}

// A class retains identity across Axios config merges (plain objects are recursively copied).
export class RequestBudget {
  constructor(policy, signal, { now, setTimer, clearTimer }) {
    this.policy = policy
    this.now = now
    // Browser timers require their native receiver; never bind them to this budget.
    this.setTimer = (...args) => setTimer(...args)
    this.clearTimer = (...args) => clearTimer(...args)
    this.deadline = now() + policy.budget
    this.controller = new AbortController()
    this.originalSignal = signal
    this.closed = false
    this.reason = null
    this.onCancel = () => this.abort('ERR_CANCELED')
    if (signal?.aborted) this.onCancel()
    else signal?.addEventListener('abort', this.onCancel, { once: true })
    if (!this.reason) this.timer = setTimer(() => this.abort('ERR_REQUEST_BUDGET'), policy.budget)
  }
  get signal() {
    return this.controller.signal
  }
  remaining() {
    return Math.max(0, this.deadline - this.now())
  }
  abort(reason) {
    if (this.closed || this.reason) return
    this.reason = reason
    this.controller.abort()
  }
  failure() {
    if (!this.closed && !this.reason && this.remaining() <= 0) this.abort('ERR_REQUEST_BUDGET')
    return this.reason ? policyError(this.reason) : null
  }
  waitFor(promise) {
    return new Promise((resolve, reject) => {
      const fail = this.failure()
      const onAbort = () => settle(reject, this.failure())
      const settle = (fn, value) => {
        this.signal.removeEventListener('abort', onAbort)
        fn(value)
      }
      // Observe the underlying shared promise even when this request has already canceled.
      Promise.resolve(promise).then(
        (value) => settle(resolve, value),
        (error) => settle(reject, error)
      )
      if (fail) reject(fail)
      else this.signal.addEventListener('abort', onAbort, { once: true })
    })
  }
  async delay(milliseconds) {
    let timer
    try {
      await this.waitFor(
        new Promise((resolve) => {
          timer = this.setTimer(resolve, milliseconds)
        })
      )
    } finally {
      this.clearTimer(timer)
    }
  }
  finish() {
    if (this.closed) return
    this.closed = true
    this.clearTimer(this.timer)
    this.originalSignal?.removeEventListener('abort', this.onCancel)
  }
}
