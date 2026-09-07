import assert from 'node:assert/strict'
import test from 'node:test'
import { normalizePortalFilters, safeReturnUrl } from '../src/lib/portal-navigation.ts'

test('portal filters preserve valid URL state', () => {
  assert.deepEqual(normalizePortalFilters({ q: '  API  ', type: 'RULE', scope: 'PROJECT', page: '2' }), {
    q: 'API', type: 'RULE', scope: 'PROJECT', page: 2
  })
})

test('portal filters reject malformed and repeated query values', () => {
  for (const page of ['NaN', '-1', '1.2', 'Infinity', '2147483648', ['2']]) {
    assert.deepEqual(normalizePortalFilters({ q: ['private'], type: 'DRAFT', scope: 'unknown', page }), {
      q: '', type: '', scope: '', page: 0
    })
  }
})

test('login return URLs stay local and cannot create an authentication loop', () => {
  assert.equal(safeReturnUrl('/assets-public/123?versionNo=2#body'), '/assets-public/123?versionNo=2#body')
  for (const value of [undefined, ['test'], '//example.com', '/\\example.com', 'https://example.com', '/login?returnUrl=/login', '/change-password', '/%6Cogin']) {
    assert.equal(safeReturnUrl(value), '/admin')
  }
})
