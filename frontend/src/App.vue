<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getMyUnreadCount } from '@/api/notifications'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const user = computed(() => auth.user)
const roles = computed(() => auth.roles)
const showNav = computed(() => auth.isLoggedIn && route.name !== 'login' && route.name !== 'setup')
const canApprove = computed(() => roles.value.some((r) => ['MANAGER', 'FINANCE', 'ADMIN'].includes(r)))
const isAdmin = computed(() => roles.value.includes('ADMIN'))
const canAudit = computed(() => roles.value.some((r) => ['ADMIN', 'AUDITOR'].includes(r)))
const unreadCount = ref(0)

function logout() {
  auth.clearSession()
  unreadCount.value = 0
  router.push('/login')
}

async function refreshUnreadCount() {
  if (!showNav.value) {
    unreadCount.value = 0
    return
  }
  try {
    unreadCount.value = await getMyUnreadCount()
  } catch (error) {
    // 角标加载失败不应阻塞整个页面。
    console.error('未读通知数量加载失败', error)
  }
}

onMounted(() => {
  window.addEventListener('notifications-updated', refreshUnreadCount)
  void refreshUnreadCount()
  if (auth.isLoggedIn && !auth.user) void auth.loadCurrentUser()
})
onUnmounted(() => window.removeEventListener('notifications-updated', refreshUnreadCount))
watch(() => route.fullPath, () => void refreshUnreadCount())
</script>

<template>
  <nav v-if="showNav" class="global-nav">
    <strong><span class="nav-logo">¥</span>费用报销平台</strong>
    <div class="nav-links">
    <RouterLink to="/reports">报销中心</RouterLink>
    <RouterLink to="/reports/new">新建报销</RouterLink>
    <RouterLink v-if="canApprove" to="/approvals">审批工作台</RouterLink>
    <RouterLink to="/notifications" class="notification-link">
      通知
      <span v-if="unreadCount > 0" class="notification-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
    </RouterLink>
    <RouterLink v-if="isAdmin" to="/users">用户管理</RouterLink>
    <RouterLink v-if="canAudit" to="/operation-logs">操作日志</RouterLink>
    </div>
    <span class="nav-user">{{ user?.username }} · {{ roles.join(', ') }}</span>
    <button class="logout-button" type="button" @click="logout">退出</button>
  </nav>
  <RouterView />
</template>

<style scoped>
.global-nav { position: sticky; top: 0; z-index: 20; min-height: 66px; display: flex; align-items: center; gap: 30px; padding: 0 32px; color: #0f172a; background: rgba(255,255,255,.94); border-bottom: 1px solid #e2e8f0; backdrop-filter: blur(14px); }
.global-nav strong { display: flex; align-items: center; gap: 10px; white-space: nowrap; }.nav-logo { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 9px; color: white; background: #0f766e; }
.nav-links { display: flex; align-items: center; gap: 6px; }.global-nav a { padding: 9px 13px; border-radius: 8px; color: #475569; text-decoration: none; }
.global-nav a:hover { color: #0f766e; background: #f0fdfa; }.global-nav a.router-link-active { color: #0f766e; font-weight: 700; background: #f0fdfa; }
.nav-user { margin-left: auto; color: #64748b; font-size: 13px; }
.logout-button { padding: 8px 14px; border: 1px solid #e2e8f0; border-radius: 8px; color: #475569; background: #fff; cursor: pointer; }
.notification-link { position: relative; }.notification-badge { position: absolute; top: -7px; right: -8px; min-width: 18px; height: 18px; padding: 0 5px; border-radius: 9px; color: #fff; background: #ef4444; font-size: 11px; line-height: 18px; text-align: center; }
@media (max-width: 900px) { .global-nav { flex-wrap: wrap; gap: 12px; padding: 12px 18px; }.nav-links { order: 3; width: 100%; overflow-x: auto; }.nav-user { display: none; } }
</style>
