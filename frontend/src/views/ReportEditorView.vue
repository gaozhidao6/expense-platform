<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { http } from '@/api/http'
import ReportAttachments from '@/components/ReportAttachments.vue'

interface ExpenseItemForm {
  id?: number
  expenseDate: string
  expenseType: string
  merchant: string
  description: string
  amount: number
  taxAmount: number
  sortOrder: number
}

const route = useRoute()
const router = useRouter()
const reportId = computed(() => route.params.id ? Number(route.params.id) : null)
const isNewReport = computed(() => !reportId.value)
const form = reactive({ title: '', description: '', currency: 'CNY' })
const items = ref<ExpenseItemForm[]>([])
const saving = ref(false)
const submitting = ref(false)
const loading = ref(false)
const message = ref('')
const messageType = ref<'success' | 'warning' | 'error' | 'info'>('info')
const submitIdempotencyKey = ref<string | null>(null)
const reportStatus = ref('DRAFT')

const expenseTypes = [
  { value: 'TRAVEL', label: '差旅住宿' },
  { value: 'MEAL', label: '餐饮招待' },
  { value: 'TRANSPORT', label: '交通出行' },
  { value: 'OFFICE', label: '办公用品' },
  { value: 'OTHER', label: '其他费用' },
]

const editable = computed(() =>
  isNewReport.value || ['DRAFT', 'REJECTED'].includes(reportStatus.value),
)
const canSubmit = computed(() =>
  Boolean(reportId.value) && ['DRAFT', 'REJECTED'].includes(reportStatus.value),
)
const canWithdraw = computed(() =>
  Boolean(reportId.value) && reportStatus.value === 'APPROVING',
)
const total = computed(() =>
  items.value.reduce((sum, item) => sum + Number(item.amount || 0), 0).toFixed(2),
)
const completedItemCount = computed(() =>
  items.value.filter((item) => item.expenseDate
    && item.expenseType
    && item.description.trim()
    && Number(item.amount) > 0).length,
)
const statusPresentation = computed(() => {
  const map: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
    DRAFT: { label: '草稿', type: 'info' },
    REJECTED: { label: '已驳回，可修改', type: 'danger' },
    APPROVING: { label: '经理审批中', type: 'warning' },
    FINANCE_REVIEW: { label: '财务审核中', type: 'warning' },
    PENDING_PAYMENT: { label: '等待付款', type: 'warning' },
    COMPLETED: { label: '已完成', type: 'success' },
    WITHDRAWN: { label: '已撤回', type: 'info' },
  }
  return map[reportStatus.value] ?? { label: reportStatus.value, type: 'info' as const }
})

