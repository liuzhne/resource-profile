// 冷启动重试决策的回归护栏：判错会导致写操作重复执行（如新增学生落库两条）。
// 纯 node 运行，不依赖测试框架：npm run test:retry
import {
  shouldRetry,
  isColdStartFailure,
  retryDelay,
  MAX_RETRY,
  RETRY_DELAYS
} from '../src/utils/retry.js'

let pass = 0,
  fail = 0
const ok = (name, cond) => {
  cond ? pass++ : (fail++, console.log('  ✗ ' + name))
}

const timeout = { code: 'ECONNABORTED', message: 'timeout' }
const netdown = { message: 'Network Error' }
const r = (s) => ({ response: { status: s } })
const cfg = (o = {}) => ({ method: 'get', ...o })

// —— 429：任何方法都必须重试（请求未抵达应用）——
ok('429 + GET 重试', shouldRetry(r(429), cfg({ method: 'get' })) === true)
ok('429 + POST 重试', shouldRetry(r(429), cfg({ method: 'post' })) === true)
ok('429 + DELETE 重试', shouldRetry(r(429), cfg({ method: 'delete' })) === true)

// —— 安全关键：非幂等写操作超时绝不重试 ——
ok('POST 超时 不重试', shouldRetry(timeout, cfg({ method: 'post' })) === false)
ok('PUT 超时 不重试', shouldRetry(timeout, cfg({ method: 'put' })) === false)
ok('DELETE 超时 不重试', shouldRetry(timeout, cfg({ method: 'delete' })) === false)
ok('POST 断网 不重试', shouldRetry(netdown, cfg({ method: 'post' })) === false)
ok('POST 503 不重试', shouldRetry(r(503), cfg({ method: 'post' })) === false)

// —— 幂等方法超时可重试 ——
ok('GET 超时 重试', shouldRetry(timeout, cfg({ method: 'get' })) === true)
ok('GET 断网 重试', shouldRetry(netdown, cfg({ method: 'get' })) === true)
ok('GET 502 重试', shouldRetry(r(502), cfg({ method: 'get' })) === true)
ok('GET 504 重试', shouldRetry(r(504), cfg({ method: 'get' })) === true)

// —— 显式 opt-in（登录）——
ok(
  'POST+retryUnsafe 超时 重试',
  shouldRetry(timeout, cfg({ method: 'post', retryUnsafe: true })) === true
)

// —— 业务错误不该重试 ——
ok('400 不重试', shouldRetry(r(400), cfg({ method: 'get' })) === false)
ok('401 不重试', shouldRetry(r(401), cfg({ method: 'get' })) === false)
ok('404 不重试', shouldRetry(r(404), cfg({ method: 'get' })) === false)
ok('500 不重试', shouldRetry(r(500), cfg({ method: 'get' })) === false)

// —— 次数上限与显式关闭 ——
ok('达上限不再重试', shouldRetry(r(429), cfg({ __retryCount: MAX_RETRY })) === false)
ok('retry:false 关闭', shouldRetry(r(429), cfg({ retry: false })) === false)
ok('无 config 不重试', shouldRetry(r(429), undefined) === false)

// —— 冷启动判定 ——
ok('429 属冷启动', isColdStartFailure(r(429)) === true)
ok('超时属冷启动', isColdStartFailure(timeout) === true)
ok('503 属冷启动', isColdStartFailure(r(503)) === true)
ok('400 非冷启动', isColdStartFailure(r(400)) === false)

// —— 退避 ——
ok(
  '退避递增',
  RETRY_DELAYS.every((d, i) => i === 0 || d > RETRY_DELAYS[i - 1])
)
ok(
  'retryDelay 对齐表',
  RETRY_DELAYS.every((d, i) => retryDelay(i + 1) === d)
)
ok('超出用末值', retryDelay(99) === RETRY_DELAYS[RETRY_DELAYS.length - 1])
ok('次数与时间表一致', MAX_RETRY === RETRY_DELAYS.length)

// —— 窗口必须覆盖冷启动（回归护栏）——
// Render 休眠期是秒回 429 而非挂起，axios timeout 无效，只有重试窗口起作用。
// 冷启动实测 30~120s，窗口若短于 120s 会退化成「点了就失败」。
const windowMs = RETRY_DELAYS.reduce((a, b) => a + b, 0)
ok(`重试窗口 ${windowMs / 1000}s 覆盖 120s 冷启动`, windowMs >= 120000)

console.log(`\n通过 ${pass} / ${pass + fail}`)
process.exit(fail ? 1 : 0)
