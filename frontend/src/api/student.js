import request from '@/utils/request'

export const getStudentList = (params) => {
  return request.get('/student/list', { params })
}

export const getStudentDetail = (id) => {
  return request.get(`/student/${id}`)
}

export const createStudent = (data) => {
  return request.post('/student', data)
}

export const updateStudent = (id, data) => {
  return request.put(`/student/${id}`, data)
}

export const deleteStudent = (id) => {
  return request.delete(`/student/${id}`)
}
