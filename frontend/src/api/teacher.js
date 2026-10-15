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

export const getTeacherOptions = () => request.get('/teacher/options')
export const getTeacherAccounts = () => request.get('/teacher/accounts')
export const getTeacherPortrait = (id) => request.get(`/teacher/${id}/portrait`)
export const saveTeacherRecord = (id, recordId, data) =>
  recordId
    ? request.put(`/teacher/${id}/records/${recordId}`, data)
    : request.post(`/teacher/${id}/records`, data)
export const deleteTeacherRecord = (id, recordId) =>
  request.delete(`/teacher/${id}/records/${recordId}`)
export const saveTeacherEvaluation = (id, recordId, data) =>
  recordId
    ? request.put(`/teacher/${id}/evaluations/${recordId}`, data)
    : request.post(`/teacher/${id}/evaluations`, data)
export const deleteTeacherEvaluation = (id, recordId) =>
  request.delete(`/teacher/${id}/evaluations/${recordId}`)
