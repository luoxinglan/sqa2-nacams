import { test, expect, accounts } from './fixture.js'

test('游客跳转登录，登录自动签到后进入工作概览', async ({ page, api }) => {
  await page.goto('/me/books')
  await expect(page).toHaveURL(/\/login\?redirect=/)
  await page
    .getByRole('textbox', { name: '用户名', exact: true })
    .fill('student')
  await page
    .getByRole('textbox', { name: '密码', exact: true })
    .fill('test-password')
  await page.getByRole('button', { name: '登 录' }).click()
  await expect(
    page.getByRole('heading', { name: '图书目录', exact: true }),
  ).toBeVisible()
  expect(api.calls.filter((c) => c.path === '/auth/login')).toHaveLength(1)
  await page.getByRole('link', { name: '工作概览', exact: true }).click()
  await expect(
    page.getByText('测试学生，欢迎回到研究所。', { exact: false }),
  ).toBeVisible()
})
test('路由权限重新查询服务端，伪造本地管理员无效', async ({ page, api }) => {
  await api.login('student', '/me')
  await page.evaluate(() =>
    localStorage.setItem('sqa2.user', JSON.stringify({ role: 'admin' })),
  )
  await page.goto('/admin/users')
  await expect(page.getByText('无权访问此页面')).toBeVisible()
  expect(api.calls.some((c) => c.path === '/admin/users')).toBe(false)
})
test('图书管理教师可进入图书域，普通管理员无图书权限', async ({
  page,
  api,
}) => {
  await api.login('librarian', '/admin/library')
  await expect(
    page.getByRole('heading', { name: '图书管理', exact: true }),
  ).toBeVisible()
  api.user = accounts.admin
  await page.goto('/admin/library')
  await expect(page.getByText('无权访问此页面')).toBeVisible()
})
test('全职教师及所长可查询统计，校外兼职不可查询', async ({ page, api }) => {
  await api.login('teacher', '/me/statistics')
  await expect(page.getByText('未签出', { exact: true })).toBeVisible()
  expect(api.calls.find((c) => c.path === '/stats/signin').params.scope).toBe(
    'mine',
  )
  api.user = accounts.director
  await page.goto('/me/statistics')
  await expect(page.getByText('全部人员', { exact: true })).toBeVisible()
  await expect
    .poll(
      () =>
        api.calls.filter((c) => c.path === '/stats/signin').at(-1)?.params
          .scope,
    )
    .toBe('all')
  api.user = accounts.external
  await page.goto('/me/statistics')
  await expect(page.getByText('无权访问此页面')).toBeVisible()
})
test('图书检索、空态、申请确认、业务冲突与冷却状态', async ({ page, api }) => {
  await api.login('student', '/me/books')
  await expect(page.getByRole('button', { name: '申请借阅' })).toHaveCount(3)
  await expect(
    page.getByRole('button', { name: '申请借阅' }).nth(1),
  ).toBeDisabled()
  await page.getByRole('textbox', { name: '图书检索' }).fill('不存在')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByText('暂无匹配图书')).toBeVisible()
  const search = api.calls.filter((c) => c.path === '/books').at(-1)
  expect(search.params).toMatchObject({
    keyword: '不存在',
    page: '1',
    page_size: '20',
  })
  await page.getByRole('textbox', { name: '图书检索' }).fill('软件')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await page.getByRole('button', { name: '申请借阅' }).first().click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByText(/回执状态：待审核/)).toBeVisible()
  api.errors['POST /books/10/borrow-requests'] = {
    code: 409,
    message: '在借图书已达上限2册，请先归还',
  }
  await page.getByRole('button', { name: '申请借阅' }).first().click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(
    page.getByRole('alert').filter({ hasText: '在借图书已达上限2册' }).first(),
  ).toBeVisible()
})
test('我的借阅显示到期提醒，3次已确认记录仍未停借', async ({ page, api }) => {
  await api.login('student', '/me/borrows')
  await expect(page.getByText('即将到期')).toBeVisible()
  await expect(page.getByText('剩余 3 天')).toBeVisible()
  await expect(page.getByText(/已确认 3 次；超过 3 次/)).toBeVisible()
  await expect(page.getByRole('button', { name: /删除|续借/ })).toHaveCount(0)
})
test('资料八类筛选与带 Bearer 凭据的文件下载', async ({ page, api }) => {
  await api.login('student', '/me/downloads')
  await page.getByRole('combobox', { name: '资料分类' }).press('ArrowDown')
  await expect(page.getByRole('option')).toHaveCount(8)
  await page.getByRole('option', { name: '专利', exact: true }).click()
  await expect
    .poll(
      () =>
        api.calls.filter((c) => c.path === '/downloads').at(-1)?.params
          .category,
    )
    .toBe('专利')
  const event = page.waitForEvent('download')
  await page.getByRole('button', { name: '下载', exact: true }).click()
  expect((await event).suggestedFilename()).toBe('测试资料.txt')
  expect(
    api.calls.find((c) => c.path === '/downloads/40/file').headers
      .authorization,
  ).toBe('Bearer test-token')
})
test('下载失败的二进制响应解码为错误，401失效清理登录', async ({
  page,
  api,
}) => {
  await api.login('student', '/me/downloads')
  api.errors['GET /downloads/40/file'] = { code: 403, message: '文件权限不足' }
  await page.getByRole('button', { name: '下载', exact: true }).click()
  await expect(
    page.getByText('文件权限不足', { exact: true }).first(),
  ).toBeVisible()
  api.errors['GET /auth/me'] = { code: 401, message: '登录已过期' }
  await page.getByRole('link', { name: '图书目录', exact: true }).click()
  await expect(page).toHaveURL(/\/login/)
  expect(
    await page.evaluate(() => localStorage.getItem('sqa2.token')),
  ).toBeNull()
})
test('档案只提交本人字段，422逐字段提示，修改密码后重新登录', async ({
  page,
  api,
}) => {
  await api.login('student', '/me/profile')
  api.errors['PUT /me/profile'] = {
    code: 422,
    message: '字段校验失败',
    data: { fields: { email: '邮箱格式不正确' } },
  }
  await page
    .getByRole('textbox', { name: '电子邮箱', exact: true })
    .fill('bad-email')
  await page.getByRole('button', { name: '保存档案', exact: true }).click()
  await expect(page.getByText('邮箱格式不正确', { exact: true })).toBeVisible()
  const body = api.calls.find((c) => c.path === '/me/profile').body
  expect(body.role).toBeUndefined()
  expect(body.studentNo).toBeUndefined()
  await page
    .getByRole('textbox', { name: '原密码', exact: true })
    .fill('old-password')
  await page
    .getByRole('textbox', { name: '新密码', exact: true })
    .fill('new-password')
  await page
    .getByRole('textbox', { name: '确认新密码', exact: true })
    .fill('new-password')
  await page.getByRole('button', { name: '更新密码', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  expect(
    await page.evaluate(() => localStorage.getItem('sqa2.token')),
  ).toBeNull()
})
test('照片必检失败时无法保存，通过后可上传本人照片', async ({ page, api }) => {
  await api.login('student', '/me/profile')
  await page
    .locator('input[type=file]')
    .setInputFiles({
      name: 'photo.png',
      mimeType: 'image/png',
      buffer: Buffer.from('fixture'),
    })
  api.photo.ratioOk = false
  await page.getByRole('button', { name: '在线检测', exact: true }).click()
  await expect(
    page.getByRole('button', { name: '保存本人照片' }),
  ).toBeDisabled()
  api.photo.ratioOk = true
  await page.getByRole('button', { name: '在线检测', exact: true }).click()
  await expect(page.getByRole('button', { name: '保存本人照片' })).toBeEnabled()
  await page.getByRole('button', { name: '保存本人照片' }).click()
  await expect(page.getByAltText('当前档案照片')).toBeVisible()
  expect(api.calls.find((c) => c.path === '/me/photo').raw).toContain(
    'name="file"',
  )
})
test('用户新增与停用，导入逐行报告不掩盖失败', async ({ page, api }) => {
  await api.login('admin', '/admin/users')
  await page.getByRole('button', { name: '新增用户', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await dialog
    .getByRole('textbox', { name: '用户名', exact: true })
    .fill('new-student')
  await dialog
    .getByRole('textbox', { name: '初始密码', exact: true })
    .fill('Replace-Me-2026')
  await dialog
    .getByRole('textbox', { name: '中文姓名', exact: true })
    .fill('测试同学')
  await dialog
    .getByRole('textbox', { name: '学号', exact: true })
    .fill('3120260002')
  await dialog.getByRole('button', { name: '保存用户', exact: true }).click()
  await expect(dialog).not.toBeVisible()
  expect(
    api.calls.find((c) => c.path === '/admin/users' && c.method === 'POST')
      .body,
  ).toMatchObject({
    username: 'new-student',
    identityType: 'student',
    isLibrarian: false,
    studentNo: '3120260002',
  })
  await page.getByRole('button', { name: '停用', exact: true }).first().click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  expect(api.calls.filter((c) => c.method === 'DELETE')).toHaveLength(0)
  await page.getByRole('button', { name: '批量导入', exact: true }).click()
  await page
    .getByRole('dialog')
    .locator('input[type=file]')
    .setInputFiles({
      name: 'users.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('username,name\nnew-student,测试'),
    })
  await page.getByRole('button', { name: '开始导入' }).click()
  await expect(page.getByText('共 2 行，成功 1 行，失败 1 行。')).toBeVisible()
  await expect(page.getByText('学号不合法', { exact: true })).toBeVisible()
})
test('内容编辑保留扩展字段与附件URL，保存走带ID接口', async ({ page, api }) => {
  await api.login('admin', '/admin/content')
  await page.getByRole('button', { name: '编辑', exact: true }).first().click()
  const dialog = page.getByRole('dialog')
  await dialog
    .getByRole('textbox', { name: '标题', exact: true })
    .fill('更新后的新闻')
  await dialog
    .locator('input[type=file]')
    .setInputFiles({
      name: '测试附件.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('test'),
    })
  await expect(dialog.getByText('附件：/api/v1/media/test')).toBeVisible()
  await dialog.getByRole('button', { name: '保存内容' }).click()
  await expect(dialog).not.toBeVisible()
  expect(
    api.calls.find(
      (c) => c.path === '/admin/content/news/31' && c.method === 'PUT',
    ).body,
  ).toMatchObject({
    title: '更新后的新闻',
    customField: '保留扩展字段',
    attachments: ['/api/v1/media/test'],
  })
})
test('借阅审核、归还、违规确认及上限设置完整操作', async ({ page, api }) => {
  await api.login('librarian', '/admin/library')
  await page.getByRole('tab', { name: '借阅申请', exact: true }).click()
  await page.getByRole('button', { name: '批准并借出', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect
    .poll(() =>
      api.calls.some((c) => c.path === '/admin/borrow-requests/90/approve'),
    )
    .toBe(true)
  await page.getByRole('tab', { name: '借阅与归还', exact: true }).click()
  await page.getByRole('button', { name: '登记归还', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect
    .poll(() => api.calls.some((c) => c.path === '/admin/borrows/20/return'))
    .toBe(true)
  await page.getByRole('tab', { name: '诚信记录', exact: true }).click()
  await page.getByRole('button', { name: '确认违规', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect
    .poll(
      () =>
        api.calls.find(
          (c) => c.path === '/admin/violations' && c.method === 'POST',
        )?.body,
    )
    .toEqual({ id: 50 })
  expect(
    api.calls.some(
      (c) => c.path.includes('violations') && c.method === 'DELETE',
    ),
  ).toBe(false)
  await page.getByRole('tab', { name: '借阅设置', exact: true }).click()
  await page.getByRole('spinbutton', { name: '每人同时在借上限' }).fill('3')
  await page.getByRole('button', { name: '保存上限' }).click()
  await expect
    .poll(
      () =>
        api.calls.find(
          (c) => c.path === '/admin/library/config' && c.method === 'PUT',
        )?.body,
    )
    .toEqual({ maxBorrow: 3 })
})
test('恢复需勾选并二次确认，取消无请求，成功后清理会话', async ({
  page,
  api,
}) => {
  await api.login('admin', '/admin/backups')
  await expect(page.getByText('runtime/backups', { exact: true })).toBeVisible()
  await page
    .locator('input[type=file]')
    .setInputFiles({
      name: 'backup.json',
      mimeType: 'application/json',
      buffer: Buffer.from('{}'),
    })
  await expect(page.getByRole('button', { name: '确认并恢复' })).toBeDisabled()
  await page
    .getByText('我已确认备份来源，并理解恢复将覆盖当前数据', { exact: true })
    .click()
  await page.getByRole('button', { name: '确认并恢复' }).click()
  await page.getByRole('button', { name: '取消', exact: true }).click()
  expect(api.calls.filter((c) => c.path === '/admin/restore')).toHaveLength(0)
  await page.getByRole('button', { name: '确认并恢复' }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page).toHaveURL(/\/login$/)
  const call = api.calls.find((c) => c.path === '/admin/restore')
  expect(call.raw).toContain('name="confirm"\r\n\r\ntrue')
  expect(
    await page.evaluate(() => localStorage.getItem('sqa2.token')),
  ).toBeNull()
})
test('失败签出保留会话供重试，成功后清理', async ({ page, api }) => {
  await api.login('student', '/me')
  api.errors['POST /auth/logout'] = { code: 500, message: '暂时无法签出' }
  await page.getByRole('button', { name: '退出并签出' }).click()
  await expect(page.getByText('暂时无法签出', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('sqa2.token'))).toBe(
    'test-token',
  )
  delete api.errors['POST /auth/logout']
  await page.getByRole('button', { name: '退出并签出' }).click()
  await expect(page).toHaveURL(/\/login$/)
})
test('通知已读与帮助索引、窄屏布局', async ({ page, api }, testInfo) => {
  await api.login('student', '/me/notifications')
  await page.getByRole('button', { name: '标记已读' }).click()
  await expect(page.getByText('未读 0 条', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: '用户帮助', exact: true }).click()
  await page.getByRole('textbox', { name: '帮助关键词' }).fill('借阅')
  await page.getByRole('button', { name: '查询索引' }).click()
  await expect(
    page.getByText('<script>不执行脚本</script>', { exact: true }),
  ).toBeVisible()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/me/books')
  await expect(
    page.getByRole('heading', { name: '图书目录', exact: true }),
  ).toBeVisible()
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true)
  await page.screenshot({
    path: testInfo.outputPath('mobile-books.png'),
    fullPage: true,
  })
})