function today() { return new Date().toISOString().slice(0, 10) }
function blankItem(): ExpenseItemForm {
  return { expenseDate: today(), expenseType: 'OTHER', merchant: '', description: '', amount: 0, taxAmount: 0, sortOrder: items.value.length }
}
function showMessage(text: string, type: 'success' | 'warning' | 'error' | 'info' = 'info') {
  message.value = text
  messageType.value = type
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
function validate() {
  if (!form.title.trim()) return '请填写报销标题'
  if (!items.value.length) return '请至少添加一条费用明细'
  for (let index = 0; index < items.value.length; index += 1) {
    const item = items.value[index]
    const prefix = `第 ${index + 1} 条明细：`
    if (!item.expenseDate) return `${prefix}请选择费用日期`
    if (item.expenseDate > today()) return `${prefix}费用日期不能晚于今天`
    if (!item.expenseType) return `${prefix}请选择费用类型`
    if (!item.description.trim()) return `${prefix}请填写费用说明`
    if (Number(item.amount) <= 0) return `${prefix}金额必须大于 0`
  }
  return ''
}

async function load() {
  if (!reportId.value) { items.value = [blankItem()]; return }
  loading.value = true
  try {
    const { data } = await http.get(`/reports/${reportId.value}`)
    Object.assign(form, { title: data.title, description: data.description || '', currency: data.currency })
    reportStatus.value = data.status
    items.value = (data.items || []).map((item: ExpenseItemForm) => ({ ...item }))
  } catch (error: any) {
    showMessage(error.userMessage || error.response?.data?.message || '报销单加载失败', 'error')
  } finally { loading.value = false }
}

async function save() {
  const validation = validate()
  if (validation) { showMessage(validation, 'warning'); return false }
  if (!editable.value) { showMessage('当前状态不允许编辑', 'warning'); return false }
  saving.value = true
  message.value = ''
  try {
    if (!reportId.value) {
      const { data } = await http.post('/reports', { ...form, title: form.title.trim(), description: form.description.trim(), items: items.value })
      await router.replace(`/reports/${data.id}/edit`)
      reportStatus.value = data.status || 'DRAFT'
      await load()
      showMessage('草稿已创建，现在可以上传附件或继续修改', 'success')
      return true
    }
    await http.put(`/reports/${reportId.value}`, { ...form, title: form.title.trim(), description: form.description.trim() })
    for (const [index, item] of items.value.entries()) {
      const request = { ...item, sortOrder: index }
      if (item.id) await http.put(`/reports/${reportId.value}/items/${item.id}`, request)
      else await http.post(`/reports/${reportId.value}/items`, request)
    }
    await load()
    showMessage('草稿已保存', 'success')
    return true
  } catch (error: any) {
    showMessage(error.userMessage || error.response?.data?.message || '保存失败', 'error')
    return false
  } finally { saving.value = false }
}

async function removeItem(item: ExpenseItemForm, index: number) {
  if (items.value.length === 1) { showMessage('至少保留一条费用明细', 'warning'); return }
  try {
    if (item.id && reportId.value) await http.delete(`/reports/${reportId.value}/items/${item.id}`)
    items.value.splice(index, 1)
    items.value.forEach((value, itemIndex) => { value.sortOrder = itemIndex })
  } catch (error: any) {
    showMessage(error.userMessage || error.response?.data?.message || '删除明细失败', 'error')
  }
}

async function submit() {
  if (!reportId.value || submitting.value) return
  const validation = validate()
  if (validation) { showMessage(validation, 'warning'); return }
  if (!window.confirm(`确认提交审批吗？提交金额为 ${total.value} ${form.currency}。`)) return
  submitting.value = true
  submitIdempotencyKey.value ||= crypto.randomUUID()
  try {
    const saved = await save()
    if (!saved || !reportId.value) return
    await http.post(`/reports/${reportId.value}/submit`, null, { headers: { 'Idempotency-Key': submitIdempotencyKey.value } })
    submitIdempotencyKey.value = null
    await load()
    showMessage('报销单已提交审批', 'success')
  } catch (error: any) {
    showMessage(error.userMessage || error.response?.data?.message || '提交失败', 'error')
  } finally { submitting.value = false }
}

async function withdraw() {
  if (!reportId.value || !window.confirm('确认撤回这张报销单吗？')) return
  try {
    await http.post(`/reports/${reportId.value}/withdraw`)
    await load()
    showMessage('报销单已撤回', 'success')
  } catch (error: any) {
    showMessage(error.userMessage || error.response?.data?.message || '撤回失败', 'error')
  }
}
onMounted(load)
</script>

<template>
  <main v-loading="loading" class="app-page report-editor-page">
    <header class="topbar editor-topbar">
      <div>
        <p class="eyebrow">EXPENSE REPORT</p>
        <div class="title-row">
          <h1>{{ isNewReport ? '创建报销草稿' : '报销单详情' }}</h1>
          <el-tag v-if="!isNewReport" :type="statusPresentation.type">{{ statusPresentation.label }}</el-tag>
        </div>
        <p class="page-description">{{ isNewReport ? '先填写基本信息和费用明细，创建草稿后即可上传并识别发票。' : '核对明细和附件，保存无误后再提交审批。' }}</p>
      </div>
      <el-button @click="router.push('/reports')">返回列表</el-button>
    </header>

    <el-alert v-if="message" class="page-message" :title="message" :type="messageType" closable show-icon @close="message = ''" />

    <div class="progress-strip">
      <div class="progress-step is-active"><span>1</span><div><strong>填写草稿</strong><small>基本信息与费用明细</small></div></div>
      <div class="progress-line" :class="{ 'is-complete': !isNewReport }" />
      <div class="progress-step" :class="{ 'is-active': !isNewReport }"><span>2</span><div><strong>上传票据</strong><small>附件与发票识别</small></div></div>
      <div class="progress-line" :class="{ 'is-complete': !isNewReport }" />
      <div class="progress-step" :class="{ 'is-active': canSubmit }"><span>3</span><div><strong>提交审批</strong><small>确认金额后提交</small></div></div>
    </div>

    <section class="content-panel editor-section">
      <div class="section-heading"><div><span class="section-number">01</span><h2>基本信息</h2></div><small>带 * 的内容必须填写</small></div>
      <el-form label-position="top" class="basic-form">
        <el-form-item label="报销标题 *"><el-input v-model="form.title" maxlength="150" show-word-limit placeholder="例如：九月上海客户拜访差旅费" :disabled="!editable" /></el-form-item>
        <el-form-item label="报销说明"><el-input v-model="form.description" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="补充说明出差目的、费用背景等信息（选填）" :disabled="!editable" /></el-form-item>
        <el-form-item label="币种"><el-select v-model="form.currency" :disabled="!editable"><el-option label="人民币（CNY）" value="CNY" /></el-select></el-form-item>
      </el-form>
    </section>

    <section class="content-panel editor-section">
      <div class="section-heading">
        <div><span class="section-number">02</span><h2>费用明细</h2></div>
        <div class="section-meta"><span>已完整填写 {{ completedItemCount }}/{{ items.length }} 条</span><el-button v-if="editable" type="primary" plain @click="items.push(blankItem())">+ 添加一条明细</el-button></div>
      </div>
      <div class="item-list">
        <article v-for="(item, index) in items" :key="item.id ?? `new-${index}`" class="item-card">
          <header><strong>费用明细 {{ index + 1 }}</strong><el-button v-if="editable" link type="danger" @click="removeItem(item, index)">删除</el-button></header>
          <div class="item-grid">
            <label><span>费用日期 *</span><el-date-picker v-model="item.expenseDate" type="date" value-format="YYYY-MM-DD" :disabled-date="(date: Date) => date.getTime() > Date.now()" :disabled="!editable" /></label>
            <label><span>费用类型 *</span><el-select v-model="item.expenseType" :disabled="!editable"><el-option v-for="type in expenseTypes" :key="type.value" :label="type.label" :value="type.value" /></el-select></label>
            <label><span>商户名称</span><el-input v-model="item.merchant" placeholder="例如：上海虹桥酒店" :disabled="!editable" /></label>
            <label><span>金额（{{ form.currency }}）*</span><el-input-number v-model="item.amount" :min="0" :precision="2" :step="10" controls-position="right" :disabled="!editable" /></label>
            <label class="description-field"><span>费用说明 *</span><el-input v-model="item.description" maxlength="500" placeholder="说明这笔费用的用途" :disabled="!editable" /></label>
          </div>
        </article>
      </div>
      <div class="amount-summary"><span>当前合计</span><strong>{{ total }}</strong><small>{{ form.currency }}</small></div>
    </section>

    <section v-if="reportId" class="content-panel editor-section">
      <div class="section-heading simple-heading"><div><span class="section-number">03</span><h2>票据与发票识别</h2></div></div>
      <ReportAttachments :report-id="reportId" :editable="editable" :recognition-enabled="reportStatus === 'DRAFT'" :expense-items="items" @confirmed="load" />
    </section>
    <section v-else class="locked-section">
      <div class="locked-icon">03</div><div><h2>票据与发票识别</h2><p>创建草稿后开放。这样附件才能准确关联到这张报销单。</p></div>
      <el-button type="primary" :loading="saving" @click="save">创建草稿并继续</el-button>
    </section>

    <footer class="action-bar">
      <div class="action-total"><span>报销总额</span><strong>{{ total }} {{ form.currency }}</strong></div>
      <div class="action-buttons">
        <el-button @click="router.push('/reports')">取消</el-button>
        <el-button v-if="editable" type="primary" plain :loading="saving" @click="save">{{ isNewReport ? '创建草稿' : '保存修改' }}</el-button>
        <el-button v-if="canSubmit" type="success" :loading="submitting" @click="submit">提交审批</el-button>
        <el-button v-if="canWithdraw" @click="withdraw">撤回报销单</el-button>
      </div>
    </footer>
  </main>
</template>

<style scoped>
.report-editor-page { max-width: 1180px; margin: 0 auto; padding-bottom: 100px; }
.editor-topbar { align-items: flex-start; }
.title-row { display: flex; align-items: center; flex-wrap: wrap; gap: 12px; }
.title-row h1 { margin: 0; }
.page-description { margin: 8px 0 0; color: #64748b; }
.page-message { margin: 18px 0; }
.progress-strip { display: flex; align-items: center; margin: 20px 0; padding: 18px 22px; border: 1px solid #e2e8f0; border-radius: 14px; background: #fff; }
.progress-step { display: flex; align-items: center; gap: 10px; color: #94a3b8; }
.progress-step > span { display: grid; width: 30px; height: 30px; place-items: center; border-radius: 50%; background: #e2e8f0; font-weight: 700; }
.progress-step div { display: flex; flex-direction: column; white-space: nowrap; }
.progress-step small { margin-top: 2px; font-size: 11px; }
.progress-step.is-active { color: #0f766e; }
.progress-step.is-active > span { color: #fff; background: #0f766e; }
.progress-line { height: 2px; flex: 1; margin: 0 16px; background: #e2e8f0; }
.progress-line.is-complete { background: #5eead4; }
.editor-section { margin-top: 18px; padding: 24px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 16px; margin-bottom: 22px; }
.section-heading > div:first-child { display: flex; align-items: center; gap: 10px; }
.section-heading h2 { margin: 0; font-size: 20px; }
.section-heading small, .section-meta { color: #64748b; }
.section-number { color: #0f766e; font-size: 13px; font-weight: 800; letter-spacing: .08em; }
.basic-form { display: grid; grid-template-columns: 1fr 180px; gap: 0 20px; }
.basic-form .el-form-item:nth-child(2) { grid-column: 1 / -1; }
.basic-form :deep(.el-select) { width: 100%; }
.section-meta { display: flex; align-items: center; gap: 14px; font-size: 13px; }
.item-list { display: grid; gap: 14px; }
.item-card { padding: 18px; border: 1px solid #e2e8f0; border-radius: 12px; background: #f8fafc; }
.item-card > header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.item-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.item-grid label { display: flex; min-width: 0; flex-direction: column; gap: 7px; color: #475569; font-size: 13px; }
.item-grid :deep(.el-date-editor), .item-grid :deep(.el-select), .item-grid :deep(.el-input-number) { width: 100%; }
.description-field { grid-column: 1 / -1; }
.amount-summary { display: flex; justify-content: flex-end; align-items: baseline; gap: 8px; margin-top: 20px; }
.amount-summary span { color: #64748b; }
.amount-summary strong { color: #0f766e; font-size: 28px; }
.amount-summary small { color: #475569; font-weight: 700; }
.simple-heading { margin-bottom: -8px; }
.locked-section { display: flex; align-items: center; gap: 18px; margin-top: 18px; padding: 24px; border: 1px dashed #94a3b8; border-radius: 14px; background: #f8fafc; }
.locked-section .locked-icon { display: grid; width: 46px; height: 46px; flex: none; place-items: center; border-radius: 12px; color: #64748b; background: #e2e8f0; font-weight: 800; }
.locked-section h2 { margin: 0; font-size: 18px; }
.locked-section p { margin: 6px 0 0; color: #64748b; }
.locked-section .el-button { margin-left: auto; }
.action-bar { position: sticky; z-index: 10; bottom: 12px; display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-top: 22px; padding: 14px 18px; border: 1px solid #dbeafe; border-radius: 14px; background: rgba(255,255,255,.96); box-shadow: 0 12px 30px rgba(15,23,42,.12); backdrop-filter: blur(10px); }
.action-total { display: flex; flex-direction: column; color: #64748b; font-size: 12px; }
.action-total strong { color: #0f172a; font-size: 20px; }
.action-buttons { display: flex; gap: 10px; }
@media (max-width: 850px) { .progress-step small { display: none; } .item-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 620px) {
  .report-editor-page { padding-bottom: 130px; }
  .progress-strip { padding: 14px; }
  .progress-step div { display: none; }
  .progress-line { margin: 0 8px; }
  .editor-section { padding: 16px; }
  .section-heading, .section-meta, .locked-section, .action-bar { align-items: stretch; flex-direction: column; }
  .basic-form, .item-grid { grid-template-columns: 1fr; }
  .basic-form .el-form-item:nth-child(2), .description-field { grid-column: auto; }
  .locked-section .el-button { width: 100%; margin-left: 0; }
  .action-bar { bottom: 8px; }
  .action-buttons { display: grid; grid-template-columns: 1fr 1fr; }
}
</style>
