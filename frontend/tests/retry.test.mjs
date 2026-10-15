// Replay/budget policy; request-policy.test.mjs also exercises actual Axios interceptors offline.
import assert from 'node:assert/strict'
import {
  shouldRetry,
  isTransientFailure,
  retryDelay,
  MAX_RETRY,
  RETRY_DELAYS,
  resolveRequestProfile,
  resolveRequestPolicy,
  loginRequestOptions,
  serviceWarmupEnabled,
  retryAfterDelay
} from '../src/utils/retry.js'

let count = 0
const check = (name, run) => {
  run()
  count++
  console.log(`  ✓ ${name}`)
}
const config = (options = {}) => ({ method: 'get', ...options })
const failure = (status) => ({ response: { status } })
check('standard is explicit bounded 15s/30s and two short retries', () => {
  const policy = resolveRequestPolicy(config())
  assert.equal(policy.timeout, 15000)
  assert.equal(policy.budget, 30000)
  assert.equal(policy.maxRetry, 2)
  assert.deepEqual(RETRY_DELAYS, [250, 750])
  assert.equal(MAX_RETRY, RETRY_DELAYS.length)
  assert.equal(retryDelay(99), 750)
})
for (const method of ['post', 'put', 'patch', 'delete']) {
  check(
    `${method}: 429, gateway failure, timeout and retryUnsafe never authorize write replay`,
    () => {
      for (const error of [
        failure(429),
        failure(503),
        { code: 'ECONNABORTED' },
        { code: 'ERR_NETWORK' }
      ]) {
        assert.equal(shouldRetry(error, config({ method, retry: true, retryUnsafe: true })), false)
      }
    }
  )
}
check('only read methods replay known transient transport failures', () => {
  for (const method of ['GET', 'head', 'options']) {
    for (const error of [
      failure(429),
      failure(502),
      failure(503),
      failure(504),
      { code: 'ETIMEDOUT' },
      { code: 'ERR_NETWORK' }
    ]) {
      assert.equal(shouldRetry(error, config({ method })), true)
    }
  }
  assert.equal(shouldRetry(new Error('application failure'), config()), false)
  for (const status of [400, 401, 403, 404, 409, 500])
    assert.equal(shouldRetry(failure(status), config()), false)
  assert.equal(shouldRetry(failure(503), config({ method: 42 })), false)
})
check('cancel, exhausted budget and caller disabling retry are terminal', () => {
  for (const code of ['ERR_CANCELED', 'ERR_REQUEST_BUDGET']) {
    assert.equal(isTransientFailure({ code }), false)
    assert.equal(shouldRetry({ code }, config()), false)
  }
  assert.equal(shouldRetry(failure(503), config({ retry: false })), false)
  assert.equal(shouldRetry(failure(503), config({ __retryCount: 2 })), false)
  assert.equal(shouldRetry(failure(503), config({ signal: { aborted: true } })), false)
  assert.equal(shouldRetry(failure(503), undefined), false)
})
check(
  'explicit long operations and binary downloads retain time and do not automatically replay',
  () => {
    const long = resolveRequestPolicy(config({ timeout: 120000 }))
    assert.equal(long.timeout, 120000)
    assert.equal(long.budget, 120000)
    assert.equal(long.maxRetry, 0)
    const binary = resolveRequestPolicy(config({ responseType: 'blob', timeout: 15000 }))
    assert.equal(binary.timeout, 60000)
    assert.equal(binary.budget, 90000)
    assert.equal(binary.maxRetry, 0)
    const explicit = resolveRequestPolicy(
      config({ timeout: 90000, requestBudgetMs: 100000, retry: false })
    )
    assert.equal(explicit.timeout, 90000)
    assert.equal(explicit.budget, 100000)
    assert.equal(explicit.maxRetry, 0)
  }
)
check('login is 15s/30s with no POST replay; legacy Render is only explicit', () => {
  assert.deepEqual(loginRequestOptions(), { timeout: 15000, requestBudgetMs: 30000, retry: false })
  assert.equal(resolveRequestProfile({ VITE_REQUEST_PROFILE: 'standard' }).timeout, 15000)
  const legacy = resolveRequestProfile({ VITE_REQUEST_PROFILE: 'render' })
  assert.equal(legacy.timeout, 60000)
  assert.equal(legacy.budget, 240000)
  assert.equal(legacy.maxRetry, 6)
  assert.deepEqual(loginRequestOptions({ VITE_REQUEST_PROFILE: 'render' }), {
    timeout: 180000,
    requestBudgetMs: 240000,
    retry: false
  })
})
check(
  'invalid and overflowing timeout/budget values cannot create unbounded or wrapped timers',
  () => {
    for (const value of [0, -1, Infinity, NaN, 2147483648]) {
      const policy = resolveRequestPolicy(config({ timeout: value, requestBudgetMs: value }))
      assert.equal(policy.timeout, 15000)
      assert.equal(policy.budget, 30000)
    }
  }
)
check('service warmup requires both the explicit Render profile and enable flag', () => {
  assert.equal(serviceWarmupEnabled({}), false)
  assert.equal(serviceWarmupEnabled({ VITE_ENABLE_SERVICE_WARMUP: 'true' }), false)
  assert.equal(serviceWarmupEnabled({ VITE_REQUEST_PROFILE: 'render' }), false)
  assert.equal(
    serviceWarmupEnabled({ VITE_REQUEST_PROFILE: 'render', VITE_ENABLE_SERVICE_WARMUP: 'true' }),
    true
  )
})
check('read Retry-After delays respect server floor without changing write safety', () => {
  const policy = resolveRequestPolicy(config())
  assert.equal(retryAfterDelay({ response: { headers: { 'retry-after': '2' } } }, 1, policy), 2000)
  assert.equal(retryAfterDelay({ response: { headers: { 'retry-after': 'bad' } } }, 1, policy), 250)
  assert.equal(
    retryAfterDelay(
      { response: { headers: { 'retry-after': 'Tue, 13 Oct 2026 00:00:02 GMT' } } },
      1,
      policy,
      Date.parse('2026-10-13T00:00:00Z')
    ),
    2000
  )
})
console.log(`\n政策检查 ${count} / ${count}`)
await import('./request-policy.test.mjs')
await import('./request-default-cycle.test.mjs')
