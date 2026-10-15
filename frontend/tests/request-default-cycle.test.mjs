// Native ESM evaluates the real user -> auth -> request -> user cycle.
// Only browser-only router/environment boundaries are adapted; none of the three modules is mocked.
import assert from 'node:assert/strict'
import { mkdtemp, mkdir, readFile, writeFile, symlink, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { createPinia, setActivePinia, getActivePinia } from 'pinia'

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const temporary = await mkdtemp(join(tmpdir(), 'educare-default-request-cycle-'))
const storageDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'localStorage')
const sessionDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage')
const originalFetch = globalThis.fetch
let networkCalls = 0
const storage = new Map()
const memoryStorage = {
  getItem: (key) => storage.get(key) ?? null,
  setItem: (key, value) => storage.set(key, String(value)),
  removeItem: (key) => storage.delete(key)
}
Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: memoryStorage })
Object.defineProperty(globalThis, 'sessionStorage', { configurable: true, value: memoryStorage })
globalThis.fetch = () => {
  networkCalls++
  throw new Error('OFFLINE_TEST_NETWORK_FORBIDDEN')
}
setActivePinia(undefined)

const environment = '({VITE_REQUEST_PROFILE:"standard",VITE_ENABLE_SERVICE_WARMUP:"false"})'
const adapt = (source) =>
  source
    .replaceAll('@/store/modules/user', './user.js')
    .replaceAll('@/api/auth', './auth.js')
    .replaceAll('@/utils/request', './request.js')
    .replaceAll('@/utils/retry', './retry.js')
    .replaceAll('@/router/access', './access.js')
    .replaceAll('@/router', './router.js')
    .replaceAll("'./retry'", "'./retry.js'")
    .replaceAll("'./warmup'", "'./warmup.js'")
    .replaceAll("'./prewarm'", "'./prewarm.js'")
    .replaceAll('import.meta.env', environment)

const input = {
  'user.js': await readFile(join(frontend, 'src/store/modules/user.js'), 'utf8'),
  'auth.js': await readFile(join(frontend, 'src/api/auth.js'), 'utf8'),
  'request.js': await readFile(join(frontend, 'src/utils/request.js'), 'utf8'),
  'retry.js': await readFile(join(frontend, 'src/utils/retry.js'), 'utf8'),
  'warmup.js': await readFile(join(frontend, 'src/utils/warmup.js'), 'utf8'),
  'prewarm.js': await readFile(join(frontend, 'src/utils/prewarm.js'), 'utf8'),
  'access.js': await readFile(join(frontend, 'src/router/access.js'), 'utf8')
}
const scenario = async (name, requestSource) => {
  const directory = join(temporary, name)
  await mkdir(directory)
  await writeFile(join(directory, 'package.json'), '{"type":"module"}')
  await symlink(join(frontend, 'node_modules'), join(directory, 'node_modules'), 'dir')
  for (const [file, source] of Object.entries(input))
    await writeFile(join(directory, file), adapt(file === 'request.js' ? requestSource : source))
  // createWebHistory needs a browser; retaining the back edge still preserves native ESM order.
  await writeFile(
    join(directory, 'router.js'),
    'import { useUserStore } from "./user.js"; export default {replace:async()=>{}}; export const deferredStore=()=>useUserStore();'
  )
  return (file) => pathToFileURL(join(directory, file)).href
}

try {
  assert.ok(input['request.js'].includes('getUserStore = () => useUserStore(),'))
  const eager = input['request.js'].replace(
    'getUserStore = () => useUserStore(),',
    'getUserStore = useUserStore,'
  )
  const broken = await scenario('eager-default-negative-control', eager)
  await assert.rejects(
    import(broken('user.js')),
    (error) => error instanceof ReferenceError && /before initialization/.test(error.message)
  )
  console.log('  ✓ real eager default negative control fails native ESM with TDZ')

  const fixed = await scenario('lazy-default-production-source', input['request.js'])
  const user = await import(fixed('user.js'))
  const { default: request } = await import(fixed('request.js'))
  const { getUserInfo } = await import(fixed('auth.js'))
  assert.equal(getActivePinia(), undefined)
  assert.equal(typeof user.useUserStore, 'function')
  console.log('  ✓ actual default request imports through the full cycle before Pinia is installed')

  setActivePinia(createPinia())
  const store = user.useUserStore()
  store.token = 'offline-fixture-session'
  let adapterCalls = 0
  request.defaults.adapter = async (config) => {
    adapterCalls++
    assert.equal(config.headers.Authorization, 'Bearer offline-fixture-session')
    assert.equal(config.timeout <= 15000, true)
    return { status: 200, data: { code: 200, data: {} }, config, headers: {} }
  }
  await request.get('/offline-fixture/read')
  await getUserInfo('offline-fixture-session')
  assert.equal(adapterCalls, 2)
  assert.equal(networkCalls, 0)
  console.log(
    '  ✓ real default client resolves the installed store lazily and actual auth uses that same client'
  )
  console.log('\nNative default module cycle checks 3 / 3')
} finally {
  setActivePinia(undefined)
  if (storageDescriptor) Object.defineProperty(globalThis, 'localStorage', storageDescriptor)
  else delete globalThis.localStorage
  if (sessionDescriptor) Object.defineProperty(globalThis, 'sessionStorage', sessionDescriptor)
  else delete globalThis.sessionStorage
  globalThis.fetch = originalFetch
  await rm(temporary, { recursive: true, force: true })
}
