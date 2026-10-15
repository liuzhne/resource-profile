import request from '@/utils/request'

export const getStatistics = () => {
  return request.get('/data/dashboard/statistics')
}

export const getTrendData = (period = 'week') => {
  return request.get('/data/dashboard/trend', { params: { period } })
}

export const getDistributionData = () => {
  return request.get('/data/dashboard/distribution')
}

export const getRecentLogins = () => {
  return request.get('/data/dashboard/recentLogins')
}

export const getDashboardTodos = (limit = 10) =>
  request.get('/data/dashboard/todos', { params: { limit } })
export const getNotifications = (limit = 10) =>
  request.get('/data/notifications', { params: { limit } })
export const searchDirectory = (q, limit = 10) =>
  request.get('/data/search', { params: { q, limit } })
