<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

type Report = { id: number; reportNo: string; applicantId?: number; title: string; totalAmount?: number; currency?: string; status: string; approvalRound?: number; createdAt?: string }
type Statistics = { totalCount: number; draftCount: number; approvingCount: number; financeReviewCount: number; rejectedCount: number; pendingPaymentCount: number; completedCount: number; withdrawnCount: number; totalAmount?: number }

const keyword = ref(''); const status = ref(''); const startTime = ref(''); const endTime = ref('')
const router = useRouter()
const auth = useAuthStore()
const roles = computed(() => auth.roles)
const page = ref(1); const size = ref(10); const total = ref(0); const reports = ref<Report[]>([])
const statistics = ref<Statistics>({ totalCount: 0, draftCount: 0, approvingCount: 0, financeReviewCount: 0, rejectedCount: 0, pendingPaymentCount: 0, completedCount: 0, withdrawnCount: 0, totalAmount: 0 })
const loading = ref(false); const errorMessage = ref(''); const detail = ref<any>(null); const detailVisible = ref(false)
const submitIdempotencyKeys = new Map<number, string>()
const statusOptions = [{ label: '全部状态', value: '' }, { label: '草稿', value: 'DRAFT' }, { label: '审批中', value: 'APPROVING' }, { label: '财务审核', value: 'FINANCE_REVIEW' }, { label: '待付款', value: 'PENDING_PAYMENT' }, { label: '已完成', value: 'COMPLETED' }, { label: '已驳回', value: 'REJECTED' }, { label: '已撤回', value: 'WITHDRAWN' }]
// 页面根据登录用户和报销状态控制按钮展示，后端仍负责最终权限判断。
function statusLabel(value: string) { return statusOptions.find((item) => item.value === value)?.label || value }
function statusType(value: string) { return value === 'COMPLETED' ? 'success' : value === 'REJECTED' || value === 'WITHDRAWN' ? 'danger' : value === 'PENDING_PAYMENT' ? 'warning' : 'info' }
function params() { return { page: page.value, size: size.value, keyword: keyword.value.trim() || undefined, status: status.value || undefined, startTime: startTime.value || undefined, endTime: endTime.value || undefined } }
async function loadReports() { loading.value = true; errorMessage.value = ''; try { const { data } = await http.get('/reports/page', { params: params() }); reports.value = data.records || []; total.value = data.total || 0 } catch (e: any) { errorMessage.value = e.response?.data?.message || '报销单列表加载失败' } finally { loading.value = false } }
async function loadStatistics() { try { const { data } = await http.get('/reports/statistics', { params: { startTime: startTime.value || undefined, endTime: endTime.value || undefined } }); statistics.value = data } catch (e: any) { errorMessage.value = e.response?.data?.message || '统计数据加载失败' } }
function search() { page.value = 1; loadReports(); loadStatistics() }
async function openDetail(report: Report) { try { const { data } = await http.get(`/reports/${report.id}`); detail.value = data; detailVisible.value = true } catch (e: any) { errorMessage.value = e.response?.data?.message || '详情加载失败' } }
function isOwner(report: Report) { return Number(report.applicantId) === Number(auth.user?.id) }
function canEdit(report: Report) { return isOwner(report) && ['DRAFT', 'REJECTED'].includes(report.status) }
// 同一次提交重试复用相同幂等 key，成功后才清除，避免网络重试生成重复审批任务。
async function submitReport(report: Report) { const idempotencyKey = submitIdempotencyKeys.get(report.id) || crypto.randomUUID(); submitIdempotencyKeys.set(report.id, idempotencyKey); try { await http.post(`/reports/${report.id}/submit`, null, { headers: { 'Idempotency-Key': idempotencyKey } }); submitIdempotencyKeys.delete(report.id); await search() } catch (e: any) { errorMessage.value = e.response?.data?.message || '提交失败' } }
async function withdrawReport(report: Report) { try { await http.post(`/reports/${report.id}/withdraw`); await search() } catch (e: any) { errorMessage.value = e.response?.data?.message || '撤回失败' } }
async function payReport(report: Report) { try { await http.post(`/approvals/${report.id}/pay`); await search() } catch (e: any) { errorMessage.value = e.response?.data?.message || '付款失败' } }
// CSV 以 Blob 接收，通过临时对象 URL 触发浏览器下载，使用后立即释放 URL。
async function exportReports() { try { const { data } = await http.get('/reports/export', { params: { keyword: keyword.value.trim() || undefined, status: status.value || undefined, startTime: startTime.value || undefined, endTime: endTime.value || undefined }, responseType: 'blob' }); const url = URL.createObjectURL(data); const link = document.createElement('a'); link.href = url; link.download = 'expense-reports.csv'; link.click(); URL.revokeObjectURL(url) } catch (e: any) { errorMessage.value = '导出失败' } }
onMounted(() => { loadReports(); loadStatistics() })
</script>

