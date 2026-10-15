// Actual request.js/Axios chain, fake monotonic clock, adapters only; no server/model calls.
import assert from 'node:assert/strict'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { AxiosError } from 'axios'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const defaultStore = { token: '', ensureSession: async () => {}, clearSession() {} }
globalThis.__requestPolicyStore = defaultStore
const quietMessage = Object.assign(() => ({ close() {} }), { error() {}, warning() {} })
globalThis.__requestPolicyMessage = quietMessage
const server = await createServer({
  root,
  configFile: false,
  envDir: false,
  server: { middlewareMode: true, hmr: false, ws: false },
  optimizeDeps: { noDiscovery: true, include: [] },
  ssr: { noExternal: ['element-plus'] },
  resolve: { alias: { '@': path.join(root, 'src') } },
  plugins: [
    {
      name: 'request-policy-offline-dependencies',
      enforce: 'pre',
      resolveId(source) {
        if (source === 'element-plus') return '\0request-test:messages'
        if (
          source === '@/store/modules/user' ||
          source === path.join(root, 'src/store/modules/user')
        )
          return '\0request-test:store'
      },
      load(id) {
        if (id === '\0request-test:messages')
          return 'export const ElMessage = globalThis.__requestPolicyMessage'
        if (id === '\0request-test:store')
          return 'export const useUserStore = () => globalThis.__requestPolicyStore'
      }
    }
  ]
})
const flush = async () => {
  for (let i = 0; i < 20; i++) await Promise.resolve()
}
class Clock {
  time = 0
  serial = 0
  timers = new Map()
  now = () => this.time
  setTimer = (fn, delay) => {
    const id = ++this.serial
    this.timers.set(id, { at: this.time + delay, fn })
    return id
  }
  clearTimer = (id) => this.timers.delete(id)
  async advance(ms) {
    const target = this.time + ms
    await flush()
    for (;;) {
      const next = [...this.timers.entries()]
        .filter(([, timer]) => timer.at <= target)
        .sort((a, b) => a[1].at - b[1].at || a[0] - b[0])[0]
      if (!next) break
      this.time = next[1].at
      this.timers.delete(next[0])
      next[1].fn()
      await flush()
    }
    this.time = target
    await flush()
  }
}
// Node's timers and arrow fake timers tolerate receivers that Window timers reject.
// Capture these through the factory's real default parameters, then restore globals.
const withBrowserTimerDefaults = (clock, createClient) => {
  function nativeSetTimeout(fn, delay) {
    if (this !== undefined) throw new TypeError('Illegal invocation')
    return clock.setTimer(fn, delay)
  }
  function nativeClearTimeout(id) {
    if (this !== undefined) throw new TypeError('Illegal invocation')
    return clock.clearTimer(id)
  }
  assert.throws(() => ({ timer: nativeSetTimeout }).timer(() => {}, 1), /Illegal invocation/)
  assert.throws(() => ({ timer: nativeClearTimeout }).timer(1), /Illegal invocation/)
  const originalSetTimeout = globalThis.setTimeout
  const originalClearTimeout = globalThis.clearTimeout
  globalThis.setTimeout = nativeSetTimeout
  globalThis.clearTimeout = nativeClearTimeout
  try {
    return createClient()
  } finally {
    globalThis.setTimeout = originalSetTimeout
    globalThis.clearTimeout = originalClearTimeout
  }
}
const success = (config, data = { code: 200, data: 'fixture' }) => ({
  data,
  status: 200,
  config,
  headers: {}
})
const httpError = (config, status, headers = {}) =>
  new AxiosError('fixture transport status', 'ERR_BAD_RESPONSE', config, null, {
    status,
    config,
    headers
  })
const networkError = (config) =>
  new AxiosError('fixture network unavailable', 'ERR_NETWORK', config)
let count = 0
const check = async (name, run) => {
  await run()
  count++
  console.log(`  ✓ ${name}`)
}

