import test from 'node:test'
import assert from 'node:assert/strict'
import { MAX_UPLOAD_BYTES, validateUpload, validateUploads, UPLOAD_ACCEPT } from '../src/lib/asset-upload.ts'

test('file selection accepts ZIP and supported images but rejects unsafe sizes and paths', () => {
  for (const name of ['bundle.zip', 'cover.PNG', 'cover.jpg', 'cover.webp', 'guide.pdf', 'scripts/check.py']) {
    assert.equal(validateUpload({ name, size: 32 }), '')
  }
  for (const name of ['payload.exe', 'payload.svg', 'payload.html', '../file.txt', 'NUL.txt', 'folder/file.txt.', '/root/file.txt']) {
    assert.ok(validateUpload({ name, size: 32 }))
  }
  assert.ok(validateUpload({ name: 'file.txt', size: 0 }))
  assert.ok(validateUpload({ name: 'file.txt', size: MAX_UPLOAD_BYTES + 1 }))
  assert.ok(UPLOAD_ACCEPT.includes('.zip'))
  assert.ok(UPLOAD_ACCEPT.includes('.png'))
})

test('upload batches reject duplicate normalized paths before any network writes', () => {
  const file = { name: 'guide.txt', size: 32 }
  assert.ok(validateUploads([{ file, relativePath: 'Docs/guide.txt' }, { file, relativePath: 'docs\\guide.txt' }]))
  assert.equal(validateUploads([{ file, relativePath: 'docs/guide.txt' }]), '')
})
