import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { mkdir, mkdtemp, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { analyzeLoginBundle, loginBudgetFailures } from '../scripts/check-login-bundle.mjs'

const directory = await mkdtemp(join(tmpdir(), 'educare-login-bundle-'))
try {
  await mkdir(join(directory, 'assets'))
  const put = (name, text) => writeFile(join(directory, name), text)
  await put(
    'index.html',
    '<script type="module" src="/assets/index.js"></script><link rel="modulepreload" href="/assets/shared.js"><link rel="stylesheet" href="/assets/base.css">'
  )
  await put('assets/shared.js', 'export const shared=1;')
  await put('assets/base.css', '.el-table{--el-table-border-color:transparent;}')
  await put('assets/login.css', '.login{display:flex;}')
  await put(
    'assets/index.js',
    'import "./shared.js";const __vite__mapDeps=(i,m=__vite__mapDeps,d=(m.f||(m.f=["assets/login.js","assets/shared.js","assets/login.css"])))=>i.map(i=>d[i]);const routes=[{path:"/login",component:()=>preload(()=>import("./login.js"),__vite__mapDeps([0,1,2])),meta:{public:true}}];'
  )
  await put('assets/login.js', 'import {shared} from "./shared.js";export const login=shared;')
  let report = await analyzeLoginBundle(directory)
  assert.equal(report.assetCount, 5)
  assert.equal(report.cssCount, 2)
  assert.equal(report.jsCount, 3)
  assert.deepEqual(report.discovery.initialDependenciesNotInHtmlPreloads, [])
  assert.deepEqual(report.discovery.loginDependenciesNotInPreloads, [])
  assert.deepEqual(loginBudgetFailures(report), [])
  // A deferred chart name in the route map must not count as a static download.
  await put(
    'assets/index.js',
    'import "./shared.js";const __vite__mapDeps=(i,m=__vite__mapDeps,d=(m.f||(m.f=["assets/login.js","assets/shared.js","assets/login.css","assets/charts-x.js"])))=>i.map(i=>d[i]);const routes=[{path:"/login",component:()=>preload(()=>import("./login.js"),__vite__mapDeps([0,1,2])),meta:{public:true}},{path:"/dashboard",component:()=>import("./charts-x.js")}];'
  )
  assert.equal((await analyzeLoginBundle(directory)).chartsLoaded, false)
  await put('assets/charts-x.js', 'export const charts=1;')
  await put('assets/login.js', 'import "./charts-x.js";export const login=1;')
  report = await analyzeLoginBundle(directory)
  assert.ok(loginBudgetFailures(report).some((message) => message.includes('Charts')))
  await put('assets/table.js', 'export const table={name:"ElTable"};')
  await put('assets/login.js', 'import "./table.js";export const login=1;')
  report = await analyzeLoginBundle(directory)
  assert.ok(loginBudgetFailures(report).some((message) => message.includes('table')))
  await put('assets/login.js', 'export const login=1;')
  await put('assets/login.css', '.el-table__border-left-patch{position:absolute;}')
  assert.ok(
    loginBudgetFailures(await analyzeLoginBundle(directory)).some((message) =>
      message.includes('table')
    )
  )
  await put('assets/login.css', '.login{display:flex;}')
  const bulk = Array.from({ length: 8192 }, (_, index) =>
    createHash('sha256').update(String(index)).digest('hex')
  ).join('')
  await put('assets/login.js', `export const bulk="${bulk}";`)
  assert.ok(
    loginBudgetFailures(await analyzeLoginBundle(directory)).some((message) =>
      message.includes('gzip')
    )
  )
  const extras = Array.from({ length: 9 }, (_, index) => `extra-${index}.js`)
  for (const name of extras) await put(`assets/${name}`, 'export const value=1;')
  await put('assets/login.js', extras.map((name) => `import "./${name}";`).join(''))
  assert.ok(
    loginBudgetFailures(await analyzeLoginBundle(directory)).some((message) =>
      message.includes('assets')
    )
  )
  await put('assets/index.js', 'export const missingLoginRoute=1;')
  await assert.rejects(analyzeLoginBundle(directory), /login route/)
  console.log(
    'Login bundle output contract passed: lazy/preload graph, deferred charts, table/byte/count guards and unsupported shape'
  )
} finally {
  await rm(directory, { recursive: true, force: true })
}
