import { createRouter, createWebHistory } from 'vue-router'
import SetupView from '@/views/SetupView.vue'
import LoginView from '@/views/LoginView.vue'
import UsersView from '@/views/UsersView.vue'
import ApprovalsView from '@/views/ApprovalsView.vue'
import ReportsView from '@/views/ReportsView.vue'
import ReportEditorView from '@/views/ReportEditorView.vue'
import ExpensePoliciesView from '@/views/ExpensePoliciesView.vue'
import OperationLogsView from '@/views/OperationLogsView.vue'
import NotificationsView from '@/views/NotificationsView.vue'
import { pinia } from '@/stores'
import { useAuthStore } from '@/stores/auth'

// 路由 meta 只用于前端页面导航控制，真正的数据权限仍由后端校验。
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/expense-policies', name: 'expense-policies', component: ExpensePoliciesView, meta: { requiresAuth: true, roles: ['ADMIN'] } },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
    },
    {
      path: '/',
      name: 'setup',
      component: SetupView,
    },
    {
      path: '/users',
      name: 'users',
      component: UsersView,
      meta: { requiresAuth: true, roles: ['ADMIN'] },
    },
    {
      path: '/approvals',
      name: 'approvals',
      component: ApprovalsView,
      meta: { requiresAuth: true, roles: ['MANAGER', 'FINANCE', 'ADMIN'] },
    },
    {
      path: '/reports',
      name: 'reports',
      component: ReportsView,
      meta: { requiresAuth: true },
    },
    { path: '/reports/new', name: 'report-new', component: ReportEditorView, meta: { requiresAuth: true } },
    { path: '/reports/:id/edit', name: 'report-edit', component: ReportEditorView, meta: { requiresAuth: true } },
    { path: '/notifications', name: 'notifications', component: NotificationsView, meta: { requiresAuth: true } },
    { path: '/operation-logs', name: 'operation-logs', component: OperationLogsView, meta: { requiresAuth: true, roles: ['ADMIN', 'AUDITOR'] } },
  ],
})

// 全局守卫负责未登录跳转和菜单级角色限制，不能替代服务端授权。
router.beforeEach(async (to) => {
  const auth = useAuthStore(pinia)
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return { name: 'login' }
  }
  if (to.name === 'login' && auth.isLoggedIn) {
    return { name: 'reports' }
  }
  const requiredRoles = to.meta.roles as string[] | undefined
  if (auth.isLoggedIn && !auth.user) {
    try { await auth.loadCurrentUser() }
    catch {
      auth.clearSession()
      return { name: 'login' }
    }
  }
  if (auth.isLoggedIn && requiredRoles?.length) {
    if (!requiredRoles.some((role) => auth.roles.includes(role))) {
      return { name: 'reports' }
    }
  }
})

export default router
