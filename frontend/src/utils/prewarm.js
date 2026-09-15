// 后端服务预热：打开网站即并行唤醒，使用期间保持清醒。
//
// 背景：Render 免费层每个服务各自独立休眠（闲置 15 分钟），唤醒约 45~60s。
// 实测（2026-09-14）：网关醒着时，data / teacher / student / mental 仍全部在睡，各需 42~62s。
// 于是登录后进首页要先等网关醒、网关再去叫醒 data-service —— 两段冷启动串行叠加；
// 之后每进一个新页面（教师 / 学生 / 心理）又要撞上另一个服务的冷启动。
//
// 做法：
// ① 打开网站即并行唤醒全部核心服务，把「串行 N 段」压成「并行 1 段」，并与用户填登录
//    表单的时间重叠。已有 token、跳过登录页直接进首页的用户同样覆盖。
// ② 切换页面 / 切回标签页时再唤醒一次，但至多每 10 分钟一次（短于 15 分钟休眠阈值）：
//    有人在用就保持清醒，没人用就照常休眠，不白耗免费实例时长。
//
// 刻意不复用 @/utils/request：那条链路带拦截器，会弹错误提示、进重试、
// 甚至在 401 时把用户登出 —— 预热是背景行为，任何失败都必须完全静默。

/** 网关健康检查。经前端 rewrite 的 /api/* 规则同源转发到网关，且该前缀在网关白名单内。 */
export const GATEWAY_HEALTH = '/api/actuator/health'

/** 两次唤醒的最小间隔：须短于 Render 的 15 分钟休眠阈值，才能在使用期间保持清醒 */
export const WAKE_INTERVAL_MS = 10 * 60 * 1000

/** 唤醒最长可能耗时约 2 分钟，留足余量后放弃，避免连接悬挂过久 */
const ABORT_AFTER_MS = 180000

/**
 * 唤醒目标 = 网关 + VITE_PREWARM_URLS（逗号分隔，填各服务的公网健康检查地址）。
 * 下游服务必须绕开网关直接唤醒，才能与网关并行，而不是排在网关醒来之后。
 */
export const resolveWakeTargets = (extraUrls = '') => {
  const extra = (extraUrls || '')
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
  return [...new Set([GATEWAY_HEALTH, ...extra])]
}

// 只为触发唤醒、不读响应：no-cors 让跨域地址无需对方配置 CORS 也能发出（响应不透明，正合适）
const fire = (fetchImpl, url) => {
  const controller = typeof AbortController === 'function' ? new AbortController() : null
  const timer = controller ? setTimeout(() => controller.abort(), ABORT_AFTER_MS) : null
  const settle = () => {
    if (timer !== null) clearTimeout(timer)
  }
  try {
    Promise.resolve(
      fetchImpl(url, {
        method: 'GET',
        mode: 'no-cors',
        cache: 'no-store',
        credentials: 'omit',
        signal: controller ? controller.signal : undefined
      })
    ).then(settle, settle)
  } catch {
    // fetch 实现同步抛出（参数非法、环境异常等）时，异常发生在 Promise 包装之前，须在此吞掉，
    // 否则会冒泡到调用方（路由钩子 / 应用启动）把页面炸掉
    settle()
  }
}

/**
 * 创建带节流的唤醒器：调用时并行打一遍全部目标；距上次唤醒不足 interval 则跳过。
 *
 * @param {object} options
 * @param {string[]} options.targets 唤醒地址
 * @param {Function} [options.fetchImpl] 便于单测注入；默认用全局 fetch
 * @param {Function} [options.now] 便于单测注入时钟
 * @param {number} [options.interval] 最小唤醒间隔
 * @returns {() => boolean} 调用后返回本次是否真的发出了请求
 */
export const createServiceWaker = ({
  targets,
  fetchImpl = globalThis.fetch,
  now = Date.now,
  interval = WAKE_INTERVAL_MS
}) => {
  let lastWakeAt = null
  return () => {
    if (typeof fetchImpl !== 'function') return false
    const t = now()
    if (lastWakeAt !== null && t - lastWakeAt < interval) return false
    lastWakeAt = t
    targets.forEach((url) => fire(fetchImpl, url))
    return true
  }
}

/**
 * 安装预热：立即唤醒一次；之后在路由切换、标签页切回可见时按节流再唤醒。
 * 刻意不用定时器轮询 —— 用户没有操作就不唤醒，让服务照常休眠。
 */
export const installServiceWarmup = (router, wake, doc = globalThis.document) => {
  wake()
  router.afterEach(() => {
    wake()
  })
  doc?.addEventListener?.('visibilitychange', () => {
    if (doc.visibilityState === 'visible') wake()
  })
}
