import request from '@/utils/request'

// 登录是冷启动后的第一个请求，必然撞上服务唤醒：放宽到 120s 并允许重试。
// POST 但重放无害（仅重新签发 token），故显式开启 retryUnsafe。
export const login = (data) => {
  return request.post('/auth/login', data, { timeout: 120000, retryUnsafe: true })
}

export const getUserInfo = () => {
  return request.get('/auth/userInfo')
}

export const logout = () => {
  return request.post('/auth/logout')
}

export const refreshToken = () => {
  return request.post('/auth/refresh')
}
