// Production report template and request state, entirely synthetic and offline.
/* eslint-disable vue/one-component-per-file -- lightweight UI components exercise one real report template */
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse, compileTemplate } from '@vue/compiler-sfc'
import { createServer } from 'vite'
import { createSSRApp, h, provide, inject, ref, effectScope, nextTick } from 'vue'
import { renderToString } from 'vue/server-renderer'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const source = await fs.readFile(path.join(root, 'src/views/agent/ReportDetail.vue'), 'utf8')
const { descriptor } = parse(source)
const traceSource = await fs.readFile(path.join(root, 'src/views/admin/trace.vue'), 'utf8')
const traceDescriptor = parse(traceSource).descriptor
const traceCompiled = compileTemplate({
  source: traceDescriptor.template.content,
  filename: 'trace.vue',
  id: 'trace-console-test',
  ssr: true,
  ssrCssVars: []
})
assert.deepEqual(traceCompiled.errors, [])
const compiled = compileTemplate({
  source: descriptor.template.content,
  filename: 'ReportDetail.vue',
  id: 'agent-report-test',
  ssr: true,
  ssrCssVars: []
})
assert.deepEqual(compiled.errors, [])
const server = await createServer({
  root,
  configFile: false,
  server: { middlewareMode: true, hmr: false, ws: false },
  optimizeDeps: { noDiscovery: true, include: [] },
  resolve: { alias: { '@': path.join(root, 'src') } },
  plugins: [
    {
      name: 'agent-report-offline',
      enforce: 'pre',
      resolveId(id) {
        if (id === '/agent-report-template.mjs') return '\0agent-report-template'
        if (id === '/trace-console-template.mjs') return '\0trace-console-template'
        if (id === path.join(root, 'src/utils/request')) return '\0agent-report-request'
      },
      load(id) {
        if (id === '\0agent-report-template') return compiled.code
        if (id === '\0trace-console-template') return traceCompiled.code
        if (id === '\0agent-report-request')
          return 'export default { get(){throw new Error("network disabled")}, post(){throw new Error("network disabled")} }'
      }
    }
  ]
})
let count = 0
const check = async (name, action) => {
  await action()
  ++count
  console.log(`  ✓ ${name}`)
}
const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
const settle = async () => {
  await nextTick()
  await new Promise((resolve) => setImmediate(resolve))
  await nextTick()
}

