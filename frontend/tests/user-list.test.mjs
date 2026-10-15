// 真实只读 API 模块与列表状态逻辑；不访问生产、不调用写操作。
import assert from 'node:assert/strict'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const state = { calls: [], respond: null }
globalThis.__userListTest = state
const server = await createServer({
  root,
  configFile: false,
  server: { middlewareMode: true, hmr: false, ws: false },
  optimizeDeps: { noDiscovery: true, include: [] },
  resolve: { alias: { '@': path.join(root, 'src') } },
  plugins: [
    {
      name: 'user-list-test-request',
      enforce: 'pre',
      resolveId(source) {
        if (source === path.join(root, 'src/utils/request')) return '\0user-list-test:request'
      },
      load(id) {
        if (id === '\0user-list-test:request') {
          return `export default { get: (url, config) => {
          const state = globalThis.__userListTest
          state.calls.push({ url, params: { ...config.params } })
          return state.respond(url, config)
        } }`
        }
      }
    }
  ]
})

let count = 0
const check = async (name, run) => {
  state.calls.length = 0
  await run()
  count++
  console.log(`  ✓ ${name}`)
}
const page = (records, total) => ({ data: { records, total } })

try {
  const { useUserList } = await server.ssrLoadModule('/src/composables/useUserList.js')
  await check('首次加载使用 GET /user/list；显示服务端 total 而不是本页记录数', async () => {
    const model = useUserList()
    state.respond = async () =>
      page(
        [
          { id: 1, username: 'test-a' },
          { id: 2, username: 'test-b' }
        ],
        '17'
      )
    await model.fetchList()
    assert.deepEqual(state.calls, [
      {
        url: '/user/list',
        params: { page: 1, size: 10, username: undefined, status: undefined }
      }
    ])
    assert.equal(model.total.value, 17)
    assert.equal(model.userList.value.length, 2)
    assert.equal(model.rangeStart.value, 1)
    assert.equal(model.rangeEnd.value, 2)
    assert.equal(model.loading.value, false)
    assert.equal(model.loadError.value, false)
  })

  await check('查询回到第一页，用户名去空格、禁用状态0保持为有效筛选', async () => {
    const model = useUserList()
    model.currentPage.value = 3
    model.searchForm.username = ' test-account '
    model.searchForm.status = 0
    state.respond = async () => page([{ id: 8, username: 'test-account', status: 0 }], 1)
    await model.handleSearch()
    assert.deepEqual(state.calls[0].params, {
      page: 1,
      size: 10,
      username: 'test-account',
      status: 0
    })
    assert.equal(model.total.value, 1)
    assert.equal(model.currentPage.value, 1)
  })

  await check('分页与每页条数按真实接口查询；改变条数回第一页且不重复请求', async () => {
    const model = useUserList()
    state.respond = async () => page([{ id: 22, username: 'test-page' }], 31)
    await model.handlePageChange(3)
    assert.deepEqual(state.calls[0].params, {
      page: 3,
      size: 10,
      username: undefined,
      status: undefined
    })
    assert.equal(model.rangeStart.value, 21)
    assert.equal(model.rangeEnd.value, 21)
    await model.handleSizeChange(20)
    assert.equal(model.currentPage.value, 1)
    assert.equal(model.pageSize.value, 20)
    assert.equal(state.calls[1].params.page, 1)
    assert.equal(state.calls[1].params.size, 20)
    await model.handlePageChange(1)
    await model.handleSizeChange(20)
    assert.equal(state.calls.length, 2)
  })

  await check('重置重新加载无筛选第一页，合法空结果显示total=0且不报错', async () => {
    const model = useUserList()
    model.searchForm.username = 'test-filter'
    model.searchForm.status = 1
    model.currentPage.value = 4
    state.respond = async () => page([], 0)
    await model.handleReset()
    assert.deepEqual(state.calls[0].params, {
      page: 1,
      size: 10,
      username: undefined,
      status: undefined
    })
    assert.equal(model.total.value, 0)
    assert.equal(model.rangeStart.value, 0)
    assert.equal(model.rangeEnd.value, 0)
    assert.equal(model.loadError.value, false)
  })

  await check('请求失败清掉旧数据并显示失败状态，重试恢复真实total', async () => {
    const model = useUserList()
    state.respond = async () => page([{ id: 1 }], 3)
    await model.fetchList()
    state.respond = async () => {
      throw new Error('test offline')
    }
    await model.fetchList()
    assert.equal(model.loadError.value, true)
    assert.equal(model.total.value, 0)
    assert.deepEqual(model.userList.value, [])
    assert.equal(model.loading.value, false)
    state.respond = async () => page([{ id: 2 }], 5)
    await model.fetchList()
    assert.equal(model.loadError.value, false)
    assert.equal(model.total.value, 5)
  })

  await check('total小于当前记录数的异常响应不伪造计数，转失败状态', async () => {
    const model = useUserList()
    state.respond = async () => page([{ id: 1 }, { id: 2 }, { id: 3 }], 0)
    await model.fetchList()
    assert.equal(model.loadError.value, true)
    assert.deepEqual(model.userList.value, [])
  })

  for (const outcome of ['success', 'failure']) {
    await check(`旧查询延迟 ${outcome} 不覆写新查询的records/total/加载状态`, async () => {
      const model = useUserList()
      let finishOld
      let finishNew
      state.respond = () =>
        new Promise((resolve, reject) => {
          finishOld = () =>
            outcome === 'success'
              ? resolve(page([{ id: 1 }], 30))
              : reject(new Error('test old error'))
        })
      const oldQuery = model.fetchList()
      state.respond = () =>
        new Promise((resolve) => {
          finishNew = () => resolve(page([{ id: 2 }], 3))
        })
      model.searchForm.username = 'test-latest'
      const newQuery = model.handleSearch()
      finishOld()
      await oldQuery
      assert.equal(model.loading.value, true)
      assert.equal(model.loadError.value, false)
      assert.deepEqual(model.userList.value, [])
      finishNew()
      await newQuery
      assert.equal(model.loading.value, false)
      assert.equal(model.total.value, 3)
      assert.equal(model.userList.value[0].id, 2)
    })
  }
  console.log(`\n通过 ${count} / ${count}`)
} finally {
  await server.close()
  delete globalThis.__userListTest
}
