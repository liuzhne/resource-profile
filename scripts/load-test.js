// ============================================================
// 通用业务 HTTP 压测（k6）—— 互补 scripts/bench_agent.sh（后者专测 Agent 链）。
// 覆盖：登录取 token → 鉴权后打 student/list、data/dashboard、auth/userInfo。
//
// 运行（任选）：
//   k6 run scripts/load-test.js
//   docker run --rm -i --network host -e BASE_URL=http://localhost:8080 grafana/k6 run - < scripts/load-test.js
//
// 环境变量：
//   BASE_URL  默认 http://localhost:8080（经 nginx 走 https://<域名>/api）
//   USER/PASS 登录账号，默认 admin/admin（生产改密后用真实账号）
//   VUS / DURATION 覆盖默认压测形态
// ============================================================
import http from 'k6/http'
import { check, sleep } from 'k6'

const BASE = __ENV.BASE_URL || 'http://localhost:8080'
const USER = __ENV.USER || 'admin'
const PASS = __ENV.PASS || 'admin'

export const options = __ENV.VUS
  ? { vus: Number(__ENV.VUS), duration: __ENV.DURATION || '1m' }
  : {
      stages: [
        { duration: '30s', target: 20 }, // 爬坡
        { duration: '1m', target: 20 }, // 稳态
        { duration: '30s', target: 0 } // 收尾
      ],
      thresholds: {
        http_req_failed: ['rate<0.01'], // 失败率 < 1%
        http_req_duration: ['p(95)<800'] // P95 < 800ms
      }
    }

export function setup() {
  const res = http.post(`${BASE}/auth/login`, JSON.stringify({ username: USER, password: PASS }), {
    headers: { 'Content-Type': 'application/json' }
  })
  check(res, { 'login 200': (r) => r.status === 200 })
  const token = res.json('data.token')
  if (!token) throw new Error(`登录未取到 token：${res.status} ${res.body}`)
  return { token }
}

export default function (data) {
  const params = { headers: { Authorization: `Bearer ${data.token}` } }

  const list = http.get(`${BASE}/student/list?page=1&size=10`, params)
  check(list, { 'student/list 200': (r) => r.status === 200 })

  const dash = http.get(`${BASE}/data/dashboard/statistics`, params)
  check(dash, { 'dashboard/statistics 200': (r) => r.status === 200 })

  const me = http.get(`${BASE}/auth/userInfo`, params)
  check(me, { 'auth/userInfo 200': (r) => r.status === 200 })

  sleep(1)
}
