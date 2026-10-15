import test from 'node:test'
import assert from 'node:assert/strict'
import { formatGpa } from './format.js'

test('unknown GPA stays unknown and explicit zero is displayed as a measured value', () => {
  for (const unknown of [null, undefined, '', 'invalid']) assert.equal(formatGpa(unknown), '—')
  assert.equal(formatGpa(0), '0.00')
  assert.equal(formatGpa('0.00'), '0.00')
  assert.equal(formatGpa('3.56'), '3.56')
})
