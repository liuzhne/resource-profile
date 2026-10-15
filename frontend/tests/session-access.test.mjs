// 真实 Pinia store / Axios 拦截器 + Vue Router 内存路由，不调用生产服务。
import assert from 'node:assert/strict'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createRoutes } from '../src/router/routes.js'
import { createAuthGuard, filterMenuRoutes, getHomePath } from '../src/router/access.js'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const savedStorage = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
const storage = new Map()
Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: {
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, String(value)),
    removeItem: (key) => storage.delete(key)
  }
})
const state = { auth: {}, navigation: [], messages: [] }
globalThis.__sessionAccessTest = state
const mocks = {
  auth: `
    export const login = (...args) => globalThis.__sessionAccessTest.auth.login(...args)
    export const getUserInfo = (...args) => globalThis.__sessionAccessTest.auth.getUserInfo(...args)
    export const refreshToken = (...args) => globalThis.__sessionAccessTest.auth.refreshToken(...args)
    export const logout = (...args) => globalThis.__sessionAccessTest.auth.logout(...args)
  `,
  router: `export default { replace: async (path) => {
    globalThis.__sessionAccessTest.navigation.push(path)
  } }`,
  messages: `
    export const ElMessage = Object.assign(() => ({ close() {} }), {
      error: (message) => globalThis.__sessionAccessTest.messages.push(message),
      warning: (message) => globalThis.__sessionAccessTest.messages.push(message)
    })
  `,
  warmup: `export const wakeServicesNow = () => {}`
}

const server = await createServer({
  root: frontendRoot,
  configFile: false,
  server: { middlewareMode: true, hmr: false, ws: false },
  optimizeDeps: { noDiscovery: true, include: [] },
  ssr: { noExternal: ['element-plus'] },
  resolve: { alias: { '@': path.join(frontendRoot, 'src') } },
  plugins: [
    {
      name: 'session-access-test-mocks',
      enforce: 'pre',
      resolveId(source, importer) {
        if (source === path.join(frontendRoot, 'src/api/auth')) return '\0session-test:auth'
        if (source === path.join(frontendRoot, 'src/router')) return '\0session-test:router'
        if (source === 'element-plus') return '\0session-test:messages'
        if (source === './warmup' && importer?.endsWith('/src/utils/request.js')) {
          return '\0session-test:warmup'
        }
      },
      load(id) {
        if (id.startsWith('\0session-test:')) return mocks[id.split(':')[1]]
      }
    }
  ]
})

let count = 0
const check = async (name, run) => {
  await run()
  count++
  console.log(`  ✓ ${name}`)
}

