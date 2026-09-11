import request from '@/utils/request'

export const getTeacherList = (params) => {
  return request.get('/teacher/list', { params })
}

export const getTeacherDetail = (id) => {
  return request.get(`/teacher/${id}`)
}

export const createTeacher = (data) => {
  return request.post('/teacher', data)
}

export const updateTeacher = (id, data) => {
  return request.put(`/teacher/${id}`, data)
}

export const deleteTeacher = (id) => {
  return request.delete(`/teacher/${id}`)
}
