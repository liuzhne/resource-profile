import assert from 'node:assert/strict'
import { readFile, readdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { runInNewContext } from 'node:vm'
import { parse } from '@vue/compiler-sfc'
import { appIcons, installAppIcons } from '../src/icons.js'

const sourceDir = fileURLToPath(new URL('../src/', import.meta.url))
const libraryTypes = await readFile(
  new URL(
    '../node_modules/@element-plus/icons-vue/dist/types/components/index.d.ts',
    import.meta.url
  ),
  'utf8'
)
const libraryNames = new Set(
  [...libraryTypes.matchAll(/default as (\w+)/g)].map((match) => match[1])
)
const sources = []
const readSources = async (directory) => {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name)
    if (entry.isDirectory()) await readSources(path)
    else if (/\.(vue|js)$/.test(entry.name))
      sources.push({ path, content: await readFile(path, 'utf8') })
  }
}
await readSources(sourceDir)

const required = new Set()
for (const { path, content } of sources) {
  const localIcons = new Set()
  for (const match of content.matchAll(
    /import\s*\{([^}]+)\}\s*from\s*['"]@element-plus\/icons-vue['"]/g
  )) {
    for (const name of match[1].match(/\b\w+\b/g) || []) {
      if (libraryNames.has(name)) localIcons.add(name)
    }
  }
  // Dynamic string names must remain resolvable by SidebarItem, StatisticCard,
  // mental overview and the login brand. Check both AI-enabled/disabled routes.
  for (const match of content.matchAll(/icon\s*:\s*['"](\w+)['"]/g)) {
    assert.ok(libraryNames.has(match[1]), `${path}: unknown dynamic icon ${match[1]}`)
    required.add(match[1])
  }
  const template = path.endsWith('.vue')
    ? parse(content, { filename: path }).descriptor.template?.content || ''
    : ''
  for (const match of template.matchAll(/<([A-Z]\w*)\b/g)) {
    if (libraryNames.has(match[1]) && !localIcons.has(match[1])) required.add(match[1])
  }
}
for (const name of required) assert.ok(appIcons[name], `Missing globally resolved icon: ${name}`)
for (const name of Object.keys(appIcons))
  assert.ok(required.has(name), `Unused global icon: ${name}`)
assert.ok(
  Object.keys(appIcons).length < libraryNames.size / 4,
  'Avoid restoring the full icon library'
)
const registered = new Map()
installAppIcons({ component: (name, component) => registered.set(name, component) })
assert.equal(registered.size, required.size)
for (const name of required) assert.equal(registered.get(name), appIcons[name])

// Exercise the actual bootstrap with stubbed framework dependencies. A default,
// false or malformed flag must mount without registering wakeup handlers; true
// explicitly preserves the sleeping-deployment compatibility branch.
const main = (await readFile(resolve(sourceDir, 'main.js'), 'utf8'))
  .replace(/^import[^\n]*\n/gm, '')
  .replace(/import\.meta\.env/g, 'environment')
for (const flag of [undefined, 'false', 'TRUE', '1', 'true']) {
  const events = []
  const app = { use: () => app, mount: () => events.push('mount') }
  runInNewContext(main, {
    environment: { VITE_ENABLE_SERVICE_WARMUP: flag },
    createApp: () => app,
    createPinia: () => ({}),
    App: {},
    router: {},
    permissionDirective: {},
    installAppIcons: () => events.push('icons'),
    wakeServices: () => {},
    installServiceWarmup: () => events.push('warmup')
  })
  assert.ok(events.includes('mount'))
  assert.equal(events.includes('warmup'), flag === 'true')
}
console.log(
  `Icon contract passed: ${required.size} registered icons cover dynamic/template usage; warmup opt-in verified`
)
