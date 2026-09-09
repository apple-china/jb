import { expect, test } from '@playwright/test'

test('Swagger UI 展示完整中文接口目录且无运行时错误', async ({ page }) => {
  const consoleErrors: string[] = []
  const pageErrors: string[] = []
  page.on('console', message => {
    if (message.type() === 'error') consoleErrors.push(message.text())
  })
  page.on('pageerror', error => pageErrors.push(error.message))

  // Springdoc generates the large annotated document lazily. Warm it explicitly
  // so a clean service start is tested without coupling UI assertions to that
  // one-time generation cost.
  const docs = await page.request.get('http://127.0.0.1:8080/v3/api-docs')
  expect(docs.ok()).toBeTruthy()
  await page.goto('http://127.0.0.1:8080/swagger-ui/index.html', { waitUntil: 'networkidle' })

  await expect(page.locator('.info .title')).toContainText('加贝云·妆造预约 API', { timeout: 15_000 })
  await expect(page.locator('.opblock-tag')).toHaveCount(9)
  await expect(page.locator('body')).toContainText('认证与会话')
  await expect(page.locator('body')).toContainText('魔点回调')
  await expect(page.locator('body')).toContainText('本地调试')
  await expect(page.locator('.vite-error-overlay, #webpack-dev-server-client-overlay')).toHaveCount(0)
  expect(pageErrors).toEqual([])
  expect(consoleErrors).toEqual([])
})