<template>
  <main class="app-page">
    <header class="topbar"><div><p class="eyebrow">EXPENSE REPORTS</p><h1>报销单列表</h1></div><div><el-button @click="router.push('/reports/new')">新建报销单</el-button><el-button text @click="search">刷新</el-button></div></header>
    <section class="stats-grid">
      <el-card><span>总数量</span><strong>{{ statistics.totalCount }}</strong></el-card><el-card><span>审批中</span><strong>{{ statistics.approvingCount }}</strong></el-card><el-card><span>财务审核</span><strong>{{ statistics.financeReviewCount }}</strong></el-card><el-card><span>待付款</span><strong>{{ statistics.pendingPaymentCount }}</strong></el-card><el-card><span>已完成</span><strong>{{ statistics.completedCount }}</strong></el-card><el-card><span>总金额</span><strong>{{ statistics.totalAmount ?? 0 }}</strong></el-card>
    </section>
    <section class="content-panel report-panel">
      <div class="toolbar"><el-input v-model="keyword" clearable placeholder="编号或标题" @keyup.enter="search" /><el-select v-model="status" placeholder="状态" @change="search"><el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select><el-date-picker v-model="startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="开始时间" /><el-date-picker v-model="endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="结束时间" /><el-button type="primary" @click="search">查询</el-button><el-button @click="exportReports">导出 CSV</el-button></div>
      <el-alert v-if="errorMessage" :title="errorMessage" type="warning" show-icon :closable="false" />
      <el-table v-loading="loading" :data="reports" stripe><el-table-column prop="reportNo" label="报销单编号" min-width="180" /><el-table-column prop="title" label="标题" min-width="180" /><el-table-column label="金额" width="130"><template #default="scope">{{ scope.row.totalAmount ?? 0 }} {{ scope.row.currency || 'CNY' }}</template></el-table-column><el-table-column label="状态" width="120"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ statusLabel(scope.row.status) }}</el-tag></template></el-table-column><el-table-column prop="createdAt" label="创建时间" min-width="170" /><el-table-column label="操作" width="250" fixed="right"><template #default="scope"><el-button link type="primary" @click="openDetail(scope.row)">详情</el-button><el-button v-if="canEdit(scope.row)" link @click="router.push(`/reports/${scope.row.id}/edit`)">编辑</el-button><el-button v-if="canEdit(scope.row)" link type="success" @click="submitReport(scope.row)">提交</el-button><el-button v-if="isOwner(scope.row) && scope.row.status === 'APPROVING'" link @click="withdrawReport(scope.row)">撤回</el-button><el-button v-if="roles.includes('FINANCE') && scope.row.status === 'PENDING_PAYMENT'" link type="warning" @click="payReport(scope.row)">付款</el-button></template></el-table-column></el-table>
      <el-empty v-if="!loading && reports.length === 0" description="暂无报销单" /><el-pagination v-model:current-page="page" v-model:page-size="size" layout="total, sizes, prev, pager, next" :page-sizes="[10, 20, 50]" :total="total" @current-change="loadReports" @size-change="search" />
    </section>
    <el-drawer v-model="detailVisible" title="报销单详情" size="520px"><template v-if="detail"><el-descriptions :column="1" border><el-descriptions-item label="编号">{{ detail.reportNo }}</el-descriptions-item><el-descriptions-item label="标题">{{ detail.title }}</el-descriptions-item><el-descriptions-item label="金额">{{ detail.totalAmount }} {{ detail.currency }}</el-descriptions-item><el-descriptions-item label="状态">{{ statusLabel(detail.status) }}</el-descriptions-item><el-descriptions-item label="说明">{{ detail.description || '-' }}</el-descriptions-item></el-descriptions><h3>费用明细</h3><el-table :data="detail.items || []" size="small"><el-table-column prop="expenseType" label="类型" /><el-table-column prop="description" label="说明" /><el-table-column prop="amount" label="金额" /></el-table></template></el-drawer>
  </main>
</template>

<style scoped>
.stats-grid { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; max-width: 1280px; margin: 0 auto 20px; padding: 0 24px; }.stats-grid .el-card { text-align: center; }.stats-grid span { display: block; color: #64748b; }.stats-grid strong { display: block; margin-top: 8px; font-size: 24px; }.report-panel { max-width: 1280px; margin: 0 auto; padding: 22px; }.toolbar { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }.toolbar .el-input { max-width: 240px; }.toolbar .el-select { width: 140px; } h3 { margin: 24px 0 12px; } @media (max-width: 900px) { .stats-grid { grid-template-columns: repeat(3, 1fr); padding: 0 12px; } } @media (max-width: 600px) { .stats-grid { grid-template-columns: repeat(2, 1fr); } .report-panel { padding: 12px; } }
</style>
