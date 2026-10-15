import request from '@/utils/request'

// 浏览器只读同源的受保护接口；Langfuse 凭据由后端持有。
export const getObservations = (params) =>
  request.get('/agent/api/v1/observability/observations', { params, timeout: 20000, retry: false })
