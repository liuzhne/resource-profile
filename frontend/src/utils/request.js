import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { shouldRetry, retryDelay, isColdStartFailure, MAX_RETRY } from './retry'

// Render 免费层的 web service 闲置约 15 分钟后休眠，冷启动实测 30~120s。
// 原先 10s 的超时必然等不到唤醒完成，表现为「接口永远超时」；
// 故放宽默认超时，并对「确定未抵达应用」的失败自动重试。
const DEFAULT_TIMEOUT = 60000

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: DEFAULT_TIMEOUT,
  headers: {
    'Content-Type': 'application/json'
  }
})

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// 唤醒提示：全局只保留一条，避免多个并发请求把屏幕刷满
let wakingNotice = null

// 冷启动最长要等约 2 分钟，必须让用户看到进度，否则会以为页面卡死
const showWakingNotice = (attempt) => {
  const text = `服务正在唤醒，请稍候…（第 ${attempt}/${MAX_RETRY} 次重试）`
  if (wakingNotice) {
    // 已有提示则就地更新文案，不再叠加新的一条
    const el = document.querySelector('.waking-notice .el-message__content')
    if (el) el.textContent = text
    return
  }
  wakingNotice = ElMessage({
    message: text,
    type: 'info',
    duration: 0,
    showClose: true,
    customClass: 'waking-notice',
    onClose: () => {
      wakingNotice = null
    }
  })
}

const closeWakingNotice = () => {
  if (!wakingNotice) return
  wakingNotice.close()
  wakingNotice = null
}

request.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response) => {
    // 只要有请求成功返回，说明服务已醒，撤掉唤醒提示
    closeWakingNotice()

    if (response.config.responseType === 'blob') {
      return response
    }

    const res = response.data

    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')

      if (res.code === 401) {
        const userStore = useUserStore()
        userStore.logout()
      }

      return Promise.reject(new Error(res.message))
    }

    return res
  },
  async (error) => {
    const config = error.config

    if (shouldRetry(error, config)) {
      config.__retryCount = (config.__retryCount || 0) + 1
      showWakingNotice(config.__retryCount)
      await sleep(retryDelay(config.__retryCount))
      return request(config)
    }

    closeWakingNotice()

    // HTTP 401（网关 JwtAuthGlobalFilter 拒绝：未登录/过期/登出后旧 token）→ 自动登出。
    // 注意：业务层 401（body code===401）在上面的成功分支处理；这里专门兜 HTTP 状态码 401。
    if (error.response?.status === 401) {
      const userStore = useUserStore()
      userStore.logout()
    }

    // 重试用尽后才提示，且把冷启动类失败翻译成人话，避免用户看到「timeout of 60000ms exceeded」
    const message = isColdStartFailure(error)
      ? '服务唤醒超时，请稍后重试'
      : error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
