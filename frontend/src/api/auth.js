import request from '@/utils/request'
import { loginRequestOptions } from '@/utils/retry'

// Login creates a session; a timeout/429 does not authorize replaying that POST.
export const login = (data) => {
  return request.post('/auth/login', data, loginRequestOptions(import.meta.env))
}

export const getUserInfo = (sessionToken) => {
  return request.get('/auth/userInfo', {
    headers: { Authorization: `Bearer ${sessionToken}` }
  })
}

export const logout = (sessionToken) => {
  return request.post('/auth/logout', null, {
    // 注销只属于发起时的会话，不能在重试期间撤销后来登录的新令牌。
    retry: false,
    headers: { Authorization: `Bearer ${sessionToken}` }
  })
}

export const refreshToken = (token) => {
  return request.post('/auth/refresh', null, {
    withCredentials: true,
    retry: false,
    ...(token ? { headers: { 'X-Refresh-Token': token } } : {})
  })
}

export const updateProfile = (data) => request.put('/auth/profile', data)
export const changePassword = (data) => request.post('/auth/password', data, { retry: false })

export const submitAccountRequest = (data) => request.post('/auth/support', data, { retry: false })
