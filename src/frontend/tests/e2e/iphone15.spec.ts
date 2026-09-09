import { expect, test, type Page, type TestInfo } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(new Date())

function watchBrowserErrors(page: Page) {
  const errors: string[] = []
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  page.on('pageerror', error => errors.push(error.message))
  return errors
}

async function expectIPhonePage(page: Page, errors: string[]) {
  // The iPhone 15 descriptor models a 393×852 device. Mobile Safari reserves
  // browser chrome and exposes a 393×659 CSS content viewport to the page.
  expect(page.viewportSize()).toEqual({ width: 393, height: 659 })
  expect(await page.evaluate(() => devicePixelRatio)).toBe(3)
  expect(await page.evaluate(() => ({
    touchEvents: 'ontouchstart' in window,
    coarsePointer: matchMedia('(pointer: coarse)').matches,
    noHover: matchMedia('(hover: none)').matches,
  }))).toEqual({ touchEvents: true, coarsePointer: true, noHover: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true)
  await expect(page.locator('vite-error-overlay')).toHaveCount(0)
  expect(errors).toEqual([])
}

async function attachScreenshot(page: Page, testInfo: TestInfo, name: string) {
  const screenshot = await page.screenshot({ fullPage: true })
  const evidenceDir = resolve(process.cwd(), '../../docs/qa/iphone15')
  await mkdir(evidenceDir, { recursive: true })
  await writeFile(resolve(evidenceDir, `${name}.png`), screenshot)
  await testInfo.attach(name, { body: screenshot, contentType: 'image/png' })
}

async function mockStreamer(page: Page) {
  await page.route('**/api/v1/me', route => route.fulfill({ json: { success: true, data: { userId: 'streamer01', nickname: '玲玲', role: 'STREAMER', csrfToken: 'csrf' } } }))
  await page.route('**/api/v1/booking-context**', route => route.fulfill({ json: { success: true, data: {
    selectedDate: today, recommendedDate: today, writeEnabled: true, myAppointment: null, cancelledAppointments: [],
    operationCounts: { cancelCount: 0, modifyCount: 0 }, dailySchedule: [], makeupArtists: [], teams: [], defaults: {},
    rules: { stepMinutes: 10, durationMinutes: 20, leadMinutes: 20, cancelLimit: 2, modifyLimit: 3 },
  } } }))
}

async function mockAdmin(page: Page) {
  await page.route('**/api/v1/me', route => route.fulfill({ json: { success: true, data: { userId: 'admin01', nickname: 'Admin', role: 'SUPER_ADMIN', canCreateAppointments: true, canModifyAppointments: true, canCancelAppointments: true, csrfToken: 'csrf' } } }))
  await page.route('**/api/v1/admin/appointments**', route => route.fulfill({ json: { success: true, data: { items: [], total: 0, page: 1, size: 30, totalPages: 0 } } }))
  await page.route('**/api/v1/admin/appointments/schedule-card/status', route => route.fulfill({ json: { success: true, data: { dates: [] } } }))
  await page.route('**/api/v1/admin/makeup-artists', route => route.fulfill({ json: { success: true, data: [] } }))
  await page.route('**/api/v1/admin/teams', route => route.fulfill({ json: { success: true, data: [] } }))
  await page.route('**/api/v1/admin/streamers', route => route.fulfill({ json: { success: true, data: [] } }))
  await page.route('**/api/v1/admin/accounts', route => route.fulfill({ json: { success: true, data: [] } }))
  await page.route('**/api/v1/admin/system-setting', route => route.fulfill({ json: { success: true, data: { enabled: true, version: 0 } } }))
}

test.describe('iPhone 15 Safari', () => {
  test('renders login and streamer pages as touch-first Mobile Safari', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'iphone15-safari', 'Safari-only scenario')
    const errors = watchBrowserErrors(page)
    await page.goto('/login')
    await expect(page.getByRole('heading', { name: '登录', exact: true })).toBeVisible()
    expect(await page.evaluate(() => navigator.userAgent)).toContain('Mobile')
    await expectIPhonePage(page, errors)

    await mockStreamer(page)
    await page.goto('/booking')
    await expect(page.getByRole('heading', { name: '今天', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '打开账号菜单' })).toBeVisible()
    await attachScreenshot(page, testInfo, 'iphone15-safari-booking')
    await expectIPhonePage(page, errors)
  })

  test('renders the admin toolbar and bottom sheet without clipping', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'iphone15-safari', 'Safari-only scenario')
    const errors = watchBrowserErrors(page)
    await mockAdmin(page)
    await page.goto('/admin')
    await expect(page.getByRole('button', { name: '筛选预约记录' })).toBeVisible()
    await page.getByRole('button', { name: '筛选预约记录' }).click()
    await expect(page.getByLabel('开始日期')).toBeVisible()
    await attachScreenshot(page, testInfo, 'iphone15-safari-admin-filter')
    await expectIPhonePage(page, errors)
  })
})

test.describe('iPhone 15 DingTalk micro-app', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    if (testInfo.project.name !== 'iphone15-dingtalk') return
    await page.addInitScript(() => {
      window.dd = {
        requestAuthCode: options => options.success({ code: 'ios-dingtalk-code' }),
      }
    })
  })

  test('uses the DingTalk JSAPI and lands a streamer on booking', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'iphone15-dingtalk', 'DingTalk-only scenario')
    const errors = watchBrowserErrors(page)
    await page.route('**/api/v1/auth/dingtalk-login', route => route.fulfill({ json: { success: true, data: { userId: 'streamer01', nickname: '玲玲', role: 'STREAMER', csrfToken: 'csrf' } } }))
    await mockStreamer(page)
    await page.goto('/login')
    expect(await page.evaluate(() => navigator.userAgent)).toContain('DingTalk')
    await page.getByRole('button', { name: '钉钉免登' }).click()
    await page.waitForURL('**/booking')
    await expect(page.getByRole('heading', { name: '今天', exact: true })).toBeVisible()
    await attachScreenshot(page, testInfo, 'iphone15-dingtalk-streamer')
    await expectIPhonePage(page, errors)
  })

  test('lands an operator on admin after DingTalk authentication', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'iphone15-dingtalk', 'DingTalk-only scenario')
    const errors = watchBrowserErrors(page)
    await page.route('**/api/v1/auth/dingtalk-login', route => route.fulfill({ json: { success: true, data: { userId: 'operator01', nickname: '运营一号', role: 'OPERATOR', canCreateAppointments: true, csrfToken: 'csrf' } } }))
    await mockAdmin(page)
    await page.goto('/login')
    await page.getByRole('button', { name: '钉钉免登' }).click()
    await page.waitForURL('**/admin')
    await expect(page.getByRole('navigation', { name: '预约日期' })).toBeVisible()
    await expectIPhonePage(page, errors)
  })

  test('shows a clear message when DingTalk rejects authorization', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'iphone15-dingtalk', 'DingTalk-only scenario')
    const errors = watchBrowserErrors(page)
    await page.goto('/login')
    await page.evaluate(() => {
      window.dd = { requestAuthCode: options => options.fail(new Error('user denied')) }
    })
    await page.getByRole('button', { name: '钉钉免登' }).click()
    await expect(page.locator('.app-toast-error')).toContainText('钉钉免登失败，请重试')
    await attachScreenshot(page, testInfo, 'iphone15-dingtalk-auth-failure')
    await expectIPhonePage(page, errors)
  })
})
