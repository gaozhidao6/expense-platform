<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { http } from '@/api/http'
import { errorMessage } from '@/api/errors'
import { useAuthStore } from '@/stores/auth'

type ApprovalTask = {
  id: number
  reportId: number
  reportNo?: string
  reportTitle?: string
  applicantId?: number
  totalAmount?: number
  approvalRound?: number
  nodeCode: string
  nodeName?: string
  status: string
  createdAt?: string
}

type ApprovalRecord = {
  id: number
  action: string
  fromStatus: string
  toStatus: string
  comment?: string
  operatorId?: number
  operatedAt?: string
}

const tasks = ref<ApprovalTask[]>([])
const records = ref<ApprovalRecord[]>([])
const selected = ref<ApprovalTask | null>(null)
const comment = ref('')
const loading = ref(false)
const detailLoading = ref(false)
const message = ref('')
const messageType = ref<'success' | 'warning' | 'error'>('warning')
const operating = ref(false)
const auth = useAuthStore()

const canOperateSelected = computed(() => {
  if (!selected.value) return false
  const roles = auth.roles
  if (roles.includes('ADMIN')) return true
  if (selected.value.nodeCode === 'MANAGER_APPROVAL') return roles.includes('MANAGER')
  if (['FINANCE_REVIEW', 'PAYMENT'].includes(selected.value.nodeCode)) return roles.includes('FINANCE')
  return false
})

// 待办列表由后端按当前审批人过滤，前端不传 approverId。
async function loadTasks() {
  loading.value = true
  message.value = ''
  try {
    const { data } = await http.get<ApprovalTask[]>('/approvals/my-pending')
    tasks.value = data || []
  } catch (error: any) {
    messageType.value = 'error'
    message.value = errorMessage(error, '待审批任务加载失败')
  } finally {
    loading.value = false
  }
}

async function selectTask(task: ApprovalTask) {
  selected.value = task
  message.value = ''
  records.value = []
  detailLoading.value = true
  try {
    const { data } = await http.get<ApprovalRecord[]>(`/approvals/${task.reportId}/records`)
    records.value = data || []
  } catch (error: any) {
    messageType.value = 'error'
    message.value = errorMessage(error, '审批历史加载失败')
    records.value = []
  } finally {
    detailLoading.value = false
  }
}

// 根据当前审批节点选择后端状态机接口，未知节点只允许进入付款路径。
function endpoint(action: string) {
  if (!selected.value) return ''
  const id = selected.value.reportId
  if (selected.value.nodeCode === 'MANAGER_APPROVAL') return `/approvals/${id}/manager/${action}`
  if (selected.value.nodeCode === 'FINANCE_REVIEW') return `/approvals/${id}/finance/${action}`
  return `/approvals/${id}/pay`
}

// 审批操作完成后重新加载待办，避免继续操作已经失效的任务快照。
async function operate(action: 'approve' | 'reject' | 'pay') {
  if (!selected.value) return
  if (action === 'reject' && !comment.value.trim()) {
    message.value = '驳回时必须填写驳回意见'
    messageType.value = 'warning'
    return
  }
  if (!canOperateSelected.value) {
    message.value = '当前账号角色与该审批节点不匹配，请刷新待办或重新登录'
    messageType.value = 'error'
    return
  }
  const actionLabel = action === 'approve' ? '通过' : action === 'reject' ? '驳回' : '确认付款'
  try {
    await ElMessageBox.confirm(
      `确定要${actionLabel}报销单 ${selected.value.reportNo || selected.value.reportId} 吗？`,
      '确认审批操作',
      { confirmButtonText: actionLabel, cancelButtonText: '取消', type: action === 'reject' ? 'warning' : 'info' },
    )
  } catch {
    return
  }
  message.value = ''
  operating.value = true
  try {
    await http.post(endpoint(action), comment.value.trim() ? { comment: comment.value.trim() } : undefined)
    message.value = '操作成功'
    messageType.value = 'success'
    comment.value = ''
    selected.value = null
    records.value = []
    await loadTasks()
  } catch (error: any) {
    messageType.value = 'error'
    message.value = errorMessage(error, '审批操作失败，请刷新后重试')
  } finally {
    operating.value = false
  }
}

onMounted(loadTasks)
</script>

<template>
  <main class="app-page">
    <header class="topbar">
      <div>
        <p class="eyebrow">APPROVAL WORKSPACE</p>
        <h1>审批工作台</h1>
      </div>
      <el-button text @click="loadTasks">刷新</el-button>
    </header>

    <section class="approval-layout">
      <div class="content-panel task-panel">
        <div class="panel-heading"><h2>我的待办</h2><el-tag>{{ tasks.length }}</el-tag></div>
        <el-alert v-if="message" :title="message" :type="messageType" :closable="false" show-icon />
        <el-table v-loading="loading" :data="tasks" highlight-current-row @row-click="selectTask">
          <el-table-column prop="reportNo" label="报销单" min-width="150" />
          <el-table-column prop="reportTitle" label="标题" min-width="160" />
          <el-table-column prop="totalAmount" label="金额" width="110" />
          <el-table-column prop="nodeName" label="节点" width="110" />
        </el-table>
        <el-empty v-if="!loading && tasks.length === 0" description="暂无待审批任务" />
      </div>

      <div class="content-panel detail-panel" v-loading="detailLoading">
        <el-empty v-if="!selected" description="选择左侧任务查看详情" />
        <template v-else>
          <div class="panel-heading"><h2>{{ selected.reportTitle || selected.reportNo }}</h2><el-tag type="warning">{{ selected.nodeName }}</el-tag></div>
          <p class="muted">报销单：{{ selected.reportNo }} · 金额：{{ selected.totalAmount ?? '-' }} · 第 {{ selected.approvalRound }} 轮</p>
          <h3>审批历史</h3>
          <el-timeline>
            <el-timeline-item v-for="record in records" :key="record.id" :timestamp="record.operatedAt">
              {{ record.fromStatus }} → {{ record.toStatus }}（{{ record.action }}）
              <div v-if="record.comment" class="timeline-comment">{{ record.comment }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-alert v-if="!canOperateSelected" title="当前账号不能处理该节点；你仍可查看审批历史" type="warning" :closable="false" show-icon />
          <el-input v-if="selected.nodeCode !== 'PAYMENT'" v-model="comment" type="textarea" :rows="3" placeholder="填写审批意见（驳回时必填）" maxlength="1000" show-word-limit />
          <div v-if="canOperateSelected" class="action-row">
            <template v-if="selected.nodeCode !== 'PAYMENT'">
              <el-button type="success" :loading="operating" @click="operate('approve')">通过</el-button>
              <el-button type="danger" :disabled="operating" @click="operate('reject')">驳回</el-button>
            </template>
            <el-button v-else type="primary" :loading="operating" @click="operate('pay')">确认付款</el-button>
          </div>
        </template>
      </div>
    </section>
  </main>
</template>

<style scoped>
.approval-layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(360px, 1fr); gap: 20px; max-width: 1280px; margin: 0 auto; padding: 0 24px 24px; }
.task-panel, .detail-panel { min-height: 520px; padding: 22px; }
.panel-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 16px; }
.panel-heading h2 { margin: 0; font-size: 20px; }
.detail-panel h3 { margin: 24px 0 12px; }
.timeline-comment { margin-top: 6px; color: #64748b; }
.action-row { display: flex; gap: 12px; margin-top: 16px; }
@media (max-width: 800px) { .approval-layout { grid-template-columns: 1fr; padding: 0 12px 16px; } }
</style>
