const CODES = {
  INPUT_BUDGET: '请求内容超过预算',
  OUTPUT_BUDGET: '输出参数超过预算',
  QUEUE_FULL: '模型请求队列已满',
  COOLDOWN_UNAVAILABLE: '共享限流状态不可用',
  DEADLINE_EXCEEDED: '任务执行超时',
  ITERATION_LIMIT: '分析超过最大轮次',
  PROVIDER_RATE_LIMIT: '模型服务限流',
  PROVIDER_JSON_SCHEMA: '模型服务拒绝输出格式',
  PROVIDER_AUTH: '模型服务认证失败',
  PROVIDER_ERROR: '模型服务拒绝请求',
  NETWORK_ERROR: '模型服务连接失败',
  OUTPUT_INVALID: '模型输出校验失败',
  TOOL_ERROR: '工具执行失败',
  MCP_UNAVAILABLE: '工具服务未就绪',
  UNKNOWN: '未记录失败类别'
}
const STAGES = {
  LLM_REQUEST: '模型请求',
  TOOL_EXECUTION: '工具执行',
  FINAL_VALIDATION: '最终输出校验',
  COMPLIANCE_CHECKING: '合规审核',
  UNKNOWN: '未记录失败阶段'
}

export const failureCodeLabel = (code) => CODES[code] || CODES.UNKNOWN
export const failureStageLabel = (stage) => STAGES[stage] || STAGES.UNKNOWN
export const failureHttpLabel = (value) =>
  Number.isInteger(value) && value >= 400 && value <= 599 ? `HTTP ${value}` : '未记录'
