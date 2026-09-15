// 全局唤醒器实例。依赖 import.meta.env，故与纯逻辑的 prewarm.js 分开，后者保持可在 node 下单测。
import { createServiceWaker, resolveWakeTargets } from './prewarm'

const targets = resolveWakeTargets(import.meta.env.VITE_PREWARM_URLS)

/** 常规唤醒：应用启动、路由切换、标签页切回时调用，至多每 10 分钟一次 */
export const wakeServices = createServiceWaker({ targets })

/**
 * 紧急唤醒：请求撞上冷启动（429 / 5xx / 超时）时立即调用，至多每 30 秒一次。
 *
 * 实测（2026-09-15）：经网关转发到休眠服务的请求，Render 5 秒内直接回 429、且不触发唤醒；
 * 同一服务由浏览器 / 外部直连则会被正常唤醒（约 45s）。所以只靠经网关重试，永远等不到服务醒来。
 */
export const wakeServicesNow = createServiceWaker({ targets, interval: 30 * 1000 })
