import request from '@/utils/request'

export const login = (data) => {
  return request.post('/auth/login', data)
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
