import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { http } from '@/api/http'

export type LoginUser = {
  id: number
  username: string
  realName: string
  roles: string[]
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('access_token') || '')
  const user = ref<LoginUser | null>(null)
  const loadingUser = ref(false)
  let loadPromise: Promise<LoginUser> | null = null

  const isLoggedIn = computed(() => Boolean(token.value))
  const roles = computed(() => user.value?.roles || [])

  // Token 是唯一持久化凭证；用户与角色始终通过 /auth/me 向后端确认。
  async function loadCurrentUser() {
    if (!token.value) return null
    // 多个页面同时初始化时复用同一个请求，避免重复调用 /auth/me。
    if (loadPromise) return loadPromise
    loadingUser.value = true
    loadPromise = (async () => {
      const { data } = await http.get<LoginUser>('/auth/me')
      user.value = data
      return data
    })()
    try {
      return await loadPromise
    } finally {
      loadingUser.value = false
      loadPromise = null
    }
  }

  async function establishSession(accessToken: string) {
    localStorage.setItem('access_token', accessToken)
    token.value = accessToken
    user.value = null
    await loadCurrentUser()
  }

  function clearSession() {
    localStorage.removeItem('access_token')
    // 清理旧版本遗留的数据，之后不再写入 login_user。
    localStorage.removeItem('login_user')
    token.value = ''
    user.value = null
  }

  // 在另一个标签页登录、切换账号或退出时，同步当前页面的身份。
  function enableCrossTabSync() {
    window.addEventListener('storage', (event) => {
      if (event.key !== 'access_token' || event.newValue === token.value) return
      token.value = event.newValue || ''
      user.value = null
      if (token.value) {
        void loadCurrentUser()
      } else if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    })
  }

  return { token, user, roles, isLoggedIn, loadingUser, loadCurrentUser, establishSession, clearSession, enableCrossTabSync }
})
