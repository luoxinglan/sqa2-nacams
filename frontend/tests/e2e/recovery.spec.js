import { test, expect } from './fixture.js'
test('初次核验网络失败提供重试页，恢复后进入原目标', async ({ page, api }) => {
  api.errors['GET /auth/me'] = { code: 500, message: '服务暂不可用' }
  await api.login('student', '/me/books')
  await expect(
    page.getByText('登录状态核验失败', { exact: true }),
  ).toBeVisible()
  delete api.errors['GET /auth/me']
  await page.getByRole('button', { name: '重新核验' }).click()
  await expect(
    page.getByRole('heading', { name: '图书目录', exact: true }),
  ).toBeVisible()
})
