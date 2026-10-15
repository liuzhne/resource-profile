import { readFile } from 'node:fs/promises'
import { resolve, posix } from 'node:path'
import { pathToFileURL } from 'node:url'
import { gzipSync } from 'node:zlib'

export const LOGIN_BUDGETS = Object.freeze({ gzipBytes: 200 * 1024, assetCount: 12 })

// Inspect built output, including Vite's lazy-login preload list. Do not count
// route-map strings as downloads or assume every URL requires a new connection.
export const analyzeLoginBundle = async (directory) => {
  const root = resolve(directory)
  const source = new Map()
  const normalize = (name) => {
    const path = posix.normalize(name.replace(/^\//, ''))
    if (/^[a-z]+:/i.test(path) || path.startsWith('../') || path === '..')
      throw new Error('Login assets must be local to the build directory')
    return path
  }
  const load = async (name) => {
    if (!source.has(name)) source.set(name, await readFile(resolve(root, name)))
    return source.get(name)
  }
  const html = (await load('index.html')).toString('utf8')
  const entry = html.match(/<script\b[^>]*\bsrc="([^"]+\.js)"/)?.[1]
  if (!entry) throw new Error('No built module entry found')
  const declared = new Set([normalize(entry)])
  for (const match of html.matchAll(/<link\b[^>]*\bhref="([^"]+\.(?:js|css))"/g))
    declared.add(normalize(match[1]))
  const reached = new Set()
  const visit = async (asset) => {
    asset = normalize(asset)
    if (reached.has(asset)) return
    reached.add(asset)
    if (!asset.endsWith('.js')) return
    const content = (await load(asset)).toString('utf8')
    const imports = new Set()
    for (const match of content.matchAll(
      /\b(?:import|export)\s*(?:\{[^}]*\}|[^;\n]*?)?\s*from\s*["'](\.\/[^"']+)["']/g
    ))
      imports.add(match[1])
    for (const match of content.matchAll(/\bimport\s*["'](\.\/[^"']+)["']/g)) imports.add(match[1])
    for (const child of imports) await visit(posix.join(posix.dirname(asset), child))
  }
  for (const asset of declared) await visit(asset)
  const initial = new Set(reached)
  let route
  for (const asset of [...initial].sort()) {
    if (!asset.endsWith('.js')) continue
    const content = (await load(asset)).toString('utf8')
    const match = /path:\s*["'`]\/login["'`]/.exec(content)
    if (match) {
      route = { asset, content, offset: match.index }
      break
    }
  }
  if (!route) throw new Error('No lazy login route in the entry import graph')
  const segment = route.content.slice(route.offset, route.offset + 1400).split('meta:')[0]
  const login = segment.match(/import\(\s*["'`](\.\/[^"'`]+\.js)["'`]\s*\)/)?.[1]
  if (!login) throw new Error('Cannot resolve the built login import')
  await visit(posix.join(posix.dirname(route.asset), login))
  const map = segment.match(/__vite__mapDeps\(\[([^\]]*)\]/)
  const preloads = new Set()
  if (map) {
    const dependencyArray = route.content.match(/m\.f=(\[[^\]]+\])/)
    if (!dependencyArray) throw new Error('Missing Vite dependency map for login')
    const dependencies = JSON.parse(dependencyArray[1])
    for (const index of map[1].split(',').filter((value) => value.trim())) {
      const name = dependencies[Number(index)]
      if (typeof name !== 'string') throw new Error('Invalid login preload index')
      preloads.add(normalize(name))
      await visit(name)
    }
  }
  const files = []
  let tableImplementation = false
  let tableLibraryStyles = false
  for (const path of [...reached].sort()) {
    const bytes = await load(path)
    const content = bytes.toString('utf8')
    if (path.endsWith('.js')) {
      tableImplementation ||=
        /(?:name|__name)\s*:\s*["'`](?:ElTable|ElTableColumn|table|table-column)["'`]/.test(content)
    } else if (path.endsWith('.css')) {
      // Application-wide table theme overrides are small and intentional. This
      // selector identifies the actual Element Plus table implementation styles.
      tableLibraryStyles ||= content.includes('.el-table__border-left-patch')
    }
    files.push({ path, bytes: bytes.length, gzipBytes: gzipSync(bytes, { level: 9 }).length })
  }
  const loginNew = [...reached].filter((path) => !initial.has(path)).sort()
  return {
    scope:
      'Built anonymous login JS/CSS graph and gzip estimate; not browser transfer or rendering time',
    entry: normalize(entry),
    assetCount: files.length,
    jsCount: files.filter((file) => file.path.endsWith('.js')).length,
    cssCount: files.filter((file) => file.path.endsWith('.css')).length,
    uncompressedBytes: files.reduce((total, file) => total + file.bytes, 0),
    gzipBytes: files.reduce((total, file) => total + file.gzipBytes, 0),
    chartsLoaded: files.some((file) => /(?:^|\/)charts-[^/]+\.js$/.test(file.path)),
    tableImplementationLoaded: tableImplementation,
    tableLibraryStylesLoaded: tableLibraryStyles,
    discovery: {
      initialAssets: [...initial].sort(),
      loginNewAssets: loginNew,
      initialDependenciesNotInHtmlPreloads: [...initial]
        .filter((path) => !declared.has(path))
        .sort(),
      loginDependenciesNotInPreloads: loginNew.filter((path) => !preloads.has(path))
    },
    files
  }
}

export const loginBudgetFailures = (report) => {
  const failures = []
  if (report.gzipBytes > LOGIN_BUDGETS.gzipBytes)
    failures.push(
      `Login gzip estimate ${report.gzipBytes} exceeds ${LOGIN_BUDGETS.gzipBytes} bytes`
    )
  if (report.assetCount > LOGIN_BUDGETS.assetCount)
    failures.push(`Login assets ${report.assetCount} exceed ${LOGIN_BUDGETS.assetCount}`)
  if (report.chartsLoaded) failures.push('Charts entered the anonymous login graph')
  if (report.tableImplementationLoaded || report.tableLibraryStylesLoaded)
    failures.push('Element Plus table entered the anonymous login graph')
  return failures
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  const report = await analyzeLoginBundle(process.argv[2] || 'dist')
  console.log(JSON.stringify(report, null, 2))
  const failures = loginBudgetFailures(report)
  if (failures.length) {
    console.error(failures.join('\n'))
    process.exitCode = 1
  }
}
