// Actual Vue setup scripts and templates, synthetic offline APIs.
import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import vm from 'node:vm'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'
import { parse, compileTemplate } from '@vue/compiler-sfc'
import { ref, reactive, computed, watch, nextTick, effectScope } from 'vue'
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const settle = async () => {
  await nextTick()
  await new Promise((done) => setImmediate(done))
  await nextTick()
}
const deferred = () => {
  let resolve
  const promise = new Promise((yes) => {
    resolve = yes
  })
  return { resolve, promise }
}
const setup = async (file, bindings, fields) => {
  const source = await fs.readFile(path.join(root, 'src/views', file), 'utf8')
  const tree = ts.createSourceFile(
    file,
    parse(source).descriptor.scriptSetup.content,
    ts.ScriptTarget.Latest,
    true,
    ts.ScriptKind.TS
  )
  const stripped = ts.factory.updateSourceFile(
    tree,
    tree.statements.filter((statement) => !ts.isImportDeclaration(statement))
  )
  const script = ts.transpileModule(ts.createPrinter().printFile(stripped), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.None }
  }).outputText
  const scope = effectScope()
  const context = {
    ref,
    reactive,
    computed,
    watch,
    console,
    MENTAL_WRITE_ROLES: ['admin', 'psychologist'],
    ElMessage: { warning() {}, success() {}, error() {} },
    ...bindings
  }
  scope.run(() => vm.runInNewContext(`${script}\nglobalThis.state={${fields.join(',')}}`, context))
  return { ...context.state, scope }
}
const questionnaire = (id) => ({
  data: {
    questionnaire: { id, title: `虚构问卷${id}` },
    questions: [
      {
        id: id * 10,
        content: `虚构题目${id}`,
        questionType: 'scale',
        required: 1,
        scaleMin: 1,
        scaleMax: 5,
        sortOrder: 1
      }
    ],
    levelRules: []
  }
})
for (const folder of ['mental', 'student-mental']) {
  for (const file of await fs.readdir(path.join(root, 'src/views', folder))) {
    if (!file.endsWith('.vue')) continue
    const source = await fs.readFile(path.join(root, 'src/views', folder, file), 'utf8')
    assert.deepEqual(
      compileTemplate({
        source: parse(source).descriptor.template.content,
        filename: file,
        id: file
      }).errors,
      [],
      `${folder}/${file}`
    )
  }
}
console.log('✓ 心理页面真实模板编译通过，包括预警弹窗')
{
  const route = reactive({ params: { id: '1' } })
  const first = deferred()
  const second = deferred()
  const submitted = []
  const navigated = []
  const model = await setup(
    'student-mental/take.vue',
    {
      useRoute: () => route,
      useRouter: () => ({ push: (url) => navigated.push(url) }),
      useUserStore: () => ({ userInfo: { id: 901 }, userRoles: ['student'] }),
      studentGetQuestionnaireForTaking: (id) => (id === 1 ? first.promise : second.promise),
      studentSubmitAnswers: async (payload) => {
        submitted.push(payload)
        return { data: { id: 888 } }
      }
    },
    ['questionnaire', 'questions', 'answers', 'handleSubmit', 'loadedQuestionnaireId']
  )
  try {
    route.params.id = '2'
    await settle()
    second.resolve(questionnaire(2))
    await settle()
    model.answers[20].scaleValue = 4
    first.resolve(questionnaire(1))
    await settle()
    assert.equal(model.questionnaire.value.id, 2)
    assert.equal(model.answers[10], undefined)
    model.answers[20].scaleValue = 9
    await model.handleSubmit()
    assert.equal(submitted.length, 0)
    model.answers[20].scaleValue = 4
    await model.handleSubmit()
    assert.equal(submitted[0].questionnaireId, 2)
    assert.equal(submitted[0].answers[0].scaleValue, 4)
    assert.deepEqual(Array.from(submitted[0].answers[0].optionIndices), [])
    assert.deepEqual(navigated, ['/student-mental/result/888'])
  } finally {
    model.scope.stop()
  }
}
console.log('✓ 作答页复用清空旧答案、忽略迟到响应、验证并提交量表整数')
{
  const route = reactive({ params: { assessmentId: '1' } })
  const first = deferred()
  const second = deferred()
  const model = await setup(
    'student-mental/result.vue',
    {
      useRoute: () => route,
      useRouter: () => ({}),
      useUserStore: () => ({ userInfo: { id: 901 }, userRoles: ['student'] }),
      studentGetMyAssessment: (_, id) => (id === 1 ? first.promise : second.promise)
    },
    ['data', 'questions', 'answerView', 'bannerClass', 'canSeeRaw', 'canSeeLevel']
  )
  try {
    route.params.assessmentId = '2'
    await settle()
    second.resolve({
      data: {
        assessment: { id: 2, level: null, score: null },
        questions: [{ id: 22, content: '提交时旧题干', questionType: 'scale' }],
        answersJson: JSON.stringify([{ questionId: 22, scaleValue: 3 }])
      }
    })
    await settle()
    first.resolve({ data: { assessment: { id: 1 }, questions: [] } })
    await settle()
    assert.equal(model.data.value.assessment.id, 2)
    assert.equal(model.answerView.value[0].content, '提交时旧题干')
    assert.equal(model.answerView.value[0].scaleValue, 3)
    assert.equal(model.bannerClass.value, '')
    assert.equal(model.canSeeRaw.value, false)
    assert.equal(model.canSeeLevel.value, false)
  } finally {
    model.scope.stop()
  }
}
console.log('✓ 结果页使用历史快照、忽略迟到响应，权限空值不显示正常评级')
{
  const route = reactive({ params: { id: '1' } })
  const first = deferred()
  const second = deferred()
  const model = await setup(
    'mental/questionnaire-design.vue',
    {
      useRoute: () => route,
      useRouter: () => ({}),
      useUserStore: () => ({ userRoles: ['psychologist'] }),
      getQuestionnaireFull: (id) => (id === 1 ? first.promise : second.promise)
    },
    ['questionnaire', 'questions', 'form', 'canWrite']
  )
  try {
    model.form.content = '旧题目编辑'
    route.params.id = '2'
    await settle()
    second.resolve(questionnaire(2))
    await settle()
    first.resolve(questionnaire(1))
    await settle()
    assert.equal(model.questionnaire.value.id, 2)
    assert.equal(model.questions.value[0].id, 20)
    assert.equal(model.form.content, '')
    assert.equal(model.canWrite.value, true)
  } finally {
    model.scope.stop()
  }
}
console.log('✓ 题库设计页参数切换清空旧表单并忽略旧问卷响应')
{
  const route = reactive({ params: { id: '1' } })
  const firstMeta = deferred()
  const firstRows = deferred()
  const secondMeta = deferred()
  const secondRows = deferred()
  const model = await setup(
    'mental/questionnaire-result.vue',
    {
      useRoute: () => route,
      useRouter: () => ({}),
      usePermission: () => ({ hasRole: () => true }),
      getQuestionnaireDetail: (id) => (id === 1 ? firstMeta.promise : secondMeta.promise),
      getCompletionList: (id) => (id === 1 ? firstRows.promise : secondRows.promise)
    },
    ['rows', 'questionnaire', 'avgScore']
  )
  try {
    route.params.id = '2'
    await settle()
    secondMeta.resolve({ data: { id: 2 } })
    secondRows.resolve({ data: [{ score: null }, { score: 0 }] })
    await settle()
    firstMeta.resolve({ data: { id: 1 } })
    firstRows.resolve({ data: [{ score: 99 }] })
    await settle()
    assert.equal(model.questionnaire.value.id, 2)
    assert.equal(model.avgScore.value, 0)
    model.rows.value = [{ score: null }, {}]
    assert.equal(model.avgScore.value, null)
  } finally {
    model.scope.stop()
  }
}
console.log('✓ 完成情况页忽略旧参数响应，平均分区分真实零分与不可见分数')
console.log('mental-contract: 5 checks passed')
