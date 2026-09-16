// 指数退避的回归护栏。纯 node 运行，不依赖测试框架：npm run test:backoff
import { createBackoff } from '../src/utils/backoff.js'

let pass = 0,
  fail = 0
const ok = (name, cond) => {
  cond ? pass++ : (fail++, console.log('  ✗ ' + name))
}

const b = createBackoff({ min: 2000, max: 60000 })
const seq = Array.from({ length: 7 }, () => b.next())
ok('逐次翻倍', JSON.stringify(seq.slice(0, 5)) === JSON.stringify([2000, 4000, 8000, 16000, 32000]))
ok('封顶 max', seq[5] === 60000 && seq[6] === 60000)
b.reset()
ok('reset 后回到 min', b.next() === 2000)
ok('reset 后继续翻倍', b.next() === 4000)

const other = createBackoff({ min: 2000, max: 60000 })
ok('实例之间互不影响', other.next() === 2000)

console.log(`\n通过 ${pass} / ${pass + fail}`)
process.exit(fail ? 1 : 0)
