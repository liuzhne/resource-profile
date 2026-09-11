import request from '@/utils/request'

/* ========== 概览 / 报告 ========== */
export const getMentalOverview = () => request.get('/mental/overview')
export const getMentalAnalysis = () => request.get('/mental/analysis')

/* ========== 问卷元数据（教师/管理员） ========== */
export const getQuestionnaireList = (params) => request.get('/mental/questionnaires', { params })
export const getQuestionnaireDetail = (id) => request.get(`/mental/questionnaires/${id}`)
export const createQuestionnaire = (data) => request.post('/mental/questionnaires', data)
export const updateQuestionnaire = (id, data) => request.put(`/mental/questionnaires/${id}`, data)
export const deleteQuestionnaire = (id) => request.delete(`/mental/questionnaires/${id}`)

/* ========== 问卷完整内容（题目 + 等级规则） ========== */
export const getQuestionnaireFull = (id) => request.get(`/mental/questionnaires/${id}/full`)

/* ========== 题目 CRUD（教师/管理员） ========== */
export const listQuestions = (questionnaireId) =>
  request.get(`/mental/questionnaires/${questionnaireId}/questions`)
export const addQuestion = (questionnaireId, data) =>
  request.post(`/mental/questionnaires/${questionnaireId}/questions`, data)
export const updateQuestion = (questionId, data) =>
  request.put(`/mental/questions/${questionId}`, data)
export const deleteQuestion = (questionId) => request.delete(`/mental/questions/${questionId}`)

/* ========== 完成情况（教师/管理员） ========== */
export const getCompletionList = (questionnaireId) =>
  request.get(`/mental/questionnaires/${questionnaireId}/completion`)

/* ========== 学生侧 ========== */
export const studentListQuestionnaires = (userId) =>
  request.get('/mental/student/questionnaires', { params: { userId } })
export const studentGetQuestionnaireForTaking = (id) =>
  request.get(`/mental/student/questionnaires/${id}`)
export const studentSubmitAnswers = (data) => request.post('/mental/student/assessments', data)
export const studentMyHistory = (userId) =>
  request.get('/mental/student/assessments', { params: { userId } })
export const studentGetMyAssessment = (userId, assessmentId) =>
  request.get(`/mental/student/assessments/${assessmentId}`, { params: { userId } })
