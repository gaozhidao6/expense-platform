import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import './style.css'
import App from './App.vue'
import router from './router'
import { pinia } from './stores'
import { useAuthStore } from './stores/auth'

// 前端统一注册状态管理、路由和 Element Plus，再挂载到 index.html 的 #app。
const app = createApp(App)
  .use(pinia)
  .use(router)
  .use(ElementPlus)

// 只注册一次跨标签页监听，避免各页面重复监听 localStorage。
useAuthStore(pinia).enableCrossTabSync()
app.mount('#app')
