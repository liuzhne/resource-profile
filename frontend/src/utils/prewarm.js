// 网关预热。
//
// 背景：Render 免费层服务闲置 15 分钟后休眠，冷启动约 2 分钟，而 Render 只肯把请求
// 挂住约 114s 就返回 429。登录链路已通过 rewrite 直连 auth-service 绕过网关，
// 但登录之后的所有业务接口仍必须经网关（JWT 校验在那里做，不能绕），
// 于是用户刚进系统就会撞上网关的冷启动。
//
// 做法：登录页一加载就静默打一次网关健康检查，把「网关唤醒」与「用户填表单」
// 两段时间重叠。用户提交表单通常要十几到几十秒，这段时间足够网关启动一大截。
//
// 刻意不复用 @/utils/request：那条链路带拦截器，会弹错误提示、进重试、
// 甚至在 401 时把用户登出 —— 预热是背景行为，任何失败都必须完全静默。

/** 网关健康检查路径。经前端 rewrite 的 /api/* 规则转发到网关，且该前缀在网关白名单内。 */
const GATEWAY_HEALTH = '/api/actuator/health'

/** 唤醒最长可能耗时约 2 分钟，留足余量后放弃，避免连接悬挂过久 */
const ABORT_AFTER_MS = 180000

let started = false

/**
 * 触发一次网关预热。整个页面生命周期内只发一次 —— 重复请求既无益，
 * 又会额外占用 Render 的唤醒配额。
 *
 * @param {Function} fetchImpl 便于单测注入；默认用全局 fetch
 * @returns {boolean} 本次是否真的发起了请求（false 表示此前已预热过）
 */
export const prewarmGateway = (fetchImpl = globalThis.fetch) => {
  if (started) return false
  started = true

  if (typeof fetchImpl !== 'function') return false

  const controller = typeof AbortController === 'function' ? new AbortController() : null
  if (controller) setTimeout(() => controller.abort(), ABORT_AFTER_MS)

  // 不 await：纯背景行为，不阻塞页面渲染，失败一律吞掉。
  // try 包住同步调用本身 —— fetch 实现若同步抛出（参数非法、环境异常等），
  // 异常发生在 Promise 包装之前，会一路冒泡到 onMounted 把登录页炸掉。
  try {
    Promise.resolve(
      fetchImpl(GATEWAY_HEALTH, {
        method: 'GET',
        cache: 'no-store',
        signal: controller ? controller.signal : undefined
      })
    ).catch(() => {})
  } catch {
    return false
  }

  return true
}

/** 仅供单测重置模块状态 */
export const __resetPrewarm = () => {
  started = false
}
