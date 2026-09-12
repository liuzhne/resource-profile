// 冷启动重试的决策逻辑。
// 抽成纯函数便于单测：这里判错会导致写操作被重复执行（如「新增学生」落库两条）。

// 重试窗口必须覆盖冷启动量级（实测 30~120s）。
//
// 关键：Render 在服务休眠期是「秒回 429」而非挂起，所以 axios 的 timeout 对这条路径
// 完全无效 —— 唯一起作用的是重试次数与间隔。此前 2 次 / 累计 7s 的窗口远不够，
// 三次请求会在 7 秒内全部撞 429 然后放弃，用户看到的是「点了就失败，连等待都没有」。
//
// 下面的时间表累计约 131s，覆盖最坏情况的冷启动。
export const MAX_RETRY = 6
export const RETRY_DELAYS = [3000, 8000, 15000, 25000, 35000, 45000]

// 天然幂等的方法，超时后重放不产生副作用
const IDEMPOTENT_METHODS = ['get', 'head', 'options']

/** 冷启动类失败：服务在休眠/唤醒中，而非业务出错 */
export const isColdStartFailure = (error) => {
  const status = error?.response?.status
  if (status === 429 || [502, 503, 504].includes(status)) return true
  return error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT' || !error?.response
}

/**
 * 是否值得重试。
 *
 * 关键约束：超时不代表请求没被服务端执行 —— 冷启动时很可能是「已受理但响应慢」。
 * 因此非幂等写操作默认不因超时重试，否则会重复落库。
 * 唯一对所有方法放行的是 429：那是 Render 边缘层在休眠期直接拦掉的，
 * 请求从未抵达应用，重放绝对安全。
 */
export const shouldRetry = (error, config) => {
  if (!config || config.retry === false) return false
  if ((config.__retryCount || 0) >= MAX_RETRY) return false

  const status = error?.response?.status

  // 429：请求未抵达应用，任何方法都可安全重试
  if (status === 429) return true

  const method = (config.method || 'get').toLowerCase()
  // retryUnsafe 供调用方对「重复执行无害」的写操作显式开启（如登录）
  const safeToReplay = IDEMPOTENT_METHODS.includes(method) || config.retryUnsafe === true
  if (!safeToReplay) return false

  if (error?.code === 'ECONNABORTED' || error?.code === 'ETIMEDOUT') return true
  if (!error?.response) return true
  return [502, 503, 504].includes(status)
}

/** 第 n 次重试前的等待时长（n 从 1 起） */
export const retryDelay = (attempt) =>
  RETRY_DELAYS[attempt - 1] ?? RETRY_DELAYS[RETRY_DELAYS.length - 1]
