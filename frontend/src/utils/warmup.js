// 全局唤醒器实例。依赖 import.meta.env，故与纯逻辑的 prewarm.js 分开，后者保持可在 node 下单测。
import { createServiceWaker, resolveWakeTargets } from './prewarm'
import { serviceWarmupEnabled } from './retry'

const enabled = serviceWarmupEnabled(import.meta.env)
const targets = enabled ? resolveWakeTargets(import.meta.env.VITE_PREWARM_URLS) : []

/** 常规唤醒：应用启动、路由切换、标签页切回时调用，至多每 10 分钟一次 */
export const wakeServices = enabled ? createServiceWaker({ targets }) : () => false

/**
 * 紧急预热：仅显式启用的 Render profile，至多每 30 秒一次。
 * 请求链只在可重放的读请求收到 429 / 502 / 503 / 504 时调用；不将超时解释为休眠。
 *
 * 实测（2026-09-15）：经网关转发到休眠服务的请求，Render 5 秒内直接回 429、且不触发唤醒；
 * 同一服务由浏览器 / 外部直连则会被正常唤醒（约 45s）。所以只靠经网关重试，永远等不到服务醒来。
 */
export const wakeServicesNow = enabled
  ? createServiceWaker({ targets, interval: 30 * 1000 })
  : () => false