try {
  const { createRequestClient, default: defaultClient } =
    await server.ssrLoadModule('/src/utils/request.js')
  const fixture = (env = {}, browserTimers = false) => {
    const clock = new Clock(),
      messages = [],
      attempts = []
    let clears = 0,
      wakes = 0
    const store = {
      token: 'fixture-session',
      ensureSession: async () => {},
      clearSession: () => {
        clears++
      }
    }
    const message = Object.assign(
      (options) => {
        messages.push(options.message)
        return { close() {} }
      },
      {
        error: (text) => messages.push(text),
        warning: (text) => messages.push(text)
      }
    )
    const dependencies = {
      env,
      getUserStore: () => store,
      message,
      wake: () => {
        wakes++
      },
      now: clock.now,
      wallNow: () => 0
    }
    const client = browserTimers
      ? withBrowserTimerDefaults(clock, () => createRequestClient(dependencies))
      : createRequestClient({
          ...dependencies,
          setTimer: clock.setTimer,
          clearTimer: clock.clearTimer
        })
    const setAdapter = (adapter) => {
      client.defaults.adapter = (config) => {
        attempts.push(config)
        return adapter(config)
      }
    }
    const timed = (config, delay, result) =>
      new Promise((resolve, reject) => {
        let timer
        const stop = () => {
          clock.clearTimer(timer)
          config.signal.removeEventListener('abort', onAbort)
        }
        const onAbort = () => {
          stop()
          reject(new AxiosError('fixture canceled', 'ERR_CANCELED', config))
        }
        config.signal.addEventListener('abort', onAbort, { once: true })
        timer = clock.setTimer(() => {
          stop()
          result
            ? resolve(success(config, result))
            : reject(new AxiosError('fixture timeout', 'ECONNABORTED', config))
        }, delay)
      })
    return {
      client,
      clock,
      attempts,
      store,
      messages,
      setAdapter,
      timed,
      clears: () => clears,
      wakes: () => wakes
    }
  }

  await check(
    'default browser timer receivers allow successful login POST and preserve an HTTP 400 failure',
    async () => {
      for (const status of [200, 400]) {
        const f = fixture({}, true)
        f.setAdapter(async (c) => {
          if (status === 400) throw httpError(c, status)
          return success(c)
        })
        if (status === 200) assert.equal((await f.client.post('/auth/login', {})).code, 200)
        else
          await assert.rejects(
            f.client.post('/auth/login', {}),
            (error) => error.response.status === 400
          )
        assert.equal(f.attempts.length, 1)
        assert.equal(f.clears(), 0)
        assert.equal(f.messages.length, status === 400 ? 1 : 0)
        assert.equal(f.clock.timers.size, 0)
      }
    }
  )
  await check(
    'default browser timers also retain valid receivers during read backoff and successful cleanup',
    async () => {
      const f = fixture({}, true)
      f.setAdapter(async (c) => {
        if (f.attempts.length === 1) throw httpError(c, 503)
        return success(c)
      })
      const result = f.client.get('/fixture/read')
      await f.clock.advance(250)
      assert.equal((await result).code, 200)
      assert.equal(f.attempts.length, 2)
      assert.equal(f.clears(), 0)
      assert.equal(f.clock.timers.size, 0)
    }
  )
  await check(
    'default browser timer cleanup preserves caller cancellation and deadline errors without losing session',
    async () => {
      for (const cancel of [false, true]) {
        const f = fixture({}, true),
          abort = new AbortController()
        f.setAdapter(() => new Promise(() => {}))
        const result = f.client
          .get('/fixture/read', { signal: abort.signal, requestBudgetMs: 1000 })
          .catch((error) => error)
        if (cancel) {
          await flush()
          abort.abort()
        } else await f.clock.advance(1000)
        assert.equal((await result).code, cancel ? 'ERR_CANCELED' : 'ERR_REQUEST_BUDGET')
        assert.equal(f.attempts.length, 1)
        assert.equal(f.clears(), 0)
        assert.equal(f.messages.length, cancel ? 0 : 1)
        assert.equal(f.clock.timers.size, 0)
      }
    }
  )
  await check(
    'GET total budget bounds attempt timeouts plus backoff at 30s and releases all timers',
    async () => {
      const f = fixture()
      f.setAdapter((c) => f.timed(c, c.timeout))
      const result = f.client.get('/fixture/read').catch((error) => error)
      await f.clock.advance(30000)
      assert.equal((await result).code, 'ERR_REQUEST_BUDGET')
      assert.deepEqual(
        f.attempts.map((c) => c.timeout),
        [15000, 14750]
      )
      assert.equal(f.clock.time, 30000)
      assert.equal(f.clock.timers.size, 0)
      assert.equal(f.clears(), 0)
      assert.equal(f.wakes(), 0)
      assert.ok(f.messages.every((s) => !/唤醒|冷启动|2-3/.test(s)))
    }
  )
  await check(
    'two short read retries recover through real response interceptors and keep budget identity',
    async () => {
      const f = fixture()
      f.setAdapter(async (c) => {
        if (f.attempts.length < 3) throw httpError(c, 503)
        return success(c)
      })
      const result = f.client.get('/fixture/read')
      await f.clock.advance(1000)
      assert.equal((await result).code, 200)
      assert.equal(f.attempts.length, 3)
      assert.ok(f.attempts.every((c) => c.__requestBudget === f.attempts[0].__requestBudget))
      assert.equal(f.clock.timers.size, 0)
    }
  )
  await check(
    'total deadline settles a pending adapter even when that adapter ignores AbortSignal',
    async () => {
      const f = fixture()
      f.setAdapter(() => new Promise(() => {}))
      const result = f.client.get('/fixture/read', { requestBudgetMs: 3000 }).catch((e) => e)
      await f.clock.advance(3000)
      assert.equal((await result).code, 'ERR_REQUEST_BUDGET')
      assert.equal(f.attempts.length, 1)
      assert.equal(f.clears(), 0)
      assert.equal(f.clock.timers.size, 0)
    }
  )
  await check(
    'caller cancellation also settles an adapter that ignores the signal without retry or toast',
    async () => {
      const f = fixture(),
        abort = new AbortController()
      f.setAdapter(() => new Promise(() => {}))
      const result = f.client.get('/fixture/read', { signal: abort.signal }).catch((e) => e)
      await flush()
      abort.abort()
      assert.equal((await result).code, 'ERR_CANCELED')
      assert.equal(f.attempts.length, 1)
      assert.equal(f.messages.length, 0)
      assert.equal(f.clock.timers.size, 0)
    }
  )
  for (const status of [429, 503]) {
    await check(`POST ${status} is sent once even with retryUnsafe opt-in`, async () => {
      const f = fixture()
      f.setAdapter(async (c) => {
        throw httpError(c, status)
      })
      await assert.rejects(
        f.client.post('/fixture/write', {}, { retryUnsafe: true }),
        (e) => e.response.status === status
      )
      assert.equal(f.attempts.length, 1)
      assert.equal(f.clock.timers.size, 0)
      assert.equal(f.wakes(), 0)
    })
  }
  await check(
    'actual auth.login applies 15s/30s and never automatically resends a timed-out session POST',
    async () => {
      const { login } = await server.ssrLoadModule('/src/api/auth.js')
      let calls = 0
      defaultClient.defaults.adapter = async (config) => {
        calls++
        assert.equal(config.timeout, 15000)
        assert.equal(config.__requestBudget.policy.budget, 30000)
        assert.equal(config.retry, false)
        assert.equal(config.url, '/auth/login')
        throw new AxiosError('fixture timeout', 'ECONNABORTED', config)
      }
      await assert.rejects(login({}), (e) => e.code === 'ECONNABORTED')
      assert.equal(calls, 1)
    }
  )
  await check(
    'cancellation during backoff cancels its timer, makes no second request and reports no failure toast',
    async () => {
      const f = fixture(),
        abort = new AbortController()
      f.setAdapter(async (c) => {
        throw networkError(c)
      })
      const result = f.client.get('/fixture/read', { signal: abort.signal }).catch((e) => e)
      await flush()
      abort.abort()
      await flush()
      assert.equal((await result).code, 'ERR_CANCELED')
      assert.equal(f.attempts.length, 1)
      assert.equal(f.messages.length, 1)
      assert.equal(f.clock.timers.size, 0)
      assert.equal(f.clears(), 0)
    }
  )
  await check(
    'cancellation while network adapter is pending aborts promptly without retry or clearing session',
    async () => {
      const f = fixture(),
        abort = new AbortController()
      f.setAdapter((c) => f.timed(c, c.timeout))
      const result = f.client.get('/fixture/read', { signal: abort.signal }).catch((e) => e)
      await flush()
      abort.abort()
      await flush()
      assert.equal((await result).code, 'ERR_CANCELED')
      assert.equal(f.attempts.length, 1)
      assert.equal(f.messages.length, 0)
      assert.equal(f.clears(), 0)
      assert.equal(f.clock.timers.size, 0)
    }
  )
  await check(
    'already canceled requests never reach session preparation or the adapter',
    async () => {
      const f = fixture(),
        abort = new AbortController()
      abort.abort()
      let preparations = 0
      f.store.ensureSession = async () => {
        preparations++
      }
      f.setAdapter(async (c) => success(c))
      await assert.rejects(
        f.client.get('/fixture/read', { signal: abort.signal }),
        (e) => e.code === 'ERR_CANCELED'
      )
      assert.equal(preparations, 0)
      assert.equal(f.attempts.length, 0)
      assert.equal(f.clock.timers.size, 0)
    }
  )
  await check(
    'session preparation is inside the request deadline and does not block forever or send after expiry',
    async () => {
      const f = fixture()
      f.store.ensureSession = () => new Promise(() => {})
      f.setAdapter(async (c) => success(c))
      const result = f.client.get('/fixture/read', { requestBudgetMs: 5000 }).catch((e) => e)
      await f.clock.advance(5000)
      assert.equal((await result).code, 'ERR_REQUEST_BUDGET')
      assert.equal(f.attempts.length, 0)
      assert.equal(f.clears(), 0)
      assert.equal(f.clock.timers.size, 0)
    }
  )
  await check(
    'slow session refresh reduces the following adapter timeout; budgets do not restart after preparation',
    async () => {
      const f = fixture()
      f.store.ensureSession = () => new Promise((resolve) => f.clock.setTimer(resolve, 25000))
      f.setAdapter(async (c) => success(c))
      const result = f.client.get('/fixture/read')
      await f.clock.advance(25000)
      assert.equal((await result).code, 200)
      assert.equal(f.attempts[0].timeout, 5000)
      assert.equal(f.clock.timers.size, 0)
    }
  )
  await check(
    'a retried read keeps its original Authorization even if a new session logs in during backoff',
    async () => {
      const f = fixture()
      let preparations = 0
      f.store.ensureSession = async () => {
        preparations++
      }
      f.setAdapter(async (c) => {
        if (f.attempts.length === 1) throw httpError(c, 503)
        assert.equal(c.headers.Authorization, 'Bearer fixture-session')
        throw httpError(c, 401)
      })
      const result = f.client.get('/fixture/private').catch((e) => e)
      await flush()
      f.store.token = 'fixture-new-session'
      await f.clock.advance(250)
      await result
      assert.equal(preparations, 1)
      assert.equal(f.clears(), 0)
      assert.equal(f.attempts.length, 2)
      assert.equal(f.clock.timers.size, 0)
    }
  )
  await check('oversized Retry-After cannot extend the deadline or replay writes', async () => {
    const f = fixture()
    f.setAdapter(async (c) => {
      throw httpError(c, 429, { 'retry-after': '120' })
    })
    await assert.rejects(f.client.get('/fixture/read'), (e) => e.response.status === 429)
    assert.equal(f.attempts.length, 1)
    assert.equal(f.clock.time, 0)
    assert.equal(f.clock.timers.size, 0)
  })
  await check('read Retry-After is honored inside the remaining budget', async () => {
    const f = fixture()
    f.setAdapter(async (c) => {
      if (f.attempts.length === 1) throw httpError(c, 429, { 'retry-after': '1' })
      return success(c)
    })
    const result = f.client.get('/fixture/read')
    await f.clock.advance(999)
    assert.equal(f.attempts.length, 1)
    await f.clock.advance(1)
    assert.equal((await result).code, 200)
    assert.equal(f.attempts.length, 2)
  })
  await check(
    'PDF blob retains a 60s attempt / 90s budget and can successfully finish after the ordinary 30s window',
    async () => {
      const f = fixture()
      f.setAdapter((c) => f.timed(c, 45000, 'fixture-binary'))
      const result = f.client.get('/fixture/download', { responseType: 'blob' })
      await f.clock.advance(45000)
      assert.equal((await result).data, 'fixture-binary')
      assert.equal(f.attempts[0].timeout, 60000)
      assert.equal(f.attempts[0].__requestBudget.policy.budget, 90000)
      assert.equal(f.attempts.length, 1)
      assert.equal(f.clock.timers.size, 0)
    }
  )
  await check(
    'explicit 20s observability timeout/retry:false remains intact and is not retried',
    async () => {
      const f = fixture()
      f.setAdapter(async (c) => {
        throw httpError(c, 503)
      })
      await assert.rejects(f.client.get('/fixture/observations', { timeout: 20000, retry: false }))
      assert.equal(f.attempts[0].timeout, 20000)
      assert.equal(f.attempts.length, 1)
    }
  )
  await check(
    'explicit long write timeout is respected while its caller budget can deliberately shorten one attempt',
    async () => {
      const f = fixture()
      f.setAdapter((c) => f.timed(c, c.timeout))
      const result = f.client
        .post('/fixture/long-write', {}, { timeout: 120000, requestBudgetMs: 5000 })
        .catch((e) => e)
      await f.clock.advance(5000)
      assert.equal((await result).code, 'ERR_REQUEST_BUDGET')
      assert.equal(f.attempts[0].timeout, 5000)
      assert.equal(f.attempts.length, 1)
    }
  )
  for (const business of [false, true]) {
    for (const switched of [false, true]) {
      await check(
        `${business ? 'business' : 'HTTP'} 401 clears only its original session (new session=${switched})`,
        async () => {
          const f = fixture()
          let release
          f.setAdapter(
            (c) =>
              new Promise((resolve, reject) => {
                release = () =>
                  business
                    ? resolve(success(c, { code: 401, message: 'fixture expired' }))
                    : reject(httpError(c, 401))
              })
          )
          const result = f.client.get('/fixture/private').catch((e) => e)
          await flush()
          if (switched) f.store.token = 'fixture-new-session'
          release()
          await result
          assert.equal(f.clears(), switched ? 0 : 1)
          assert.equal(f.attempts.length, 1)
          assert.equal(f.clock.timers.size, 0)
        }
      )
    }
  }
  await check(
    'default standard never warms services; Render warmup requires both explicit settings',
    async () => {
      for (const env of [
        {},
        { VITE_ENABLE_SERVICE_WARMUP: 'true' },
        { VITE_REQUEST_PROFILE: 'render' },
        { VITE_REQUEST_PROFILE: 'render', VITE_ENABLE_SERVICE_WARMUP: 'true' }
      ]) {
        const f = fixture(env)
        f.setAdapter(async (c) => {
          if (f.attempts.length === 1) throw httpError(c, 503)
          return success(c)
        })
        const result = f.client.get('/fixture/read')
        await f.clock.advance(env.VITE_REQUEST_PROFILE === 'render' ? 5000 : 250)
        await result
        assert.equal(
          f.wakes(),
          env.VITE_REQUEST_PROFILE === 'render' && env.VITE_ENABLE_SERVICE_WARMUP === 'true' ? 1 : 0
        )
        assert.equal(f.clock.timers.size, 0)
      }
    }
  )
  await check(
    'Render legacy timeout budget is explicit and remains bounded, with writes never replayed',
    async () => {
      const f = fixture({ VITE_REQUEST_PROFILE: 'render' })
      f.setAdapter((c) => f.timed(c, c.timeout))
      const result = f.client.get('/fixture/read').catch((e) => e)
      await f.clock.advance(240000)
      assert.equal((await result).code, 'ERR_REQUEST_BUDGET')
      assert.deepEqual(
        f.attempts.map((c) => c.timeout),
        [60000, 60000, 60000, 10000]
      )
      assert.equal(f.clock.timers.size, 0)
    }
  )
  console.log(`\nAxios拦截器检查 ${count} / ${count}`)
} finally {
  await server.close()
  delete globalThis.__requestPolicyStore
  delete globalThis.__requestPolicyMessage
}
