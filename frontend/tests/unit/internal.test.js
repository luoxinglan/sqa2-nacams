import test from 'node:test'
import assert from 'node:assert/strict'
import {
  canAccess,
  safeRedirect,
  downloadName,
  fileSize,
} from '../../src/utils/internal.js'
import { userPayload } from '../../src/utils/profileFields.js'

test('图书权限只取明确标记，管理员不自动成为图书管理教师', () => {
  assert.equal(canAccess({ role: 'admin' }, 'librarian'), false)
  assert.equal(
    canAccess({ role: 'user', isLibrarian: true }, 'librarian'),
    true,
  )
  assert.equal(
    canAccess({ role: 'user', isLibrarian: 'false' }, 'librarian'),
    false,
  )
  assert.equal(canAccess(null, 'admin'), false)
})
test('统计只向所长与全职教师开放', () => {
  assert.equal(
    canAccess({ identityType: 'teacher', teacherType: 'full' }, 'statistics'),
    true,
  )
  assert.equal(
    canAccess(
      { identityType: 'teacher', teacherType: 'external' },
      'statistics',
    ),
    false,
  )
  assert.equal(
    canAccess({ identityType: 'student', teacherType: 'full' }, 'statistics'),
    false,
  )
  assert.equal(canAccess({ isDirector: true }, 'statistics'), true)
})
test('登录后只允许站内路径，拒绝外部、协议相对、反斜线与控制字符跳转', () => {
  for (const value of [
    'https://example.invalid',
    '//example.invalid',
    '/\\evil',
    '/\nevil',
    '/login?redirect=/me',
    ['//evil'],
  ])
    assert.equal(safeRedirect(value), '/me')
  assert.equal(safeRedirect('/me/books?keyword=安全'), '/me/books?keyword=安全')
})
test('下载文件名保留 UTF8 中文并移除目录分隔符', () => {
  assert.equal(
    downloadName(
      "attachment; filename*=UTF-8''%E8%B5%84%E6%96%99.docx",
      'fallback',
    ),
    '资料.docx',
  )
  assert.equal(
    downloadName('attachment; filename="../data.csv"', 'fallback'),
    '.._data.csv',
  )
  assert.equal(
    downloadName("attachment; filename*=UTF-8''%zz", 'fallback'),
    'fallback',
  )
  assert.equal(fileSize(0), '0 B')
})
test('本人更新严格白名单，省略可选数字空值并保留布尔假值', () => {
  assert.deepEqual(
    userPayload(
      {
        role: 'admin',
        email: 'a@example.invalid',
        advisorUserId: '',
        active: false,
      },
      ['email', 'advisorUserId', 'active'],
    ),
    { email: 'a@example.invalid', active: false },
  )
})
