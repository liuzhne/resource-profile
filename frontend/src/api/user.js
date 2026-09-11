import request from '@/utils/request'

export const getUserList = (params) => {
  return request.get('/user/list', { params })
}

export const getUserDetail = (id) => {
  return request.get(`/user/${id}`)
}

export const createUser = (data) => {
  return request.post('/user', data)
}

export const updateUser = (id, data) => {
  return request.put(`/user/${id}`, data)
}

export const deleteUser = (id) => {
  return request.delete(`/user/${id}`)
}
