// 网关预热的回归护栏：预热是背景行为，任何失败都不得冒泡影响登录。
// 纯 node 运行，不依赖测试框架：npm run test:prewarm
import { prewarmGateway, __resetPrewarm } from '../src/utils/prewarm.js'

let pass = 0,
  fail = 0
const ok = (name, cond) => {
  cond ? pass++ : (fail++, console.log('  ✗ ' + name))
}

// —— 正常触发 ——
__resetPrewarm()
let calls = []
const spy = (url, opts) => {
  calls.push({ url, opts })
  return Promise.resolve({ ok: true })
}
ok('首次调用返回 true', prewarmGateway(spy) === true)
ok('恰好发起 1 次请求', calls.length === 1)
ok('打的是网关健康检查', calls[0].url === '/api/actuator/health')
ok('用 GET', calls[0].opts.method === 'GET')
ok('不走缓存', calls[0].opts.cache === 'no-store')

// —— 只发一次 ——
ok('重复调用返回 false', prewarmGateway(spy) === false)
ok('重复调用不再发请求', calls.length === 1)

// —— 失败必须完全静默（否则会污染登录页）——
__resetPrewarm()
let unhandled = null
const onUnhandled = (e) => {
  unhandled = e
}
process.on('unhandledRejection', onUnhandled)
const rejecting = () => Promise.reject(new Error('429 Too Many Requests'))
ok('拒绝时不抛同步异常', prewarmGateway(rejecting) === true)
await new Promise((r) => setTimeout(r, 50))
ok('拒绝时无 unhandledRejection', unhandled === null)
process.off('unhandledRejection', onUnhandled)

// —— fetch 同步抛出也不能炸 ——
__resetPrewarm()
const throwing = () => {
  throw new Error('boom')
}
let threw = false
let ret = null
try {
  ret = prewarmGateway(throwing)
} catch {
  threw = true
}
ok('fetch 同步抛出被吞掉', threw === false)
ok('同步抛出时返回 false', ret === false)

// —— 环境不支持 fetch 时优雅退出 ——
// 注意：传 undefined 会触发默认参数（真实 fetch），须传 null 才走到该分支
__resetPrewarm()
ok('无 fetch 实现时返回 false', prewarmGateway(null) === false)

console.log(`\n通过 ${pass} / ${pass + fail}`)
process.exit(fail ? 1 : 0)
