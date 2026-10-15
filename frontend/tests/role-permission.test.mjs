import assert from 'node:assert/strict'
import {
  isSupportedPermission,
  supportedSelection
} from '../src/views/admin/rolePermissionContract.js'

const catalog = [
  { id: 1, code: 'teacher:write', allowedRoles: ['admin'] },
  { id: 2, code: 'student:write', allowedRoles: ['admin', 'teacher'] },
  { id: 3, code: 'mental:write', allowedRoles: ['admin', 'psychologist'] }
]
assert.equal(isSupportedPermission(catalog[0], 'teacher'), false)
assert.equal(isSupportedPermission(catalog[1], 'teacher'), true)
assert.equal(isSupportedPermission(catalog[1], 'psychologist'), false)
assert.deepEqual(supportedSelection(catalog, 'teacher', ['1', 2, '3']), ['2'])
assert.deepEqual(supportedSelection(catalog, 'student', [1, 2, 3]), [])
assert.deepEqual(supportedSelection(catalog, 'admin', []), ['1', '2', '3'])
assert.equal(isSupportedPermission({ id: 4 }, 'teacher'), false)
console.log('role permission upper-bound contract: PASS')
