import assert from 'node:assert/strict'
import fs from 'node:fs'

const router = fs.readFileSync('src/router/index.ts', 'utf8')
const app = fs.readFileSync('src/App.vue', 'utf8')
const logs = fs.readFileSync('src/views/OperationLogsView.vue', 'utf8')
const invoiceApi = fs.readFileSync('src/api/invoices.ts', 'utf8')
const attachments = fs.readFileSync('src/components/ReportAttachments.vue', 'utf8')
const reportEditor = fs.readFileSync('src/views/ReportEditorView.vue', 'utf8')
const authStore = fs.readFileSync('src/stores/auth.ts', 'utf8')
const approvals = fs.readFileSync('src/views/ApprovalsView.vue', 'utf8')

assert.match(router, /path: '\/operation-logs'/)
assert.match(router, /roles: \['ADMIN', 'AUDITOR'\]/)
assert.match(router, /roles: \['MANAGER', 'FINANCE', 'ADMIN'\]/)
assert.match(app, /canAudit/)
for (const field of ['operatorId', 'operation', 'businessType', 'result', 'startTime', 'endTime']) {
  assert.match(logs, new RegExp(field))
}
assert.match(logs, /http\.get\('\/operation-logs'/)

for (const action of [
  'recognizeInvoice',
  'getInvoiceSuggestion',
  'confirmInvoiceSuggestion',
  'rejectInvoiceSuggestion',
]) {
  assert.match(invoiceApi, new RegExp(`function ${action}`))
}
assert.match(invoiceApi, /\/attachments\/\$\{attachmentId\}\/recognition/)
assert.match(invoiceApi, /\/invoice-suggestions\/\$\{suggestionId\}\/confirm/)
assert.match(attachments, /识别发票/)
assert.match(attachments, /确认写入/)
assert.match(attachments, /拒绝建议/)
assert.match(attachments, /savedExpenseItems/)
assert.match(reportEditor, /@confirmed="load"/)
assert.match(reportEditor, /创建草稿并继续/)
assert.match(reportEditor, /创建草稿后开放/)
assert.match(reportEditor, /Boolean\(reportId\.value\).*\['DRAFT', 'REJECTED'\]/s)
assert.match(reportEditor, /第 \$\{index \+ 1\} 条明细/)
assert.match(reportEditor, /确认提交审批吗/)
assert.doesNotMatch(fs.readFileSync('src/views/LoginView.vue', 'utf8'), /setItem\('login_user'/)
assert.match(authStore, /http\.get<LoginUser>\('\/auth\/me'\)/)
assert.match(authStore, /enableCrossTabSync/)
assert.match(approvals, /ElMessageBox\.confirm/)
assert.match(approvals, /selected\.nodeCode !== 'PAYMENT'/)
console.log('Frontend smoke checks passed')
