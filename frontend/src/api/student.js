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

export const getStudentOptions = () => request.get('/student/options')
export const getStudentAccounts = () => request.get('/student/accounts')
export const getStudentAcademic = (id, term) =>
  request.get(`/student/${id}/academic`, { params: { term } })
export const getStudentRank = (id) => request.get(`/student/${id}/rank`)
export const getStudentQuality = (id) => request.get(`/student/${id}/quality`)
export const getStudentMentalSummary = (id) => request.get(`/mental/students/${id}/assessments`)
export const saveStudentQuality = (id, recordId, data) =>
  recordId
    ? request.put(`/student/${id}/quality/${recordId}`, data)
    : request.post(`/student/${id}/quality`, data)
export const deleteStudentQuality = (id, recordId) =>
  request.delete(`/student/${id}/quality/${recordId}`)
