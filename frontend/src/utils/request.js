import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import {
  RequestBudget,
  resolveRequestProfile,
  resolveRequestPolicy,
  shouldRetry,
  retryAfterDelay,
  isTransientFailure,
  isCanceledRequest,
  serviceWarmupEnabled
} from './retry'
import { wakeServicesNow } from './warmup'

const monotonicNow = () => globalThis.performance?.now?.() ?? Date.now()

// The optional dependencies exercise the real Axios interceptor chain with an offline adapter.
export const createRequestClient = ({
  env = import.meta.env,
  // user -> auth -> request -> user is a real module cycle; resolve Pinia only per request.
  getUserStore = () => useUserStore(),
  message = ElMessage,
  wake = wakeServicesNow,
  now = monotonicNow,
  wallNow = Date.now,
  setTimer = setTimeout,
  clearTimer = clearTimeout
} = {}) => {
  const profile = resolveRequestProfile(env)
  const request = axios.create({
    baseURL: env.VITE_API_BASE_URL || '/api',
    timeout: profile.timeout,
    headers: { 'Content-Type': 'application/json' }
  })
  const clearUnauthorizedSession = (config) => {
    const userStore = getUserStore()
    const authorization = config?.headers?.get?.('Authorization') || config?.headers?.Authorization
    // A late 401 belongs only to the session that sent this request.
    if (userStore.token && authorization === `Bearer ${userStore.token}`) userStore.clearSession()
  }
  const fail = (error, config) => {
    const budget = config?.__requestBudget
    const finalError = budget?.failure() || error
    finalError.config ||= config
    budget?.finish()
    if (error.response?.status === 401) clearUnauthorizedSession(config)
    if (isCanceledRequest(finalError) || finalError.__requestNoticeShown)
      return Promise.reject(finalError)
    finalError.__requestNoticeShown = true
    if (error.response?.status === 403)
      message.warning(error.response?.data?.message || '无权限访问该资源')
    else if (finalError.code === 'ERR_REQUEST_BUDGET')
      message.error('请求等待已超过时间上限，请稍后重试')
    else if (['ECONNABORTED', 'ETIMEDOUT'].includes(finalError.code))
      message.error('请求响应超时，请稍后重试')
    else if (error.response?.status === 429) message.error('请求频率受限，请稍后重试')
    else if (isTransientFailure(finalError)) message.error('连接暂时不可用，请稍后重试')
    else message.error(error.response?.data?.message || error.message || '请求失败')
    return Promise.reject(finalError)
  }

  request.interceptors.request.use(async (config) => {
    let budget = config.__requestBudget
    if (!(budget instanceof RequestBudget) || budget.closed) {
      budget = new RequestBudget(resolveRequestPolicy(config, profile), config.signal, {
        now,
        setTimer,
        clearTimer
      })
      config.__requestBudget = budget
      config.__retryCount = 0
    }
    config.signal = budget.signal
    try {
      const initialFailure = budget.failure()
      if (initialFailure) throw initialFailure
      const userStore = getUserStore()
      if (!config.url?.startsWith('/auth/') && !config.headers.Authorization)
        await budget.waitFor(userStore.ensureSession())
      const preparationFailure = budget.failure()
      if (preparationFailure) throw preparationFailure
      if (userStore.token && !config.headers.Authorization)
        config.headers.Authorization = `Bearer ${userStore.token}`
      config.timeout = Math.max(1, Math.min(budget.policy.timeout, Math.floor(budget.remaining())))
      budget.adapter ||= axios.getAdapter(config.adapter)
      config.adapter = async (attemptConfig) => {
        try {
          const beforeSend = budget.failure()
          if (beforeSend) throw beforeSend
          // Native adapters obey signal; this race also bounds adapters that settle late.
          return await budget.waitFor(budget.adapter(attemptConfig))
        } catch (error) {
          error.config ||= attemptConfig
          throw error
        }
      }
      return config
    } catch (error) {
      budget.finish()
      // Preserve an inner refresh request's session identity and diagnostic handling.
      error.config ||= config
      throw error
    }
  })

  request.interceptors.response.use(
    (response) => {
      if (response.data?.code === 401) clearUnauthorizedSession(response.config)
      const expired = response.config.__requestBudget?.failure()
      if (expired) return fail(expired, response.config)
      response.config.__requestBudget?.finish()
      if (['blob', 'arraybuffer'].includes(response.config.responseType)) return response
      const res = response.data
      if (res.code !== 200) {
        if (res.code === 403) message.warning(res.message || '无权限执行该操作')
        else message.error(res.message || '请求失败')
        return Promise.reject(
          Object.assign(new Error(res.message), { code: res.code, __requestNoticeShown: true })
        )
      }
      return res
    },
    async (error) => {
      const config = error.config
      const budget = config?.__requestBudget
      if (
        budget &&
        !budget.closed &&
        shouldRetry(error, config, budget.policy) &&
        !budget.failure()
      ) {
        const attempt = (config.__retryCount || 0) + 1
        const delay = retryAfterDelay(error, attempt, budget.policy, wallNow())
        if (Number.isFinite(delay) && delay < budget.remaining()) {
          config.__retryCount = attempt
          // Optional Render prewarm is never inferred from hostname, timeout or a failed write.
          if (serviceWarmupEnabled(env) && [429, 502, 503, 504].includes(error.response?.status))
            wake()
          if (!budget.noticeShown) {
            budget.noticeShown = true
            message({
              message: '连接暂时不稳定，正在重新尝试…',
              type: 'info',
              duration: 2000,
              grouping: true
            })
          }
          try {
            await budget.delay(delay)
          } catch (canceled) {
            return fail(canceled, config)
          }
          return request(config)
        }
      }
      return fail(error, config)
    }
  )
  return request
}

export default createRequestClient()
