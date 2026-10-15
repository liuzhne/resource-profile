// Real Vue template + reactive request state; all data synthetic and requests stubbed offline.
/* eslint-disable vue/one-component-per-file -- small SSR component stubs exercise the real template */
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse, compileTemplate } from '@vue/compiler-sfc'
import { createServer } from 'vite'
import { createSSRApp, h, provide, inject, effectScope } from 'vue'
import { renderToString } from 'vue/server-renderer'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = await fs.readFile(path.join(root, 'src/views/admin/trace.vue'), 'utf8')
const { descriptor } = parse(source)
const compiled = compileTemplate({
  source: descriptor.template.content,
  filename: 'trace.vue',
  id: 'observation-test',
  ssr: true,
  ssrCssVars: []
})
assert.deepEqual(compiled.errors, [])
const requestState = { calls: [], respond: null }
globalThis.__observationTest = requestState
const server = await createServer({
  root,
  configFile: false,
  server: { middlewareMode: true, hmr: false, ws: false },
  optimizeDeps: { noDiscovery: true, include: [] },
  resolve: { alias: { '@': path.join(root, 'src') } },
  plugins: [
    {
      name: 'observation-offline',
      enforce: 'pre',
      resolveId(id) {
        if (id === '/observation-template.mjs') return '\0observation-template'
        if (id === path.join(root, 'src/utils/request')) return '\0observation-request'
      },
      load(id) {
        if (id === '\0observation-template') return compiled.code
        if (id === '\0observation-request')
          return `export default { get: (url, config) => {
        const state = globalThis.__observationTest
        state.requestOptions = { timeout: config.timeout, retry: config.retry }
        state.calls.push({ url, params: { ...config.params } })
        return state.respond(config.params)
      } }`
      }
    }
  ]
})
const fixedNow = () => new Date('2026-10-08T03:00:00.000Z')
const record = (id, extra = {}) => ({
  id,
  traceId: 'synthetic-trace',
  name: 'llm.chat',
  type: 'GENERATION',
  startTime: '2026-10-08T02:00:00Z',
  endTime: '2026-10-08T02:00:01.5Z',
  model: 'synthetic-model',
  inputTokens: 12,
  outputTokens: 8,
  totalTokens: 20,
  latencyMs: 1500,
  level: 'DEFAULT',
  ...extra
})
const page = (params, records = [], nextCursor = null) => ({
  data: {
    records,
    nextCursor,
    hasMore: nextCursor != null,
    from: params.from,
    to: params.to
  }
})
const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
let count = 0
const check = async (name, run) => {
  requestState.calls.length = 0
  await run()
  ++count
  console.log(`  ✓ ${name}`)
}

