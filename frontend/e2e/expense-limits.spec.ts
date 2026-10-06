import { test, expect, type Page } from '@playwright/test'
import fs from 'node:fs/promises'
import path from 'node:path'

async function login(page: Page, role: string) {
  const logout = page.getByRole('button', { name: '退出', exact: true })
  if (await logout.count()) await logout.click()
  else await page.goto('/login')
  await page.locator('.account-card').filter({ hasText: role }).click()
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page).toHaveURL(/\/reports$/)
}

test('expense limit: admin rule → employee reason → reviewer snapshot', async ({ page }) => {
  const title = `超限流程演示-${Date.now()}`
  await login(page, '管理员')
  const original = await page.evaluate(async () => {
    const data = await (await fetch('/api/expense-policies', {
      headers: { Authorization: `Bearer ${localStorage.getItem('access_token')}` },
    })).json()
    return data.find((p: any) => p.expenseType === 'TRANSPORT') as { limitAmount: number; enabled: boolean }
  })
  try {
    await page.getByRole('link', { name: '费用规则', exact: true }).click()
    const row = page.getByRole('row').filter({ hasText: '交通出行' })
    await row.locator('.el-input-number input').fill('200')
    if (!original.enabled) await row.getByRole('switch').click()
    await row.getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.getByText('交通出行规则已保存')).toBeVisible()
    await login(page, '员工')
    await expect(page.getByRole('link', { name: '费用规则', exact: true })).toHaveCount(0)
    const forbidden = await page.evaluate(async () => (await fetch('/api/expense-policies/TRANSPORT', {
      method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('access_token')}` },
      body: JSON.stringify({ limitAmount: 9999, enabled: false }),
    })).status)
    expect(forbidden).toBe(403)
    await page.getByRole('button', { name: '新建报销单', exact: true }).click()
    await page.getByPlaceholder('例如：九月上海客户拜访差旅费').fill(title)
    await page.locator('.item-card .el-select').click()
    await page.getByRole('option', { name: '交通出行', exact: true }).click()
    await page.getByPlaceholder('说明这笔费用的用途').fill('临时客户拜访交通费')
    await page.locator('.item-card .el-input-number input').fill('250')
    await page.getByRole('button', { name: '创建草稿', exact: true }).click()
    await expect(page).toHaveURL(/\/reports\/\d+\/edit$/)
    const reportId = page.url().match(/reports\/(\d+)/)![1]
    await expect(page.getByText('本条金额超过单笔限额 200 元，请填写原因供审批人核查')).toBeVisible()
    await page.getByRole('button', { name: '提交审批', exact: true }).click()
    await expect(page.getByText('第 1 条费用明细超过单笔限额，请填写超限原因后再提交', { exact: true })).toBeVisible()
    await page.getByPlaceholder('说明超出限额的业务原因，供审批人核查').fill('客户临时变更地点，需改签车票')
    page.once('dialog', d => d.accept())
    await page.getByRole('button', { name: '提交审批', exact: true }).click()
    await expect(page.getByText('报销单已提交审批', { exact: true })).toBeVisible()
    await login(page, '管理员')
    await page.goto('/expense-policies')
    await page.getByRole('row').filter({ hasText: '交通出行' }).locator('.el-input-number input').fill('500')
    await page.getByRole('row').filter({ hasText: '交通出行' }).getByRole('button', { name: '保存', exact: true }).click()
    await expect(page.getByText('交通出行规则已保存')).toBeVisible()
    await login(page, '经理')
    await page.goto('/approvals')
    await page.getByRole('row').filter({ hasText: title }).click()
    await expect(page.locator('.detail-panel')).toContainText('客户临时变更地点，需改签车票')
    await expect(page.locator('.detail-panel').getByRole('cell', { name: '200', exact: true })).toBeVisible()
    await expect(page.locator('.detail-panel').getByRole('cell', { name: '超限', exact: true })).toBeVisible()
    await expect(page.locator('.action-row').getByRole('button', { name: '通过', exact: true })).toBeEnabled()
    if (process.env.CAPTURE_LIMIT_DEMO === 'true') {
      await fs.mkdir(path.resolve('../docs/assets'), { recursive: true })
      await page.screenshot({ path: path.resolve('../docs/assets/expense-limit-approval.png'), animations: 'disabled' })
    }
    // End the demo in REJECTED so it does not add a permanently pending approval task.
    await page.getByPlaceholder('填写审批意见（驳回时必填）').fill('演示结束，请按实际费用规则补充材料')
    await page.locator('.action-row').getByRole('button', { name: '驳回', exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '驳回', exact: true }).click()
    await expect(page.locator('.detail-panel').getByRole('heading', { name: title, exact: true })).toHaveCount(0)
    await page.goto(`/reports/${reportId}/edit`)
    await expect(page.getByText('单笔限额：500 元', { exact: true })).toBeVisible()
  } finally {
    await login(page, '管理员')
    const restored = await page.evaluate(async policy => (await fetch('/api/expense-policies/TRANSPORT', {
      method: 'PUT', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('access_token')}` },
      body: JSON.stringify(policy),
    })).status, original)
    expect(restored).toBe(200)
  }
})
