import { test, expect, type Page } from '@playwright/test'
import fs from 'node:fs/promises'
import path from 'node:path'

const assets = path.resolve('../docs/assets')
const capture = process.env.RECORD_DEMO === 'true'
async function snapshot(page: Page, name: string) {
  if (capture) {
    await fs.mkdir(assets, { recursive: true })
    await page.screenshot({ path: path.join(assets, `${name}.png`) })
    await page.waitForTimeout(1800) // Give viewers time to read each stage in the optional recording.
  }
}
async function login(page: Page, role: string) {
  if (await page.getByRole('button', { name: '退出', exact: true }).count()) {
    await page.getByRole('button', { name: '退出', exact: true }).click()
  } else await page.goto('/login')
  await page.locator('.account-card').filter({ hasText: role }).click()
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/reports$/)
}
async function approve(page: Page, title: string, action: string) {
  await page.goto('/approvals')
  await page.getByRole('row').filter({ hasText: title }).click()
  await expect(page.locator('.detail-panel')).toContainText(title)
  await snapshot(page, action === '确认付款' ? 'payment' : 'approval')
  const endpoint = action === '确认付款' ? '/pay' : '/approve'
  const response = page.waitForResponse(r => r.url().includes('/api/approvals/') && r.url().endsWith(endpoint) && r.request().method() === 'POST')
  await page.locator('.action-row').getByRole('button', { name: action, exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: action, exact: true }).click()
  expect((await response).ok()).toBeTruthy()
  // Completed approval clears the selection; payment is handled from the reports list.
  await expect(page.locator('.detail-panel').getByRole('heading', { name: title, exact: true })).toHaveCount(0)
}

test('expense workflow: employee → manager → finance → payment → audit', async ({ page }) => {
  const title = `差旅报销演示-${Date.now()}`
  await page.goto('/login')
  await snapshot(page, 'login')
  await login(page, '员工')
  await page.getByRole('button', { name: '新建报销单', exact: true }).click()
  await page.getByPlaceholder('例如：九月上海客户拜访差旅费').fill(title)
  await page.getByPlaceholder('补充说明出差目的、费用背景等信息（选填）').fill('客户拜访交通费用；演示数据，不含真实票据。')
  await page.getByPlaceholder('说明这笔费用的用途').fill('客户拜访往返交通')
  await page.locator('.item-card .el-input-number input').fill('128.50')
  await page.getByRole('button', { name: '创建草稿', exact: true }).click()
  await expect(page).toHaveURL(/\/reports\/\d+\/edit$/)
  const id = page.url().match(/reports\/(\d+)/)![1]
  await expect(page.getByText('草稿已创建，现在可以上传附件或继续修改')).toBeVisible()
  await page.locator('input[type=file]').setInputFiles({ name: 'demo-invoice.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7\nDemo fixture - not a real invoice\n%%EOF') })
  await page.getByRole('button', { name: '上传附件', exact: true }).click()
  await expect(page.getByRole('cell', { name: 'demo-invoice.pdf', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '识别发票', exact: true }).click()
  await expect(page.locator('.suggestion-card')).toBeVisible()
  await page.getByRole('button', { name: '确认写入', exact: true }).click()
  await expect(page.locator('.suggestion-card')).toContainText('CONFIRMED')
  await page.evaluate(() => window.scrollTo(0, 0))
  await snapshot(page, 'report-editor')
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', { name: '提交审批', exact: true }).click()
  await expect(page.getByText('报销单已提交审批', { exact: true })).toBeVisible()
  await login(page, '经理')
  await approve(page, title, '通过')
  await login(page, '财务')
  await approve(page, title, '通过')
  await page.goto('/reports')
  await page.getByPlaceholder('编号或标题').fill(title)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  const paymentRow = page.getByRole('row').filter({ hasText: title })
  await expect(paymentRow).toContainText('待付款')
  await snapshot(page, 'payment')
  page.once('dialog', dialog => dialog.accept())
  await paymentRow.getByRole('button', { name: '付款', exact: true }).click()
  await expect(paymentRow).toContainText('已完成')
  await login(page, '员工')
  await page.getByPlaceholder('编号或标题').fill(title)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await expect(page.getByRole('row').filter({ hasText: title })).toContainText('已完成')
  await snapshot(page, 'reports')
  await login(page, '管理员')
  await page.goto('/operation-logs')
  await expect(page.getByRole('heading', { name: '操作日志', exact: true })).toBeVisible()
  await expect(page.getByRole('cell', { name: 'FINANCE_PAY', exact: true }).first()).toBeVisible()
  await snapshot(page, 'audit')
  // Verify the final API state and persisted approval history under the same UI login.
  const result = await page.evaluate(async reportId => {
    const response = await fetch(`/api/reports/${reportId}`, { headers: { Authorization: `Bearer ${localStorage.getItem('access_token')}` } })
    return { status: response.status, body: await response.json() }
  }, id)
  expect(result.status).toBe(200)
  expect(result.body.status).toBe('COMPLETED')
})

test('employee cannot reach administrator data, including a forged navigation route', async ({ page }) => {
  await login(page, '员工')
  await page.goto('/operation-logs')
  await expect(page).toHaveURL(/\/reports$/)
  const status = await page.evaluate(async () => (await fetch('/api/operation-logs', {
    headers: { Authorization: `Bearer ${localStorage.getItem('access_token')}` },
  })).status)
  expect(status).toBe(403)
  await expect(page.getByRole('link', { name: '操作日志', exact: true })).toHaveCount(0)
})
