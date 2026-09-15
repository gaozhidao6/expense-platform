<template>
  <main class="notifications-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">MESSAGE CENTER</p>
        <h1>我的通知</h1>
        <p class="subtitle">查看审批进度和待办消息，已读状态只影响当前用户。</p>
      </div>
      <div class="header-actions">
        <el-button :loading="markingAll" @click="markAllAsRead">全部标为已读</el-button>
        <el-button :loading="loading" @click="loadNotifications">刷新</el-button>
      </div>
    </header>

    <el-card shadow="never" class="filter-card">
      <el-form inline>
        <el-form-item label="阅读状态">
          <el-select v-model="readFilter" style="width: 130px" @change="handleFilterChange">
            <el-option label="全部" value="" />
            <el-option label="未读" value="0" />
            <el-option label="已读" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知类型">
          <el-select v-model="typeFilter" style="width: 160px" @change="handleFilterChange">
            <el-option label="全部" value="" />
            <el-option label="待审批" value="APPROVAL_TODO" />
            <el-option label="审批通过" value="APPROVED" />
            <el-option label="审批驳回" value="REJECTED" />
            <el-option label="已打款" value="PAID" />
          </el-select>
        </el-form-item>
      </el-form>
    </el-card>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <el-card v-loading="loading" shadow="never" class="list-card">
      <el-empty v-if="!loading && notifications.length === 0" description="暂时没有通知" />

      <article v-for="item in notifications" :key="item.id" class="notification-item" :class="{ unread: item.isRead === 0 }">
        <span v-if="item.isRead === 0" class="unread-dot" aria-label="未读" />
        <div class="notification-body">
          <div class="notification-title-row">
            <h2>{{ item.title }}</h2>
            <el-tag :type="typeTag(item.type)" effect="plain">{{ typeLabel(item.type) }}</el-tag>
          </div>
          <p>{{ item.content }}</p>
          <time>{{ formatDateTime(item.createdAt) }}</time>
        </div>
        <div class="notification-actions">
          <el-button v-if="item.businessType === 'EXPENSE_REPORT' && item.businessId" link type="primary" @click="openBusiness(item)">
            查看报销单
          </el-button>
          <el-button v-if="item.isRead === 0" link :loading="markingId === item.id" @click="markAsRead(item)">
            标为已读
          </el-button>
          <span v-else class="read-label">已读</span>
        </div>
      </article>

      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        v-model:page-size="size"
        class="pagination"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="loadNotifications"
      />
    </el-card>
  </main>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  getMyNotifications,
  markAllNotificationsAsRead,
  markNotificationAsRead,
  type NotificationItem,
} from '@/api/notifications'

const router = useRouter()
const notifications = ref<NotificationItem[]>([])
const loading = ref(false)
const markingId = ref<number | null>(null)
const markingAll = ref(false)
const errorMessage = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const typeFilter = ref('')
const readFilter = ref('')

async function loadNotifications() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await getMyNotifications({
      page: page.value,
      size: size.value,
      type: typeFilter.value || undefined,
      // 下拉框保存字符串，发送请求时再转换成后端需要的 0/1。
      isRead: readFilter.value === '' ? undefined : Number(readFilter.value),
    })
    notifications.value = result.records
    total.value = result.total
  } catch (error) {
    console.error(error)
    errorMessage.value = '通知加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  page.value = 1
  void loadNotifications()
}

async function markAsRead(item: NotificationItem) {
  markingId.value = item.id
  try {
    await markNotificationAsRead(item.id)
    // 后端成功后再改本地状态，避免接口失败但页面误显示为已读。
    item.isRead = 1
    item.readAt = new Date().toISOString()
    window.dispatchEvent(new Event('notifications-updated'))
    ElMessage.success('已标记为已读')
  } catch (error) {
    console.error(error)
    ElMessage.error('操作失败，请稍后重试')
    throw error
  } finally {
    markingId.value = null
  }
}

/**
 * 前端只负责调用接口和刷新页面；哪些数据能够更新由后端根据登录用户决定。
 */
async function markAllAsRead() {
  markingAll.value = true
  try {
    await markAllNotificationsAsRead()
    await loadNotifications()
    window.dispatchEvent(new Event('notifications-updated'))
    ElMessage.success('全部通知已标记为已读')
  } catch (error) {
    console.error(error)
    ElMessage.error('全部标为已读失败，请稍后重试')
  } finally {
    markingAll.value = false
  }
}

async function openBusiness(item: NotificationItem) {
  if (item.isRead === 0) {
    try {
      await markAsRead(item)
    } catch {
      return
    }
  }
  if (item.businessId) await router.push(`/reports/${item.businessId}/edit`)
}

function typeLabel(type: string) {
  const labels: Record<string, string> = {
    APPROVAL_TODO: '待审批', APPROVED: '审批通过', REJECTED: '审批驳回', PAID: '已打款',
  }
  return labels[type] ?? type
}

function typeTag(type: string): 'success' | 'warning' | 'danger' | 'info' {
  if (type === 'APPROVED' || type === 'PAID') return 'success'
  if (type === 'REJECTED') return 'danger'
  if (type === 'APPROVAL_TODO') return 'warning'
  return 'info'
}

function formatDateTime(value: string) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

onMounted(loadNotifications)
</script>

<style scoped>
.notifications-page { max-width: 1100px; margin: 0 auto; padding: 32px; display: grid; gap: 16px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; }
.header-actions { display: flex; gap: 10px; }
.page-header h1 { margin: 2px 0 8px; }.eyebrow { margin: 0; color: #0f766e; font-size: 12px; letter-spacing: .12em; }
.subtitle { margin: 0; color: #64748b; }.filter-card :deep(.el-card__body) { padding-bottom: 2px; }
.list-card :deep(.el-card__body) { padding: 0; }.notification-item { display: flex; align-items: center; gap: 20px; padding: 20px 24px; border-bottom: 1px solid #ebeef5; }
.notification-item.unread { background: #f0fdfa; }.unread-dot { width: 8px; height: 8px; flex: 0 0 auto; border-radius: 50%; background: #0f766e; }
.notification-body { min-width: 0; flex: 1; }.notification-title-row { display: flex; align-items: center; gap: 10px; }.notification-title-row h2 { margin: 0; font-size: 16px; }
.notification-body p { margin: 8px 0; color: #475569; line-height: 1.6; }.notification-body time, .read-label { color: #909399; font-size: 12px; }
.notification-actions { display: flex; align-items: center; gap: 8px; }.pagination { justify-content: flex-end; padding: 18px 24px; }
@media (max-width: 720px) { .notifications-page { padding: 20px 14px; }.notification-item { align-items: flex-start; flex-wrap: wrap; }.notification-actions { width: 100%; justify-content: flex-end; } }
</style>
