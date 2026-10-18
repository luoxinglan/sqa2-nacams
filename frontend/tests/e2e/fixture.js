import { test as base, expect } from '@playwright/test'

export const accounts = {
  student: {
    id: 5,
    username: 'student',
    name: '测试学生',
    role: 'user',
    identityType: 'student',
    studentNo: '3120260001',
    active: true,
    isDirector: false,
    isLibrarian: false,
    email: 'student@example.invalid',
  },
  teacher: {
    id: 4,
    username: 'teacher',
    name: '测试教师',
    role: 'user',
    identityType: 'teacher',
    teacherType: 'full',
    employeeNo: '6120100001',
    active: true,
  },
  external: {
    id: 6,
    username: 'external',
    name: '兼职教师',
    role: 'user',
    identityType: 'teacher',
    teacherType: 'external',
    appointmentNo: 'TEST-1',
    appointmentTerm: '2026',
    active: true,
  },
  librarian: {
    id: 3,
    username: 'librarian',
    name: '图书教师',
    role: 'user',
    identityType: 'teacher',
    teacherType: 'full',
    isLibrarian: true,
    active: true,
  },
  director: {
    id: 2,
    username: 'director',
    name: '测试所长',
    role: 'user',
    identityType: 'teacher',
    teacherType: 'full',
    isDirector: true,
    active: true,
  },
  admin: {
    id: 1,
    username: 'admin',
    name: '测试管理员',
    role: 'admin',
    identityType: 'staff',
    active: true,
    isLibrarian: false,
    isDirector: false,
  },
}
export const test = base.extend({
  api: async ({ page }, use) => {
    const state = {
      user: accounts.student,
      calls: [],
      errors: {},
      photo: { sizeOk: true, ratioOk: true, suggestions: ['建议使用白色背景'] },
      report: {
        total: 2,
        success: 1,
        failed: 1,
        rows: [
          { row: 2, ok: true, reason: '成功' },
          { row: 3, ok: false, reason: '学号不合法' },
        ],
      },
    }
    const paged = (items, extra = {}) => ({
      items,
      total: items.length,
      page: 1,
      page_size: 20,
      ...extra,
    })
    state.books = [
      {
        id: 10,
        code: '2026001',
        title: '软件质量保障',
        author: '测试作者',
        status: '可借',
        location: '文件柜',
        publicationYear: 2026,
      },
      {
        id: 11,
        code: '2026002',
        title: '冷却测试图书',
        status: '冷却中',
        availableAt: '2026-10-23',
      },
      { id: 12, code: '2026003', title: '已借图书', status: '已借出' },
    ]
    state.contents = [
      {
        id: 31,
        title: '测试新闻',
        category: '新闻动态',
        body: '原有正文',
        author: '测试人员',
        publishedAt: '2026-10-18 10:00',
        images: [],
        videos: [],
        attachments: [],
        customField: '保留扩展字段',
      },
    ]
    await page.route('**/api/v1/**', async (route) => {
      const req = route.request(),
        url = new URL(req.url()),
        path = decodeURIComponent(url.pathname.replace('/api/v1', '')),
        method = req.method(),
        key = `${method} ${path}`
      const call = {
        method,
        path,
        params: Object.fromEntries(url.searchParams),
        headers: req.headers(),
        raw: req.postData(),
      }
      try {
        call.body = req.postDataJSON()
      } catch {
        call.body = null
      }
      state.calls.push(call)
      const reply = (data, code = 0, message = 'success') =>
        route.fulfill({
          status: code || 200,
          contentType: 'application/json',
          body: JSON.stringify({ code, message, data }),
        })
      const error = state.errors[key]
      if (error) return reply(error.data || null, error.code, error.message)
      if (path === '/site/info')
        return reply({
          name: '测试研究所',
          englishName: 'Test Institute',
          logo: '/logo.svg',
          copyright: '© 2026 测试研究所',
          links: {},
        })
      if (path === '/auth/login') {
        state.user = accounts[call.body.username] || accounts.student
        return reply({
          token: 'test-token',
          user: { ...state.user, teacherType: undefined },
        })
      }
      if (req.headers().authorization !== 'Bearer test-token')
        return reply(null, 401, '请先登录')
      if (path === '/auth/me') return reply(state.user)
      if (path === '/auth/logout' || path === '/me/password') return reply(null)
      if (path === '/me/profile') return reply({ ...state.user, ...call.body })
      if (path === '/me/photo')
        return reply({ ...state.user, photo: '/logo.svg' })
      if (path === '/books')
        return reply(
          paged(
            url.searchParams.get('keyword') === '不存在' ? [] : state.books,
          ),
        )
      if (/^\/books\/\d+\/borrow-requests$/.test(path))
        return reply({ id: 90, bookId: 10, status: 'pending' })
      if (path === '/me/borrows')
        return reply(
          paged([
            {
              id: 20,
              bookCode: '2026001',
              bookTitle: '软件质量保障',
              status: '在借',
              borrowedAt: '2026-10-18 10:00',
              dueAt: '2026-11-17 10:00',
              dueReminder: true,
              remainingDays: 3,
            },
          ]),
        )
      if (path === '/me/violations')
        return reply(
          paged([], { confirmedCount: 3, suspended: false, threshold: 3 }),
        )
      if (path === '/notifications')
        return reply(
          paged(
            [
              {
                id: 8,
                title: '到期提醒',
                body: '请及时归还图书',
                isRead: state.read || false,
                createdAt: '2026-10-18 10:00',
              },
            ],
            { unread: state.read ? 0 : 1 },
          ),
        )
      if (/^\/notifications\/\d+\/read$/.test(path)) {
        state.read = true
        return reply(null)
      }
      if (path === '/help')
        return reply({
          keyword: url.searchParams.get('keyword') || '',
          tree: [
            {
              category: '图书借阅',
              items: [
                { title: '如何借阅', body: '申请后由图书管理教师审核。' },
              ],
            },
          ],
          matches: [{ title: '检索结果', body: '<script>不执行脚本</script>' }],
        })
      if (path === '/stats/signin')
        return reply(
          paged([
            {
              id: 1,
              name: '测试学生',
              signinAt: '2026-10-18 10:00',
              signoutAt: null,
            },
          ]),
        )
      if (path === '/photos/check') return reply(state.photo)
      if (path.endsWith('/import')) return reply(state.report)
      if (path === '/admin/media')
        return reply({
          name: '测试附件.txt',
          url: '/api/v1/media/test',
          contentType: 'text/plain',
        })
      if (
        /^\/downloads\/\d+\/file$/.test(path) ||
        /^\/admin\/backups\/.+\/file$/.test(path)
      )
        return route.fulfill({
          status: 200,
          headers: {
            'Content-Type': 'application/octet-stream',
            'Content-Disposition':
              "attachment; filename*=UTF-8''%E6%B5%8B%E8%AF%95%E8%B5%84%E6%96%99.txt",
          },
          body: 'private file contents',
        })
      if (path === '/downloads')
        return reply(
          paged([
            {
              id: 40,
              title: '开题报告模板',
              category: '开题',
              fileName: '测试资料.txt',
              size: 22,
              sortOrder: 0,
            },
          ]),
        )
      if (/^\/admin\/downloads/.test(path))
        return reply({ id: 40, title: '新资料' })
      if (path === '/admin/users')
        return method === 'GET'
          ? reply(paged([accounts.student, accounts.external]))
          : reply({ ...call.body, id: 9 })
      if (/^\/admin\/users\/\d+$/.test(path)) return reply(call.body)
      if (/^\/admin\/content\//.test(path))
        return method === 'GET'
          ? reply(paged(state.contents))
          : reply({ ...call.body, id: 31 })
      if (path === '/admin/borrow-requests')
        return reply(
          paged([
            {
              id: 90,
              userName: '测试学生',
              bookTitle: '软件质量保障',
              status: url.searchParams.get('status'),
              requestedAt: '2026-10-18 10:00',
            },
          ]),
        )
      if (/^\/admin\/borrow-requests\/\d+\//.test(path))
        return reply({
          id: 90,
          status: path.endsWith('approve') ? '在借' : 'rejected',
        })
      if (path === '/admin/borrows')
        return reply(
          paged([
            {
              id: 20,
              userName: '测试学生',
              bookTitle: '软件质量保障',
              status: '已超期',
              dueAt: '2026-09-01 10:00',
              overdueDays: 31,
            },
          ]),
        )
      if (/^\/admin\/borrows\/\d+\/return$/.test(path))
        return reply({ id: 20, status: '已归还' })
      if (path === '/admin/violations')
        return method === 'GET'
          ? reply(
              paged([
                {
                  id: 50,
                  userName: '测试学生',
                  bookTitle: '软件质量保障',
                  status: url.searchParams.get('status'),
                  reason: '超期未还',
                },
              ]),
            )
          : reply({ id: call.body.id, status: 'confirmed' })
      if (path === '/admin/library/config')
        return reply({
          maxBorrow: call.body?.maxBorrow || 2,
          loanDays: 30,
          remindBeforeWorkdays: 3,
        })
      if (/^\/admin\/books/.test(path)) return reply({ id: 10, ...call.body })
      if (path === '/admin/backups')
        return reply({
          items: [
            {
              fileName: 'backup-test.json',
              size: 20,
              createdAt: '2026-10-18T10:00:00',
            },
          ],
          schedule: '0 0 2 * * *',
          zone: 'Asia/Shanghai',
          location: 'runtime/backups',
          retention: '人工归档',
        })
      if (path === '/admin/backup') return reply({ fileName: 'new.json' })
      if (path === '/admin/restore')
        return reply({ restored: true, loginRequired: true })
      return reply(null, 404, `未注册测试接口 ${key}`)
    })
    state.login = async (username = 'student', path = '/me') => {
      state.user = accounts[username]
      await page.addInitScript((user) => {
        if (!sessionStorage.getItem('test-seeded')) {
          localStorage.setItem('sqa2.token', 'test-token')
          localStorage.setItem('sqa2.user', JSON.stringify(user))
          sessionStorage.setItem('test-seeded', '1')
        }
      }, state.user)
      await page.goto(path)
    }
    await use(state)
  },
})
export { expect }
