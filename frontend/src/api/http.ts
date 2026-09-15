import axios from 'axios'

// 所有业务请求统一使用 /api 前缀，并由开发代理或 Nginx 转发到后端。
export const http = axios.create({
  baseURL: '/api',
  timeout: 10_000,
})

// 请求发出前自动携带 JWT，页面组件不需要重复拼接 Authorization。
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('access_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器统一处理常见错误；401 会清理失效登录态并返回登录页。
http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const messages: Record<number, string> = {
      400: '请求参数有误',
      403: '没有操作权限',
      404: '请求的数据不存在',
      409: '数据已被修改，请刷新后重试',
      500: '服务器异常，请稍后重试',
    }
    if (status && messages[status]) {
      ;(error as any).userMessage = error.response?.data?.message || messages[status]
    }
    if (status === 401) {
      localStorage.removeItem('access_token')
      localStorage.removeItem('login_user')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  },
)
