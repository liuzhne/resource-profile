import request from '@/utils/request'

export const getUserList = (params) => request.get('/user/list', { params })

export const saveUser = (data) =>
  data.id ? request.put(`/user/${data.id}`, data) : request.post('/user', data)
export const deleteUser = (id) => request.delete(`/user/${id}`)
export const getUserRoles = (id) => request.get(`/user/${id}/roles`)
export const getRoles = () => request.get('/user/roles')
export const saveRole = (data) =>
  data.id ? request.put(`/user/roles/${data.id}`, data) : request.post('/user/roles', data)
export const deleteRole = (id) => request.delete(`/user/roles/${id}`)
export const getPermissions = () => request.get('/user/roles/permissions')
export const getRolePermissions = (id) => request.get(`/user/roles/${id}/permissions`)
export const saveRolePermissions = (id, data) => request.put(`/user/roles/${id}/permissions`, data)

export const getAccountRequests = (status = 'OPEN') =>
  request.get('/user/support', { params: { status } })
export const resolveAccountRequest = (id, data) => request.put(`/user/support/${id}`, data)
