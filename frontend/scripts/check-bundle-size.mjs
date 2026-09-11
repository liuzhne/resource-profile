import { readFile, readdir, stat } from 'node:fs/promises'
import { resolve } from 'node:path'

const assetsDir = resolve('dist/assets')
const limits = {
  entryKiB: 500,
  chunkKiB: 1200,
  totalJavaScriptKiB: 2500
}

const files = (await readdir(assetsDir)).filter((name) => name.endsWith('.js'))
const sizes = await Promise.all(
  files.map(async (name) => ({ name, bytes: (await stat(resolve(assetsDir, name))).size }))
)

const failures = []
const indexHtml = await readFile(resolve('dist/index.html'), 'utf8')
const entryName = indexHtml.match(/src="\/assets\/(index-[^"]+\.js)"/)?.[1]
const entry = sizes.find(({ name }) => name === entryName)
if (!entry) {
  failures.push('未找到 dist/assets/index-*.js 业务入口')
} else if (entry.bytes > limits.entryKiB * 1024) {
  failures.push(`${entry.name} 超过业务入口预算 ${limits.entryKiB} KiB`)
}

for (const chunk of sizes) {
  if (chunk.bytes > limits.chunkKiB * 1024) {
    failures.push(`${chunk.name} 超过单 chunk 预算 ${limits.chunkKiB} KiB`)
  }
}

const totalBytes = sizes.reduce((sum, chunk) => sum + chunk.bytes, 0)
if (totalBytes > limits.totalJavaScriptKiB * 1024) {
  failures.push(`JavaScript 总量超过预算 ${limits.totalJavaScriptKiB} KiB`)
}

const kib = (bytes) => (bytes / 1024).toFixed(1)
for (const chunk of sizes.sort((a, b) => b.bytes - a.bytes)) {
  console.log(`${chunk.name}: ${kib(chunk.bytes)} KiB`)
}
console.log(`total: ${kib(totalBytes)} KiB`)

if (failures.length > 0) {
  console.error(failures.join('\n'))
  process.exitCode = 1
}