try {
  const { ssrRender } = await server.ssrLoadModule('/observation-template.mjs')
  const helpers = await server.ssrLoadModule('/src/views/admin/observationState.js')
  const { useObservations } = helpers
  const render = async (model, extra = {}) => {
    const app = createSSRApp({
      setup: () => ({ ...model, ...helpers, langfuseUrl: '', ...extra }),
      ssrRender
    })
    app.config.warnHandler = () => {}
    for (const name of [
      'ElCard',
      'ElRadioGroup',
      'ElRadioButton',
      'ElSelect',
      'ElOption',
      'ElTag'
    ]) {
      app.component(name, {
        setup:
          (_, { slots }) =>
          () =>
            h('div', [slots.header?.(), slots.default?.()])
      })
    }
    app.component('ElButton', {
      props: { disabled: Boolean },
      setup:
        (props, { slots }) =>
        () =>
          h('button', { disabled: props.disabled }, slots.default?.())
    })
    app.component('ElEmpty', {
      props: { description: { type: String, default: '' } },
      setup: (props) => () => h('p', props.description)
    })
    app.component('ElAlert', {
      props: { title: { type: String, default: '' } },
      setup: (props) => () => h('p', props.title)
    })
    app.component('ElLink', {
      props: {
        href: { type: String, default: '' },
        target: { type: String, default: '' },
        rel: { type: String, default: '' }
      },
      setup:
        (props, { slots }) =>
        () =>
          h('a', props, slots.default?.())
    })
    app.component('ElTable', {
      props: { data: { type: Array, default: () => [] } },
      setup: (props, { slots }) => {
        provide('rows', props.data)
        return () => h('table', slots.default?.())
      }
    })
    app.component('ElTableColumn', {
      props: { label: { type: String, default: '' } },
      setup: (props, { slots }) => {
        const rows = inject('rows', [])
        return () =>
          h('tbody', [
            h('tr', [h('th', props.label)]),
            ...rows.map((row) => h('tr', [h('td', slots.default?.({ row }))]))
          ])
      }
    })
    app.directive('loading', { getSSRProps: () => ({}) })
    return renderToString(app)
  }

  await check('首次读取仅使用同源GET与24小时窗口；加载模板可见且翻页按钮禁用', async () => {
    const response = deferred()
    requestState.respond = () => response.promise
    const model = useObservations(undefined, fixedNow)
    const pending = model.refresh()
    assert.deepEqual(requestState.calls, [
      {
        url: '/agent/api/v1/observability/observations',
        params: {
          from: '2026-10-07T03:00:00.000Z',
          to: '2026-10-08T03:00:00.000Z',
          limit: 20
        }
      }
    ])
    assert.equal(model.loading.value, true)
    assert.deepEqual(requestState.requestOptions, { timeout: 20000, retry: false })
    assert.equal(model.canNext.value, false)
    assert.equal(model.canPrevious.value, false)
    const html = await render(model)
    assert.ok(html.includes('正在读取追踪数据'))
    assert.ok(!html.includes('<table'))
    response.resolve(page(requestState.calls[0].params, [record('synthetic-1')], 'opaque-cursor-1'))
    await pending
    assert.equal(model.loading.value, false)
    assert.equal(model.records.value.length, 1)
    assert.equal(model.canNext.value, true)
  })

  await check(
    '真实表格显示调用模型、毫秒耗时与用量；DEFAULT不是业务成功，摘要仅为本页',
    async () => {
      requestState.respond = async (params) => page(params, [record('synthetic-1')])
      const model = useObservations(undefined, fixedNow)
      await model.refresh()
      const html = await render(model)
      for (const text of [
        'LLM 追踪（Langfuse）',
        'llm.chat',
        'synthetic-model',
        '1,500 ms',
        '输入 token',
        '输出 token',
        '总 token',
        '默认级别',
        '本页 1 条观测记录',
        '观测级别不代表业务任务是否成功'
      ])
        assert.ok(html.includes(text), text)
      assert.ok(!html.includes('<iframe'))
      assert.ok(!html.includes('任务成功'))
      assert.ok(!html.includes('项目总量'))
      assert.equal(model.canNext.value, false)
    }
  )

  await check('下一页与上一页复用冻结的时间边界和真实游标，不虚构总页数', async () => {
    let clock = fixedNow()
    requestState.respond = async (params) =>
      page(params, [record(params.cursor || 'first')], params.cursor ? null : 'opaque-cursor-1')
    const model = useObservations(undefined, () => clock)
    await model.refresh()
    const first = requestState.calls[0].params
    clock = new Date('2026-10-09T04:00:00Z')
    await model.nextPage()
    assert.deepEqual(requestState.calls[1].params, { ...first, cursor: 'opaque-cursor-1' })
    assert.equal(model.currentPage.value, 2)
    assert.equal(model.canPrevious.value, true)
    assert.equal(model.canNext.value, false)
    await model.nextPage()
    assert.equal(requestState.calls.length, 2)
    await model.previousPage()
    assert.deepEqual(requestState.calls[2].params, first)
    assert.equal(model.currentPage.value, 1)
    assert.equal(model.canPrevious.value, false)
  })
  await check(
    '任务关联traceID通过受保护同源API过滤，安全状态与诊断投影且时间固定UTC+8',
    async () => {
      requestState.respond = async (params) =>
        page(
          params,
          [
            record('root-1', {
              name: 'agent.loop',
              agentStatus: 'LLM_ERROR',
              iterations: 1,
              errorCode: 'PROVIDER_JSON_SCHEMA',
              errorStage: 'LLM_REQUEST',
              httpStatus: 400,
              metadata: { secret: 'PRIVATE_CONTENT' }
            })
          ],
          'next-safe-cursor'
        )
      const model = useObservations(undefined, fixedNow, 'real-root-trace')
      await model.refresh()
      assert.equal(requestState.calls[0].params.traceId, 'real-root-trace')
      await model.nextPage()
      assert.equal(requestState.calls[1].params.traceId, 'real-root-trace')
      const html = await render(model)
      for (const text of [
        '模型请求失败',
        '1 轮',
        '模型服务拒绝输出格式',
        'HTTP 400',
        '2026-10-08 10:00:00'
      ])
        assert.ok(html.includes(text), text)
      assert.ok(!html.includes('PRIVATE_CONTENT'))
      assert.equal(
        helpers.formatObservationTime('2026-10-08T10:00:00+08:00'),
        '2026-10-08 10:00:00'
      )
      assert.equal(helpers.formatObservationTime('2026-10-08T02:00:00'), '未记录')
      await model.changeTraceId('')
      assert.equal(model.currentPage.value, 1)
      assert.equal(requestState.calls.at(-1).params.traceId, undefined)
    }
  )

  await check('切换7天/30天与条数重置游标，刷新更新窗口，不发送任意范围或超限条数', async () => {
    let clock = fixedNow()
    requestState.respond = async (params) =>
      page(params, [record('synthetic-1')], 'opaque-cursor-1')
    const model = useObservations(undefined, () => clock)
    await model.refresh()
    await model.nextPage()
    await model.changeRange(7)
    assert.equal(model.currentPage.value, 1)
    assert.equal(requestState.calls[2].params.from, '2026-10-01T03:00:00.000Z')
    assert.equal(requestState.calls[2].params.cursor, undefined)
    await model.changeRange(30)
    assert.equal(requestState.calls[3].params.from, '2026-09-08T03:00:00.000Z')
    await model.changeSize(50)
    assert.equal(requestState.calls[4].params.limit, 50)
    assert.equal(requestState.calls[4].params.cursor, undefined)
    await model.changeRange(31)
    await model.changeSize(51)
    await model.changeSize(50)
    assert.equal(requestState.calls.length, 5)
    clock = new Date('2026-10-09T04:00:00Z')
    await model.refresh()
    assert.equal(requestState.calls[5].params.to, '2026-10-09T04:00:00.000Z')
  })

  await check('空结果显示真实空态，不显示旧记录或假0项目统计', async () => {
    const model = useObservations(undefined, fixedNow)
    requestState.respond = async (params) => page(params, [record('old-row')])
    await model.refresh()
    requestState.respond = async (params) => page(params)
    await model.refresh()
    assert.equal(model.loadError.value, false)
    assert.deepEqual(model.records.value, [])
    const html = await render(model)
    assert.ok(html.includes('当前时间范围内暂无已上报的观测记录'))
    assert.ok(!html.includes('old-row'))
  })

  await check('错误清掉旧记录并显示固定错误文字；重试保留窗口且不会渲染错误payload', async () => {
    const model = useObservations(undefined, fixedNow)
    requestState.respond = async (params) =>
      page(params, [record('synthetic-1')], 'opaque-cursor-1')
    await model.refresh()
    await model.nextPage()
    const requested = requestState.calls[1].params
    requestState.respond = async () => {
      throw new Error('SECRET_RAW_PAYLOAD synthetic prompt')
    }
    await model.retry()
    assert.equal(model.loadError.value, true)
    assert.deepEqual(model.records.value, [])
    const html = await render(model)
    assert.ok(html.includes('追踪数据暂时无法加载'))
    assert.ok(html.includes('重试'))
    assert.ok(!html.includes('SECRET_RAW_PAYLOAD'))
    assert.ok(!html.includes('synthetic-model'))
    assert.equal(model.canNext.value, false)
    requestState.respond = async (params) => page(params, [record('recovered')])
    await model.retry()
    assert.deepEqual(requestState.calls.at(-1).params, requested)
    assert.equal(model.loadError.value, false)
    assert.equal(model.records.value[0].id, 'recovered')
  })

  for (const outcome of ['success', 'error']) {
    await check(`旧请求延迟${outcome}不会覆盖新范围的记录或加载状态`, async () => {
      const oldResponse = deferred(),
        newResponse = deferred()
      const model = useObservations(undefined, fixedNow)
      requestState.respond = () => oldResponse.promise
      const oldQuery = model.refresh()
      requestState.respond = () => newResponse.promise
      const newQuery = model.changeRange(7)
      if (outcome === 'success')
        oldResponse.resolve(page(requestState.calls[0].params, [record('old-row')]))
      else oldResponse.reject(new Error('old error'))
      await oldQuery
      assert.equal(model.loading.value, true)
      assert.equal(model.loadError.value, false)
      assert.deepEqual(model.records.value, [])
      newResponse.resolve(page(requestState.calls[1].params, [record('new-row')]))
      await newQuery
      assert.equal(model.loading.value, false)
      assert.equal(model.records.value[0].id, 'new-row')
    })
  }

  await check('组件卸载后的响应不修改状态；不会发起新的请求', async () => {
    const response = deferred(),
      scope = effectScope()
    requestState.respond = () => response.promise
    const model = scope.run(() => useObservations(undefined, fixedNow))
    const pending = model.refresh()
    scope.stop()
    response.resolve(page(requestState.calls[0].params, [record('late-row')]))
    await pending
    assert.deepEqual(model.records.value, [])
    await model.refresh()
    assert.equal(requestState.calls.length, 1)
  })

  await check(
    '固定错误状态区分无权限、未配置、限流、超时与上游不可用，不显示原始payload',
    async () => {
      const model = useObservations(undefined, fixedNow)
      const cases = [
        [{ code: 403 }, '仅管理员可查看追踪数据'],
        [{ code: 503 }, '追踪服务尚未启用或配置不可用'],
        [{ code: 429 }, '追踪服务请求过于频繁'],
        [{ code: 504 }, '读取追踪数据超时'],
        [{ code: 502 }, '追踪服务暂时不可用'],
        [{ code: 'ERR_BAD_RESPONSE', response: { status: 429 } }, '追踪服务请求过于频繁'],
        [{ code: 'ECONNABORTED' }, '读取追踪数据超时']
      ]
      for (const [error, expected] of cases) {
        requestState.respond = async () => {
          throw Object.assign(new Error('SECRET_RAW_PAYLOAD'), error)
        }
        await model.refresh()
        assert.equal(model.loadError.value, true)
        const html = await render(model)
        assert.ok(html.includes(expected), expected)
        assert.ok(!html.includes('SECRET_RAW_PAYLOAD'))
      }
      requestState.respond = async (params) => page(params)
      await model.retry()
      assert.equal(model.errorMessage.value, '')
    }
  )

  await check(
    '未记录模型/用量/耗时保持未知；真实0保留，额外提示词与metadata不进入状态或DOM',
    async () => {
      requestState.respond = async (params) =>
        page(params, [
          record('synthetic-unknown', {
            name: 'agent.loop',
            model: null,
            inputTokens: null,
            outputTokens: null,
            totalTokens: null,
            latencyMs: null,
            input: 'SECRET_PROMPT',
            output: 'SECRET_RESPONSE',
            metadata: { userId: 'SECRET_USER' }
          }),
          record('synthetic-zero', {
            name: 'synthetic-zero',
            inputTokens: 0,
            outputTokens: 0,
            totalTokens: 0,
            latencyMs: 0,
            level: 'ERROR'
          })
        ])
      const model = useObservations(undefined, fixedNow)
      await model.refresh()
      assert.equal(model.records.value[0].model, null)
      assert.equal(model.records.value[0].totalTokens, null)
      assert.equal(model.records.value[1].totalTokens, 0)
      assert.ok(!('input' in model.records.value[0]))
      assert.ok(!('output' in model.records.value[0]))
      assert.ok(!('metadata' in model.records.value[0]))
      const html = await render(model)
      assert.ok(html.includes('未记录'))
      assert.ok(html.includes('0 ms'))
      assert.ok(html.includes('错误级别'))
      for (const secret of ['SECRET_PROMPT', 'SECRET_RESPONSE', 'SECRET_USER'])
        assert.ok(!html.includes(secret))
      for (const value of [null, undefined, '', ' ', -1, NaN, false, {}, []])
        assert.equal(helpers.formatObservationTokens(value), '未记录')
      assert.equal(helpers.formatObservationTokens(0), '0')
      assert.equal(helpers.formatObservationTokens('0'), '0')
      assert.equal(helpers.formatObservationLatency(0), '0 ms')
      assert.equal(helpers.formatObservationLatency(null), '未记录')
      assert.equal(helpers.observationLevelLabel('DEFAULT'), '默认级别')
      assert.equal(helpers.observationLevelType('DEFAULT'), 'info')
    }
  )

  await check('不可信分页结构/时间范围/游标/重复ID拒绝显示，不伪造分页或混合时间窗口', async () => {
    const model = useObservations(undefined, fixedNow)
    const variants = [
      (_params) => ({ data: { records: [] } }),
      (params) => ({ data: { ...page(params).data, from: '2026-01-01T00:00:00Z' } }),
      (params) => ({ data: { ...page(params).data, hasMore: true, nextCursor: null } }),
      (params) => page(params, [record('same'), record('same')]),
      (params) => page(params, [null]),
      (params) =>
        page(
          params,
          Array.from({ length: 21 }, (_, index) => record(String(index)))
        )
    ]
    for (const variant of variants) {
      requestState.respond = async (params) => variant(params)
      await model.refresh()
      assert.equal(model.loadError.value, true)
      assert.deepEqual(model.records.value, [])
      assert.equal(model.canNext.value, false)
    }
  })

  await check('可选控制台链接为次要安全新窗口入口，核心数据不依赖控制台地址', async () => {
    requestState.respond = async (params) => page(params, [record('synthetic-1')])
    const model = useObservations(undefined, fixedNow)
    await model.refresh()
    const without = await render(model)
    assert.ok(without.includes('synthetic-model'))
    const withLink = await render(model, { langfuseUrl: 'https://jp.cloud.langfuse.com' })
    assert.ok(withLink.includes('href="https://jp.cloud.langfuse.com"'))
    assert.ok(
      withLink.includes('target="_blank"') && withLink.includes('rel="noopener noreferrer"')
    )
    assert.ok(withLink.includes('synthetic-model'))
    assert.ok(!withLink.includes('<iframe'))
    assert.ok(!source.includes('window.open') && !source.includes('location.href'))
  })
  console.log(`Observability inline panel: ${count} checks passed.`)
} finally {
  await server.close()
  delete globalThis.__observationTest
}