try {
  const { useUserStore } = await server.ssrLoadModule('/src/store/modules/user.js')
  const { default: request } = await server.ssrLoadModule('/src/utils/request.js')
  const actualAuth = await server.ssrLoadModule('/src/api/auth.js')
  const newStore = () => {
    storage.clear()
    state.navigation.length = 0
    state.messages.length = 0
    storage.set('token', 'test-session')
    storage.set('userInfo', JSON.stringify({ roles: ['teacher'] }))
    setActivePinia(createPinia())
    return useUserStore()
  }

  await check('主动退出携带会话调用后端，成功前保留状态，成功后清缓存并到登录页', async () => {
    const store = newStore()
    let release
    let calls = 0
    state.auth.logout = actualAuth.logout
    request.defaults.adapter = (config) => {
      calls++
      assert.equal(config.url, '/auth/logout')
      assert.equal(config.method, 'post')
      assert.equal(config.retry, false)
      assert.equal(config.headers.Authorization, 'Bearer test-session')
      assert.equal(store.token, 'test-session')
      assert.equal(storage.get('token'), 'test-session')
      return new Promise((resolve) => {
        release = () => resolve({ data: { code: 200 }, status: 200, config, headers: {} })
      })
    }
    const first = store.logoutAction()
    const duplicate = store.logoutAction()
    await new Promise((resolve) => setTimeout(resolve, 0))
    assert.equal(calls, 1)
    assert.equal(store.token, 'test-session')
    assert.deepEqual(state.navigation, [])
    release()
    await Promise.all([first, duplicate])
    assert.equal(store.token, '')
    assert.equal(store.userInfo, null)
    assert.equal(storage.has('token'), false)
    assert.equal(storage.has('userInfo'), false)
    assert.deepEqual(state.navigation, ['/login'])
  })

  await check('后端注销断网失败保留会话并可重试，不伪称退出成功', async () => {
    const store = newStore()
    state.auth.logout = async () => {
      throw new Error('test network failure')
    }
    await assert.rejects(store.logoutAction(), /test network failure/)
    assert.equal(store.token, 'test-session')
    assert.equal(storage.get('token'), 'test-session')
    assert.deepEqual(state.navigation, [])
    state.auth.logout = async () => {}
    await store.logoutAction()
    assert.equal(store.token, '')
  })

  await check('旧注销请求返回时不会清除后来登录的新会话', async () => {
    const store = newStore()
    let release
    state.auth.logout = () =>
      new Promise((resolve) => {
        release = resolve
      })
    const previousLogout = store.logoutAction()
    state.auth.login = async () => ({ data: { token: 'new-session' } })
    state.auth.getUserInfo = async () => ({ data: { roles: ['student'], userType: 2 } })
    await store.loginAction({ username: 'test-student', password: 'test-password', remember: true })
    release()
    await previousLogout
    assert.equal(store.token, 'new-session')
    assert.equal(storage.get('token'), 'new-session')
    assert.deepEqual(state.navigation, ['/student-mental/list'])
  })

  for (const kind of ['http', 'business']) {
    await check(`${kind} 401 仅清本地会话，绝不递归调用注销接口`, async () => {
      const store = newStore()
      let revocations = 0
      state.auth.logout = async () => {
        revocations++
      }
      request.defaults.adapter = async (config) => {
        assert.equal(config.headers.Authorization, 'Bearer test-session')
        if (kind === 'business') {
          return { data: { code: 401, message: 'test expired' }, status: 200, config, headers: {} }
        }
        throw Object.assign(new Error('test expired'), { config, response: { status: 401 } })
      }
      await assert.rejects(request.get('/protected', { retry: false }), (error) => {
        if (kind === 'business') assert.equal(error.code, 401)
        else assert.equal(error.response?.status, 401)
        return true
      })
      assert.equal(revocations, 0)
      assert.equal(store.token, '')
      assert.equal(storage.has('userInfo'), false)
      assert.deepEqual(state.navigation, ['/login'])
    })
  }

  await check('已过期会话的主动退出只发一次请求并正常完成', async () => {
    const store = newStore()
    let calls = 0
    request.defaults.adapter = async (config) => {
      calls++
      throw Object.assign(new Error('test expired'), { config, response: { status: 401 } })
    }
    state.auth.logout = actualAuth.logout
    await store.logoutAction()
    assert.equal(calls, 1)
    assert.equal(store.token, '')
    assert.deepEqual(state.navigation, ['/login'])
  })

  for (const kind of ['http', 'business', 'logout']) {
    await check(`旧 ${kind} 请求的延迟 401 不会清理新登录的会话`, async () => {
      const store = newStore()
      let rejectOld
      request.defaults.adapter = (config) =>
        new Promise((resolve, reject) => {
          assert.equal(config.headers.Authorization, 'Bearer test-session')
          rejectOld = () => {
            if (kind === 'business') {
              resolve({
                data: { code: 401, message: 'test expired' },
                status: 200,
                config,
                headers: {}
              })
            } else {
              reject(
                Object.assign(new Error('test expired'), { config, response: { status: 401 } })
              )
            }
          }
        })
      state.auth.logout = actualAuth.logout
      const oldRequest =
        kind === 'logout' ? store.logoutAction() : request.get('/protected', { retry: false })
      await new Promise((resolve) => setTimeout(resolve, 0))
      state.auth.login = async () => ({ data: { token: 'new-session' } })
      state.auth.getUserInfo = async () => ({ data: { roles: ['student'] } })
      await store.loginAction({ username: 'test-user', password: 'test-password', remember: true })
      rejectOld()
      if (kind === 'logout') await oldRequest
      else await assert.rejects(oldRequest)
      assert.equal(store.token, 'new-session')
      assert.equal(storage.get('token'), 'new-session')
      assert.deepEqual(state.navigation, ['/student-mental/list'])
    })
  }

  await check('注销 429 不自动重试，旧注销也不会携带后来登录的新令牌', async () => {
    const store = newStore()
    let calls = 0
    let rejectOld
    request.defaults.adapter = (config) =>
      new Promise((_resolve, reject) => {
        calls++
        assert.equal(config.headers.Authorization, 'Bearer test-session')
        assert.equal(config.retry, false)
        rejectOld = () =>
          reject(Object.assign(new Error('test limit'), { config, response: { status: 429 } }))
      })
    state.auth.logout = actualAuth.logout
    const oldLogout = store.logoutAction()
    state.auth.login = async () => ({ data: { token: 'new-session' } })
    state.auth.getUserInfo = async () => ({ data: { roles: ['student'] } })
    await store.loginAction({ username: 'test-user', password: 'test-password', remember: true })
    rejectOld()
    await assert.rejects(oldLogout, /test limit/)
    assert.equal(calls, 1)
    assert.equal(store.token, 'new-session')
    assert.deepEqual(state.navigation, ['/student-mental/list'])
  })

  await check('新登录后的主动退出独立撤销新会话，不复用仍挂起的旧注销', async () => {
    const store = newStore()
    const releases = new Map()
    let calls = 0
    request.defaults.adapter = (config) =>
      new Promise((resolve) => {
        calls++
        releases.set(config.headers.Authorization, () =>
          resolve({ data: { code: 200 }, status: 200, config, headers: {} })
        )
      })
    state.auth.logout = actualAuth.logout
    const previousLogout = store.logoutAction()
    state.auth.login = async () => ({ data: { token: 'new-session' } })
    state.auth.getUserInfo = async () => ({ data: { roles: ['student'] } })
    await store.loginAction({ username: 'test-user', password: 'test-password', remember: true })
    const currentLogout = store.logoutAction()
    await new Promise((resolve) => setTimeout(resolve, 0))
    assert.equal(calls, 2)
    releases.get('Bearer test-session')()
    await previousLogout
    assert.equal(store.token, 'new-session')
    assert.deepEqual(state.navigation, ['/student-mental/list'])
    releases.get('Bearer new-session')()
    await currentLogout
    assert.equal(store.token, '')
    assert.deepEqual(state.navigation, ['/student-mental/list', '/login'])
  })

  await check('学生真实登录落地问卷页；管理员和教师保留看板入口', async () => {
    for (const role of ['student', 'admin', 'teacher']) {
      const store = newStore()
      state.auth.login = async () => ({ data: { token: 'test-new-session' } })
      state.auth.getUserInfo = async () => ({ data: { roles: [role] } })
      await store.loginAction({ username: 'test-user', password: 'test-password', remember: true })
      assert.deepEqual(state.navigation, [getHomePath([role])])
    }
  })

  for (const outcome of ['success', 'http401', 'business401']) {
    await check(`旧 userInfo 延迟 ${outcome} 不覆写新角色，不从 guard 清除新会话`, async () => {
      const store = newStore()
      store.userInfo = null
      let finishOld
      let oldCalls = 0
      state.auth.getUserInfo = actualAuth.getUserInfo
      request.defaults.adapter = (config) => {
        assert.equal(config.url, '/auth/userInfo')
        if (config.headers.Authorization === 'Bearer new-session') {
          return Promise.resolve({
            data: { code: 200, data: { roles: ['student'] } },
            status: 200,
            config,
            headers: {}
          })
        }
        assert.equal(config.headers.Authorization, 'Bearer test-session')
        oldCalls++
        return new Promise((resolve, reject) => {
          finishOld = () => {
            if (outcome === 'http401') {
              reject(
                Object.assign(new Error('test expired'), { config, response: { status: 401 } })
              )
            } else {
              resolve({
                data:
                  outcome === 'success'
                    ? { code: 200, data: { roles: ['admin'] } }
                    : { code: 401, message: 'test expired' },
                status: 200,
                config,
                headers: {}
              })
            }
          }
        })
      }
      const pendingGuard = createAuthGuard(() => store)({
        path: '/dashboard',
        meta: { roles: ['admin', 'teacher'] }
      })
      await new Promise((resolve) => setTimeout(resolve, 0))
      state.auth.login = async () => ({ data: { token: 'new-session' } })
      await store.loginAction({ username: 'test-user', password: 'test-password', remember: true })
      finishOld()
      assert.equal(await pendingGuard, '/student-mental/list')
      assert.equal(oldCalls, 1)
      assert.equal(store.token, 'new-session')
      assert.deepEqual(store.userRoles, ['student'])
      assert.deepEqual(JSON.parse(storage.get('userInfo')).roles, ['student'])
      assert.deepEqual(state.navigation, ['/student-mental/list'])
    })
  }

  await check('会话切换后的 guard 等待新身份加载，复用新会话请求，避免采用旧缓存', async () => {
    const store = newStore()
    store.userInfo = null
    const finishes = new Map()
    const calls = new Map()
    state.auth.getUserInfo = actualAuth.getUserInfo
    request.defaults.adapter = (config) =>
      new Promise((resolve) => {
        const authorization = config.headers.Authorization
        calls.set(authorization, (calls.get(authorization) || 0) + 1)
        finishes.set(authorization, (roles) =>
          resolve({
            data: { code: 200, data: { roles } },
            status: 200,
            config,
            headers: {}
          })
        )
      })
    const pendingGuard = createAuthGuard(() => store)({
      path: '/dashboard',
      meta: { roles: ['admin', 'teacher'] }
    })
    await new Promise((resolve) => setTimeout(resolve, 0))
    state.auth.login = async () => ({ data: { token: 'new-session' } })
    const newLogin = store.loginAction({
      username: 'test-user',
      password: 'test-password',
      remember: true
    })
    await new Promise((resolve) => setTimeout(resolve, 0))
    await Promise.resolve()
    finishes.get('Bearer test-session')(['admin'])
    await new Promise((resolve) => setTimeout(resolve, 0))
    assert.equal(store.userInfo, null)
    finishes.get('Bearer new-session')(['student'])
    await newLogin
    assert.equal(await pendingGuard, '/student-mental/list')
    assert.equal(calls.get('Bearer new-session'), 1)
    assert.deepEqual(store.userRoles, ['student'])
    assert.deepEqual(state.navigation, ['/student-mental/list'])
  })

  for (const outcome of ['success', 'http401']) {
    await check(
      `较早登录的 userInfo 延迟 ${outcome} 不改变新登录角色或触发旧登录跳转`,
      async () => {
        const store = newStore()
        let finishOld
        state.auth.getUserInfo = actualAuth.getUserInfo
        state.auth.login = async () => ({ data: { token: 'previous-login' } })
        request.defaults.adapter = (config) => {
          if (config.headers.Authorization === 'Bearer new-session') {
            return Promise.resolve({
              data: { code: 200, data: { roles: ['student'] } },
              status: 200,
              config,
              headers: {}
            })
          }
          assert.equal(config.headers.Authorization, 'Bearer previous-login')
          return new Promise((resolve, reject) => {
            finishOld = () => {
              if (outcome === 'http401') {
                reject(
                  Object.assign(new Error('test expired'), { config, response: { status: 401 } })
                )
              } else {
                resolve({
                  data: { code: 200, data: { roles: ['teacher'] } },
                  status: 200,
                  config,
                  headers: {}
                })
              }
            }
          })
        }
        const previousLogin = store.loginAction({
          username: 'test-old-user',
          password: 'test-password',
          remember: true
        })
        await Promise.resolve()
        await Promise.resolve()
        await Promise.resolve()
        state.auth.login = async () => ({ data: { token: 'new-session' } })
        await store.loginAction({
          username: 'test-new-user',
          password: 'test-password',
          remember: true
        })
        finishOld()
        await previousLogin
        assert.equal(store.token, 'new-session')
        assert.deepEqual(store.userRoles, ['student'])
        assert.deepEqual(state.navigation, ['/student-mental/list'])
      }
    )
  }

  const memoryRoutes = (routes) =>
    routes.map((route) => ({
      ...route,
      ...(route.component ? { component: {} } : {}),
      ...(route.children ? { children: memoryRoutes(route.children) } : {})
    }))
  const routeFor = (store) => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: memoryRoutes(createRoutes(true))
    })
    router.beforeEach(createAuthGuard(() => store))
    return router
  }

  await check('学生直达 /、/dashboard、教/学生列表和管理员子页均重定向到允许的页面', async () => {
    const router = routeFor({
      token: 'test-session',
      userInfo: { roles: ['student'], userType: 2 }
    })
    for (const target of [
      '/',
      '/dashboard',
      '/teacher/list',
      '/student/list',
      '/admin/users',
      '/admin/trace',
      '/mental/overview',
      '/agent/warning',
      '/login'
    ]) {
      await router.push(target)
      assert.equal(router.currentRoute.value.path, '/student-mental/list')
    }
    await router.push('/profile')
    assert.equal(router.currentRoute.value.path, '/profile')
  })

  await check('角色不够时按 home 重定向；未知角色只进个人页，不按 userType 提权', async () => {
    for (const role of ['teacher', 'counselor', 'academic_advisor', 'psychologist']) {
      const router = routeFor({ token: 'test-session', userInfo: { roles: [role] } })
      await router.push('/admin/users')
      assert.equal(router.currentRoute.value.path, '/dashboard')
    }
    const router = routeFor({ token: 'test-session', userInfo: { roles: [], userType: 0 } })
    await router.push('/dashboard')
    assert.equal(router.currentRoute.value.path, '/profile')
  })

  await check('教师保留问卷题目查看与完成情况入口，学生仍被拦截', async () => {
    for (const role of ['teacher', 'counselor', 'academic_advisor', 'psychologist', 'admin']) {
      const router = routeFor({ token: 'test-session', userInfo: { roles: [role] } })
      for (const target of ['/mental/questionnaire/design/1', '/mental/questionnaire/result/1']) {
        await router.push(target)
        assert.equal(router.currentRoute.value.path, target)
      }
    }
  })

  await check('刷新缺失用户缓存时调用正确的 getUserInfoAction 后再检查角色', async () => {
    const store = {
      token: 'test-session',
      userInfo: null,
      loads: 0,
      async getUserInfoAction() {
        this.loads++
        this.userInfo = { roles: ['student'] }
      }
    }
    const router = routeFor(store)
    await router.push('/dashboard')
    assert.equal(store.loads, 1)
    assert.equal(router.currentRoute.value.path, '/student-mental/list')
  })

  await check('身份加载失败和未登录直达业务页都回登录页，不循环请求', async () => {
    const store = {
      token: 'test-session',
      userInfo: null,
      loads: 0,
      clears: 0,
      async getUserInfoAction() {
        this.loads++
        throw new Error('test expired')
      },
      clearSession(options) {
        this.clears++
        assert.equal(options.redirect, false)
        this.token = ''
      }
    }
    const router = routeFor(store)
    await router.push('/dashboard')
    assert.equal(router.currentRoute.value.path, '/login')
    assert.equal(store.loads, 1)
    assert.equal(store.clears, 1)
    await router.push('/student-mental/list')
    assert.equal(router.currentRoute.value.path, '/login')
    assert.equal(store.loads, 1)
  })

  await check(
    '菜单按真实 roles 递归过滤，学生没有看板/管理入口，教师没有写心理问卷入口',
    async () => {
      const children = createRoutes(true).find((route) => route.path === '/').children
      const studentMenu = filterMenuRoutes(children, ['student'])
      assert.deepEqual(
        studentMenu.map((route) => route.path),
        ['/student-mental', '/profile']
      )
      const teacherMenu = filterMenuRoutes(children, ['teacher'])
      assert.equal(
        teacherMenu.some((route) => route.path === '/dashboard'),
        true
      )
      assert.equal(
        teacherMenu.some((route) => route.path === '/admin'),
        false
      )
      const guardedChildren = filterMenuRoutes(
        [
          {
            path: '/management',
            meta: { roles: ['teacher', 'admin'] },
            children: [
              { path: '/management/write', meta: { roles: ['admin'] } },
              { path: '/management/read' }
            ]
          }
        ],
        ['teacher']
      )
      assert.deepEqual(
        guardedChildren[0].children.map((route) => route.path),
        ['/management/read']
      )
    }
  )

  await check('权限目录进一步限制菜单与路由，管理员保持维护入口', async () => {
    const routes = createRoutes(true).find((route) => route.path === '/').children
    const menus = filterMenuRoutes(routes, ['teacher'], ['student:read'])
    assert.equal(
      menus.some((route) => route.path === '/dashboard'),
      false
    )
    assert.equal(
      menus.some((route) => route.path === '/student'),
      true
    )
    assert.equal(
      menus.some((route) => route.path === '/mental'),
      false
    )
    const store = {
      token: 'synthetic',
      userInfo: { roles: ['teacher'], permissions: ['student:read'] }
    }
    assert.equal(
      await createAuthGuard(() => store)({ path: '/dashboard', meta: { roles: ['teacher'] } }),
      '/profile'
    )
    assert.equal(getHomePath(['admin'], []), '/dashboard')
  })
  await check('未记住的登录仅使用sessionStorage，记住登录使用持久访问状态', async () => {
    const temporary = new Map()
    const previous = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage')
    Object.defineProperty(globalThis, 'sessionStorage', {
      configurable: true,
      value: {
        getItem: (k) => temporary.get(k) ?? null,
        setItem: (k, v) => temporary.set(k, String(v)),
        removeItem: (k) => temporary.delete(k)
      }
    })
    try {
      const store = newStore()
      state.auth.login = async () => ({ data: { token: 'ephemeral-session' } })
      state.auth.getUserInfo = async () => ({ data: { roles: ['student'] } })
      await store.loginAction({
        username: 'synthetic-user',
        password: 'synthetic-password',
        remember: false
      })
      assert.equal(storage.has('token'), false)
      assert.equal(temporary.get('token'), 'ephemeral-session')
      await store.clearSession({ redirect: false })
      assert.equal(temporary.has('token'), false)
      state.auth.login = async () => ({ data: { token: 'remembered-session' } })
      await store.loginAction({
        username: 'synthetic-user',
        password: 'synthetic-password',
        remember: true
      })
      assert.equal(storage.get('token'), 'remembered-session')
      assert.equal(temporary.has('token'), false)
    } finally {
      if (previous) Object.defineProperty(globalThis, 'sessionStorage', previous)
      else delete globalThis.sessionStorage
    }
  })
  await check('过期记住会话并发刷新一次，较旧刷新结果不覆盖新登录', async () => {
    const store = newStore()
    const expired =
      'header.' +
      Buffer.from(JSON.stringify({ exp: Math.floor(Date.now() / 1000) - 1 })).toString(
        'base64url'
      ) +
      '.signature'
    state.auth.login = async () => ({ data: { token: expired } })
    state.auth.getUserInfo = async () => ({ data: { roles: ['student'] } })
    await store.loginAction({
      username: 'synthetic',
      password: 'synthetic-password',
      remember: true
    })
    let release,
      calls = 0
    state.auth.refreshToken = () => {
      calls++
      return new Promise((resolve) => {
        release = resolve
      })
    }
    const first = store.ensureSession(),
      second = store.ensureSession()
    assert.equal(calls, 1)
    state.auth.login = async () => ({ data: { token: 'replacement-session' } })
    await store.loginAction({
      username: 'replacement',
      password: 'synthetic-password',
      remember: true
    })
    release({ data: { token: 'obsolete-refresh' } })
    await Promise.all([first, second])
    assert.equal(store.token, 'replacement-session')
  })
  console.log(`\n通过 ${count} / ${count}`)
} finally {
  await server.close()
  if (savedStorage === undefined) delete globalThis.localStorage
  else Object.defineProperty(globalThis, 'localStorage', savedStorage)
  delete globalThis.__sessionAccessTest
}
