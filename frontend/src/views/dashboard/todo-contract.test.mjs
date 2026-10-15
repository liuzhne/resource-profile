import test from 'node:test'
import assert from 'node:assert/strict'
import { todoTarget, todoStatusLabel, formatEventTime } from './todoContract.js'

test('actionable notifications use actual state labels and a route available with the AI feature setting', () => {
  assert.equal(todoTarget({ kind: 'MENTAL_WARNING', sourceId: 7 }, false), '/mental/overview')
  assert.equal(todoTarget({ kind: 'AGENT_EXCEPTION', studentId: 5 }, true), '/agent/warning')
  assert.equal(todoTarget({ kind: 'AGENT_EXCEPTION', studentId: 5 }, false), '/student/detail/5')
  assert.equal(todoStatusLabel('IN_PROGRESS'), '处理中')
  assert.equal(todoStatusLabel('REJECTED'), '待人工复核')
  assert.equal(formatEventTime('2026-10-07T15:00:00'), '2026-10-07 15:00:00')
  assert.equal(formatEventTime(null), '—')
})
