const { chromium } = require('@playwright/test')

async function main() {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage({ viewport: { width: 360, height: 800 } })
  const errors = []
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: /超管/ }).click()
  await page.waitForURL('**/admin')
  await page.getByRole('button', { name: '筛选预约记录' }).waitFor()
  const toolbarLabels = await page.locator('.record-actions .record-action').allTextContents()
  const toolbarBox = await page.locator('.record-results').boundingBox()
  await page.getByRole('button', { name: '筛选预约记录' }).click()
  const filterBox = await page.locator('.record-filter').boundingBox()
  const filterBelowToolbar = !!toolbarBox && !!filterBox && filterBox.y >= toolbarBox.y + toolbarBox.height
  await page.getByLabel('开始日期').click()
  await page.locator('.calendar-popover').waitFor()
  const calendarVisible = await page.locator('.calendar-popover').isVisible()
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '重置' }).click()
  const filterStillOpenAfterReset = await page.locator('.record-filter').isVisible()
  await page.getByRole('button', { name: '发送钉钉群卡片' }).click()
  const cardDialog = page.getByRole('dialog', { name: '钉钉群卡片' })
  await cardDialog.waitFor()
  const cardSubmitLabel = (await cardDialog.locator('.card-submit').innerText()).trim()
  await cardDialog.getByRole('button', { name: '关闭' }).click()
  let detailSections = []
  const firstAppointment = page.locator('.mobile-admin-list > button:visible').first()
  if (await firstAppointment.count()) {
    await firstAppointment.click()
    const detailDialog = page.getByRole('dialog', { name: '预约详情' })
    await detailDialog.waitFor()
    detailSections = (await detailDialog.locator('.detail-sections section > h3').allTextContents()).map(value => value.trim())
    await detailDialog.getByRole('button', { name: '关闭' }).click()
  }
  await page.locator('.admin-bottom button').filter({ hasText: '设置' }).click()
  await page.getByRole('button', { name: '添加管理员' }).click()
  const adminDialog = page.locator('.sheet:visible')
  await adminDialog.waitFor()
  const adminDialogText = (await adminDialog.innerText()).trim()
  const hasAdminRoleSelect = await adminDialog.getByRole('combobox', { name: '角色' }).count() === 1
  await adminDialog.getByRole('button', { name: '关闭' }).click()
  await page.getByRole('button', { name: '添加主播' }).click()
  await page.getByLabel('搜索钉钉员工').click()
  await page.getByRole('listbox', { name: '钉钉员工选项' }).waitFor()
  const pageResult = await page.evaluate(() => ({
    hasDingTalkPanel: !!document.querySelector('[aria-label="钉钉员工选项"]'),
    hasErrorOverlay: !!document.querySelector('.vite-error-overlay,#webpack-dev-server-client-overlay'),
    bodyLength: document.body.innerText.trim().length,
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }))
  const sheetResult = await page.locator('.sheet').evaluate(sheet => ({
    scrollWidth: sheet.scrollWidth,
    clientWidth: sheet.clientWidth,
    bodyCanScroll: !!sheet.querySelector('.sheet-body') && sheet.querySelector('.sheet-body').scrollHeight >= sheet.querySelector('.sheet-body').clientHeight,
  }))
  const result = {
    ...pageResult,
    toolbarLabels: toolbarLabels.map(label => label.trim()),
    filterBelowToolbar,
    calendarVisible,
    filterStillOpenAfterReset,
    cardSubmitLabel,
    detailSections,
    hasAdminRoleSelect,
    adminDialogText,
    sheetResult,
  }
  await page.screenshot({ path: '../../.tools/settings-account-v4.png', fullPage: true })
  await browser.close()
  console.log(JSON.stringify({ result, errors }))
  if (!result.hasDingTalkPanel || result.hasErrorOverlay || errors.length || result.scrollWidth > result.clientWidth ||
      result.toolbarLabels.join(',') !== '筛选,规则,导出,发卡' || !result.filterBelowToolbar ||
      !result.calendarVisible || !result.filterStillOpenAfterReset || !['确认发送','刷新卡片'].includes(result.cardSubmitLabel) ||
      (result.detailSections.length > 0 && result.detailSections.join(',') !== '预约标识,基础信息,操作信息,修改记录,其他数据') ||
      !result.hasAdminRoleSelect ||
      result.sheetResult.scrollWidth > result.sheetResult.clientWidth || !result.sheetResult.bodyCanScroll) process.exitCode = 1
}

main().catch(error => { console.error(error); process.exit(1) })
