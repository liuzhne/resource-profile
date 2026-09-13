import request from '@/utils/request'

// 登录是冷启动后的第一个请求，必然撞上服务唤醒。
// Render 会把首个请求挂住约 114s 才返回 429，客户端超时必须留出余量，
// 否则 axios 会先于 Render 放弃，连那次「触发唤醒」的机会都拿不到。
// POST 但重放无害（仅重新签发 token），故显式开启 retryUnsafe。
export const login = (data) => {
  return request.post('/auth/login', data, { timeout: 180000, retryUnsafe: true })
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