try {
  const { ssrRender } = await server.ssrLoadModule('/agent-report-template.mjs')
  const { ssrRender: traceRender } = await server.ssrLoadModule('/trace-console-template.mjs')
  const { normalizeConsoleUrl } = await server.ssrLoadModule('/src/views/admin/traceConsole.js')
  const { useAgentReport, hasPlanContent } = await server.ssrLoadModule(
    '/src/views/agent/reportState.js'
  )
  const failureHelpers = await server.ssrLoadModule('/src/views/agent/failureState.js')
  const sourceHelpers = await server.ssrLoadModule('/src/views/agent/knowledgeSource.js')
  const { formatAgentTime } = await server.ssrLoadModule('/src/views/agent/agentTime.js')
  const { createWarningStream } = await server.ssrLoadModule('/src/views/agent/warningStream.js')
  const full = JSON.parse(
    await fs.readFile(
      path.join(root, '../backend/agent-service/src/test/resources/report/react-final.json'),
      'utf8'
    )
  )
  const legacy = JSON.parse(
    await fs.readFile(
      path.join(root, '../backend/agent-service/src/test/resources/report/legacy-plan.json'),
      'utf8'
    )
  )
  // These are the server DTO fixtures, independently asserted against PlanViewNormalizer by Java tests.
  const reactView = {
    ...full.intervention_plan,
    immediate_actions: [{ ...full.intervention_plan.immediate_actions[0], references: [] }],
    long_term_plan: [{ ...full.intervention_plan.long_term_plan[0], metrics: [] }],
    resources: [{ type: '', name: '合成支持资源：示例热线', link_or_contact: '' }]
  }
  const legacyView = {
    ...legacy,
    immediate_actions: [
      {
        action: '安排合成学习辅导',
        owner: '合成导师',
        deadline: '3 天',
        references: ['synthetic-case-002']
      }
    ],
    long_term_plan: [
      {
        goal: '完成合成复习；建立合成记录',
        horizon: '1月',
        milestones: [],
        metrics: ['每周合成复核一次']
      }
    ],
    references: ['synthetic-policy-001', 'synthetic-case-002']
  }

  const render = async (
    plan,
    knowledge = { executed: false, chunks: [], searches: [] },
    extra = {}
  ) => {
    const state = {
      taskId: '1',
      task: {
        id: 1,
        studentId: 1,
        status: 'COMPLETED',
        riskLevel: 'MEDIUM',
        interventionPlan: JSON.stringify(plan),
        riskAnalysisResult: JSON.stringify(full.risk_analysis),
        retrievedKnowledge: JSON.stringify(knowledge)
      },
      plan,
      risk: full.risk_analysis,
      knowledge,
      audit: {},
      loading: false,
      loadError: false,
      exporting: false,
      canExport: true,
      canFeedback: hasPlanContent(plan),
      feedback: { score: 0, outcome: '', comment: '' },
      feedbackSubmitting: false,
      auditType: 'info',
      auditIcon: 'span',
      hasStage: (n) => n === 1 || n === 3 || (n === 2 && knowledge.executed),
      stageType: () => 'info',
      riskType: () => 'info',
      statusType: () => 'info',
      statusLabel: (s) => s,
      riskLabel: (s) => s,
      formatTime: formatAgentTime,
      traceId: '',
      viewTrace() {},
      ...failureHelpers,
      ...sourceHelpers,
      formatScore: (s) => s,
      sourceTagType: () => 'info',
      sourceLabel: (s) => s,
      goBack() {},
      fetchDetail() {},
      handleExport() {},
      handleSubmitFeedback() {},
      ...extra
    }
    const app = createSSRApp({ data: () => state, ssrRender })
    app.config.warnHandler = () => {} // UI/icon stubs do not implement DOM-only directive behavior.
    for (const name of [
      'ElPageHeader',
      'ElButton',
      'ElCard',
      'ElDescriptions',
      'ElDescriptionsItem',
      'ElTag',
      'ElAlert',
      'ElTimeline',
      'ElTimelineItem',
      'ElCollapse',
      'ElCollapseItem',
      'ElForm',
      'ElFormItem',
      'ElRate',
      'ElSelect',
      'ElOption',
      'ElIcon'
    ]) {
      app.component(name, {
        props: { title: { type: String, default: '' }, label: { type: String, default: '' } },
        setup:
          (props, { slots }) =>
          () =>
            h('div', [
              props.title,
              props.label,
              slots.default?.(),
              slots.header?.(),
              slots.extra?.()
            ])
      })
    }
    app.component('ElInput', {
      props: { modelValue: { type: [String, Number], default: '' } },
      setup: (props) => () => h('pre', props.modelValue)
    })
    app.component('ElTable', {
      props: { data: { type: Array, default: () => [] } },
      setup: (props, { slots }) => {
        provide('rows', props.data)
        return () => h('table', slots.default?.())
      }
    })
    app.component('ElTableColumn', {
      props: { prop: { type: String, default: '' }, label: { type: String, default: '' } },
      setup: (props, { slots }) => {
        const rows = inject('rows', [])
        return () =>
          h('tbody', [
            h('tr', props.label),
            ...rows.map((row) =>
              h('tr', slots.default ? slots.default({ row }) : String(row[props.prop] ?? ''))
            )
          ])
      }
    })
    app.directive('loading', { getSSRProps: () => ({}) })
    app.directive('permission', { getSSRProps: () => ({}) })
    return renderToString(app)
  }

  await check(
    '真实 Vue 模板显示 ReAct 期限、目标、里程碑、周期、字符串资源及全局引用',
    async () => {
      const html = await render(reactView)
      for (const text of [
        '48小时内',
        '建立合成学习安排',
        '第1周完成合成计划',
        '第4周复核合成结果',
        '4周',
        '合成支持资源：示例热线',
        'synthetic-policy-001',
        '方案引用'
      ])
        assert.ok(html.includes(text), text)
      assert.ok(!html.includes('— 天'))
      assert.ok(!html.includes('立即行动（7 天内）'))
    }
  )
  await check(
    '真实 Vue 模板保留 legacy 天数、阶段、所有目标、衡量指标、对象资源及行动引用',
    async () => {
      const html = await render(legacyView)
      for (const text of [
        '3 天',
        '1月',
        '完成合成复习；建立合成记录',
        '每周合成复核一次',
        '合成学习课程',
        'https://example.invalid/synthetic',
        'synthetic-case-002'
      ])
        assert.ok(html.includes(text), text)
    }
  )
  await check('实际未执行与失败检索不会显示成功；失败来源仍可追溯', async () => {
    const noSearch = await render(reactView)
    assert.ok(!noSearch.includes('检索完成'))
    const failed = await render(reactView, {
      executed: true,
      chunks: [],
      searches: [{ tool: 'search_cases', status: 'FAILED' }]
    })
    assert.ok(failed.includes('search_cases') && failed.includes('检索失败'))
    assert.ok(!failed.includes('检索完成'))
  })
  await check('FAILED报告显示固定失败阶段、类别、HTTP与真实追踪，不输出原始异常', async () => {
    const html = await render({}, undefined, {
      task: {
        id: 42,
        status: 'FAILED',
        failureStage: 'LLM_REQUEST',
        failureCode: 'PROVIDER_JSON_SCHEMA',
        failureHttpStatus: 400,
        errorMessage: 'SECRET_RAW_ERROR'
      },
      traceId: 'real-root-trace',
      hasStage: () => false,
      canExport: false,
      canFeedback: false
    })
    for (const text of [
      '模型请求',
      '模型服务拒绝输出格式',
      'HTTP 400',
      'real-root-trace',
      '查看站内追踪'
    ])
      assert.ok(html.includes(text), text)
    assert.ok(!html.includes('SECRET_RAW_ERROR'))
    assert.equal(failureHelpers.failureCodeLabel('SECRET_RAW_ERROR'), '未记录失败类别')
    assert.equal(failureHelpers.failureStageLabel('SECRET_RAW_ERROR'), '未记录失败阶段')
    assert.equal(failureHelpers.failureCodeLabel('ITERATION_LIMIT'), '分析超过最大轮次')
    assert.equal(failureHelpers.failureHttpLabel('400'), '未记录')
    const legacy = await render({}, undefined, {
      task: { status: 'FAILED' },
      hasStage: () => false
    })
    assert.ok(legacy.includes('无法从历史状态推断具体原因'))
  })
  await check('Agent裸UTC兼容仅作用于Agent记录，带偏移时间统一为UTC+8', async () => {
    assert.equal(formatAgentTime('2026-10-11T03:49:56'), '2026-10-11 11:49:56')
    assert.equal(formatAgentTime('2026-10-11T03:49:56Z'), '2026-10-11 11:49:56')
    assert.equal(formatAgentTime('2026-10-11T11:49:56+08:00'), '2026-10-11 11:49:56')
    assert.equal(formatAgentTime('2026-10-11T23:49:56Z'), '2026-10-12 07:49:56')
    assert.equal(formatAgentTime('2026-10-11'), '')
    assert.equal(formatAgentTime('PRIVATE_NOT_A_DATE'), '')
  })
  await check('RAG出处、文档标识与可验证URL显示，来源未知不生成链接', async () => {
    const html = await render(reactView, {
      executed: true,
      searches: [],
      chunks: [
        {
          chunk_id: 'policy_0000',
          doc_id: 'policy',
          source_db: 'policy',
          source: '教育政策原文',
          source_url: 'https://www.example.org/public',
          title: '支持政策',
          content: '公开摘要',
          score: 0.8
        },
        {
          chunk_id: 'case_0000',
          source_db: 'case',
          source: '',
          source_url: 'javascript:alert(1)',
          title: '无出处资料'
        }
      ]
    })
    assert.ok(html.includes('教育政策原文'))
    assert.ok(html.includes('文档：policy'))
    assert.ok(html.includes('href="https://www.example.org/public"'))
    assert.ok(html.includes('出处未记录'))
    assert.ok(!html.includes('javascript:'))
    for (const source_url of [
      'javascript:alert(1)',
      'https://user:pass@example.org/public',
      '/local/path',
      'bad'
    ])
      assert.equal(sourceHelpers.knowledgeSourceUrl({ source_url }), '')
  })
  await check('SSE正常EOF走退避重连；401/403终止，登出与卸载阻止旧回调', async () => {
    let options,
      token = 'offline-fixture',
      notices = 0,
      connected = false,
      calls = 0
    const stream = createWarningStream({
      url: '/offline-stream',
      getToken: () => token,
      onConnected: (value) => {
        connected = value
      },
      onWarning: () => {
        notices++
      },
      backoff: { reset() {}, next: () => 2000 },
      transport: async (_url, config) => {
        calls++
        options = config
        await new Promise((resolve) =>
          config.signal.addEventListener('abort', resolve, { once: true })
        )
      }
    })
    const pending = stream.start()
    await options.onopen(new Response('', { headers: { 'content-type': 'text/event-stream' } }))
    assert.equal(connected, true)
    options.onmessage({ event: 'warning' })
    assert.equal(notices, 1)
    let ended
    try {
      options.onclose()
    } catch (error) {
      ended = error
    }
    assert.ok(ended)
    assert.equal(connected, false)
    assert.equal(options.onerror(ended), 2000)
    for (const status of [401, 403]) {
      let denied
      try {
        await options.onopen(new Response('', { status }))
      } catch (error) {
        denied = error
      }
      assert.ok(denied)
      assert.throws(() => options.onerror(denied))
    }
    const old = options
    token = ''
    await stream.start()
    await pending
    assert.equal(old.signal.aborted, true)
    assert.equal(calls, 1)
    old.onmessage({ event: 'warning' })
    await old.onopen(new Response('', { headers: { 'content-type': 'text/event-stream' } }))
    assert.equal(notices, 1)
    assert.equal(connected, false)
    stream.dispose()
    token = 'offline-again'
    await stream.start()
    assert.equal(calls, 1)
  })
  await check('无真实行动的占位方案不开放反馈；格式异常有明确提示', async () => {
    assert.equal(hasPlanContent({ summary: 'LLM不可用转人工', talk_outline: '待补全' }), false)
    const html = await render({}, undefined, {
      task: { planViewStatus: 'INVALID' },
      hasStage: () => false
    })
    assert.ok(html.includes('方案数据格式异常'))
    assert.ok(!html.includes('干预效果跟进'))
  })
  await check('切换报告身份后迟到的旧请求不能覆盖新报告或 loading 状态', async () => {
    const scope = effectScope(),
      id = ref('1'),
      first = deferred(),
      second = deferred(),
      calls = []
    const model = scope.run(() =>
      useAgentReport(id, (value) => {
        calls.push(value)
        return value === '1' ? first.promise : second.promise
      })
    )
    id.value = '2'
    await nextTick()
    second.resolve({ data: { id: 2, interventionPlan: 'new' } })
    await settle()
    first.resolve({ data: { id: 1, interventionPlan: 'old' } })
    await settle()
    assert.deepEqual(calls, ['1', '2'])
    assert.equal(model.task.value.id, 2)
    assert.equal(model.loading.value, false)
    scope.stop()
  })
  await check('同一身份刷新时旧错误不能抹掉最新成功结果', async () => {
    const scope = effectScope(),
      id = ref('1'),
      first = deferred(),
      second = deferred()
    let requests = 0
    const model = scope.run(() =>
      useAgentReport(id, () => (++requests === 1 ? first.promise : second.promise))
    )
    const fresh = model.fetchDetail()
    second.resolve({ data: { id: 1 } })
    await fresh
    first.reject(new Error('old failure'))
    await settle()
    assert.equal(model.task.value.id, 1)
    assert.equal(model.loadError.value, false)
    scope.stop()
  })
  await check('路由新身份立即清空旧档案；不匹配的服务器身份显示错误', async () => {
    const scope = effectScope(),
      id = ref('1'),
      second = deferred()
    const model = scope.run(() =>
      useAgentReport(id, (value) =>
        value === '1' ? Promise.resolve({ data: { id: 1 } }) : second.promise
      )
    )
    await settle()
    id.value = '2'
    await nextTick()
    assert.deepEqual(model.task.value, {})
    assert.equal(model.loading.value, true)
    second.resolve({ data: { id: 1 } })
    await settle()
    assert.equal(model.loadError.value, true)
    assert.deepEqual(model.task.value, {})
    scope.stop()
  })
  await check('卸载后迟到的详情请求不能写入组件状态', async () => {
    const scope = effectScope(),
      id = ref('1'),
      response = deferred()
    const model = scope.run(() => useAgentReport(id, () => response.promise))
    scope.stop()
    response.resolve({ data: { id: 1 } })
    await settle()
    assert.deepEqual(model.task.value, {})
  })
  await check('追踪入口默认日本 Cloud 控制台；拒绝脚本协议、凭据及带token的地址', async () => {
    assert.equal(normalizeConsoleUrl(), 'https://jp.cloud.langfuse.com')
    assert.equal(
      normalizeConsoleUrl('https://trace.educare.test/project/synthetic/'),
      'https://trace.educare.test/project/synthetic'
    )
    for (const bad of [
      'javascript:alert(1)',
      'http://trace.educare.test',
      'https://user:password@trace.educare.test',
      'https://trace.educare.test?key=secret',
      'https://trace.educare.test#token'
    ])
      assert.equal(normalizeConsoleUrl(bad), '')
  })
  await check('实际追踪模板提供站内数据视图，控制台链接仅为可选的新窗口入口', async () => {
    const app = createSSRApp({
      data: () => ({
        langfuseUrl: normalizeConsoleUrl(),
        rangeDays: 1,
        pageSize: 20,
        from: '',
        to: '',
        records: [],
        loading: false,
        loadError: false,
        currentPage: 1,
        canPrevious: false,
        canNext: false
      }),
      ssrRender: traceRender
    })
    app.config.warnHandler = () => {}
    app.component('ElCard', {
      setup:
        (_, { slots }) =>
        () =>
          h('div', [slots.header?.(), slots.default?.()])
    })
    app.component('ElIcon', {
      setup:
        (_, { slots }) =>
        () =>
          h('span', slots.default?.())
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
    const html = await renderToString(app)
    assert.ok(html.includes('href="https://jp.cloud.langfuse.com"'))
    assert.ok(html.includes('target="_blank"') && html.includes('rel="noopener noreferrer"'))
    assert.ok(html.includes('直接查看已上报的调用记录'))
    assert.ok(!html.includes('<iframe'))
  })
  console.log(`Agent report + trace console: ${count} checks passed.`)
} finally {
  await server.close()
}
