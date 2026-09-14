// 服务预热的回归护栏：预热是背景行为，任何失败都不得冒泡影响页面。
// 纯 node 运行，不依赖测试框架：npm run test:prewarm
import {
  GATEWAY_HEALTH,
  WAKE_INTERVAL_MS,
  resolveWakeTargets,
  createServiceWaker,
  installServiceWarmup
} from '../src/utils/prewarm.js'

let pass = 0,
  fail = 0
const ok = (name, cond) => {
  cond ? pass++ : (fail++, console.log('  ✗ ' + name))
}

const A = 'https://svc-a.example.com/actuator/health'
const B = 'https://svc-b.example.com/actuator/health'

// —— 目标解析 ——
ok('未配置时只唤醒网关', JSON.stringify(resolveWakeTargets()) === JSON.stringify([GATEWAY_HEALTH]))
ok('null 视同未配置', resolveWakeTargets(null).length === 1)
const parsed = resolveWakeTargets(` ${A} , ,${B},${A}`)
ok('解析逗号分隔并去空白、去重', JSON.stringify(parsed) === JSON.stringify([GATEWAY_HEALTH, A, B]))

// —— 一次并行唤醒全部目标 ——
let clock = 0
let calls = []
const spy = (url, opts) => {
  calls.push({ url, opts })
  return Promise.resolve({ ok: true })
}
let wake = createServiceWaker({ targets: [GATEWAY_HEALTH, A, B], fetchImpl: spy, now: () => clock })
ok('首次调用返回 true', wake() === true)
ok('一次打遍所有目标', calls.length === 3)
ok(
  '覆盖每个目标',
  [GATEWAY_HEALTH, A, B].every((u) => calls.some((c) => c.url === u))
)
ok(
  '用 GET',
  calls.every((c) => c.opts.method === 'GET')
)
ok(
  'no-cors：跨域地址无需对方配 CORS',
  calls.every((c) => c.opts.mode === 'no-cors')
)
ok(
  '不走缓存',
  calls.every((c) => c.opts.cache === 'no-store')
)
ok(
  '不带凭据',
  calls.every((c) => c.opts.credentials === 'omit')
)

// —— 节流：使用期间保活，但不重复刷请求 ——
clock = WAKE_INTERVAL_MS - 1
ok('间隔内再次调用返回 false', wake() === false)
ok('间隔内不再发请求', calls.length === 3)
clock = WAKE_INTERVAL_MS
ok('到达间隔后再次唤醒', wake() === true)
ok('再次唤醒同样打遍所有目标', calls.length === 6)
ok('间隔短于 Render 15 分钟休眠阈值', WAKE_INTERVAL_MS < 15 * 60 * 1000)

// —— 失败必须完全静默 ——
let unhandled = null
const onUnhandled = (e) => {
  unhandled = e
}
process.on('unhandledRejection', onUnhandled)
const rejecting = () => Promise.reject(new Error('429 Too Many Requests'))
wake = createServiceWaker({ targets: [A, B], fetchImpl: rejecting })
ok('拒绝时不抛同步异常', wake() === true)
await new Promise((r) => setTimeout(r, 50))
ok('拒绝时无 unhandledRejection', unhandled === null)
process.off('unhandledRejection', onUnhandled)

// —— 某个目标同步抛出，不影响其余目标 ——
calls = []
const flaky = (url, opts) => {
  if (url === A) throw new Error('boom')
  return spy(url, opts)
}
wake = createServiceWaker({ targets: [A, B], fetchImpl: flaky })
let threw = false
try {
  wake()
} catch {
  threw = true
}
ok('fetch 同步抛出被吞掉', threw === false)
ok('其余目标照常唤醒', calls.length === 1 && calls[0].url === B)

// —— 环境不支持 fetch 时优雅退出 ——
// 注意：不传 fetchImpl 会走默认参数（真实 fetch），须显式传 null 才走到该分支
wake = createServiceWaker({ targets: [A], fetchImpl: null })
ok('无 fetch 实现时返回 false', wake() === false)

// —— 安装：立即唤醒 + 路由切换 / 切回标签页时再唤醒 ——
let wakes = 0
const hooks = []
const fakeRouter = { afterEach: (fn) => hooks.push(fn) }
const listeners = {}
const fakeDoc = {
  visibilityState: 'visible',
  addEventListener: (type, fn) => {
    listeners[type] = fn
  }
}
installServiceWarmup(fakeRouter, () => wakes++, fakeDoc)
ok('安装即唤醒一次', wakes === 1)
ok('注册了路由钩子', hooks.length === 1)
hooks[0]()
ok('路由切换时唤醒', wakes === 2)
fakeDoc.visibilityState = 'hidden'
listeners.visibilitychange()
ok('标签页隐藏时不唤醒', wakes === 2)
fakeDoc.visibilityState = 'visible'
listeners.visibilitychange()
ok('切回标签页时唤醒', wakes === 3)
let installThrew = false
try {
  installServiceWarmup({ afterEach: () => {} }, () => {}, undefined)
} catch {
  installThrew = true
}
ok('无 document 环境不报错', installThrew === false)

console.log(`\n通过 ${pass} / ${pass + fail}`)
process.exit(fail ? 1 : 0)
