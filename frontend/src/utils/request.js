import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json'
  }
})

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
  (error) => {
    // HTTP 401（网关 JwtAuthGlobalFilter 拒绝：未登录/过期/登出后旧 token）→ 自动登出。
    // 注意：业务层 401（body code===401）在上面的成功分支处理；这里专门兜 HTTP 状态码 401。
    if (error.response?.status === 401) {
      const userStore = useUserStore()
      userStore.logout()
    }
    const message = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default request
