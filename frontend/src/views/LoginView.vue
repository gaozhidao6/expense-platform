<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const errorMessage = ref('')
const form = reactive({ username: 'admin', password: 'Admin123!' })

// 演示账号只用于本地验收，生产页面不应展示固定密码。
const demoAccounts = [
  { role: '员工', username: 'employee', password: 'Employee123!', description: '新建、编辑并提交报销单' },
  { role: '经理', username: 'manager', password: 'Manager123!', description: '处理部门经理审批待办' },
  { role: '财务', username: 'finance', password: 'Finance123!', description: '财务审核并完成付款' },
  { role: '管理员', username: 'admin', password: 'Admin123!', description: '查看用户和操作日志' },
]

function selectAccount(account: typeof demoAccounts[number]) {
  form.username = account.username
  form.password = account.password
  errorMessage.value = ''
}

// 登录成功后保存 JWT 和安全用户信息，后续请求由 Axios 拦截器自动带上 Token。
async function login() {
  errorMessage.value = ''
  if (!form.username.trim() || !form.password) {
    errorMessage.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  try {
    const { data } = await http.post('/auth/login', form)
    // 只保存 Token，再通过 /auth/me 获取与该 Token 对应的真实身份。
    await auth.establishSession(data.token)
    await router.push('/reports')
  } catch (error: any) {
    errorMessage.value = error.response?.status === 401
      ? '用户名或密码错误'
      : '登录失败，请确认后端已启动'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="login-shell">
      <div class="login-intro">
        <div class="brand-mark">¥</div>
        <p class="eyebrow">EXPENSE PLATFORM</p>
        <h1>让每一笔费用<br />清晰、有序、可追溯</h1>
        <p>从费用填报到审批付款，一站式完成企业报销流程。</p>
        <div class="feature-list">
          <span>✓ 多角色权限</span><span>✓ 审批轨迹</span><span>✓ 实时统计</span>
        </div>
      </div>
      <div class="auth-panel">
      <p class="eyebrow">WELCOME BACK</p>
      <h2>登录费用报销平台</h2>
      <p class="muted">选择演示身份，快速开始功能测试。</p>
      <el-form @submit.prevent="login">
        <el-form-item label="用户名">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
        <el-button class="submit-button" type="primary" native-type="submit" :loading="loading">登录</el-button>
      </el-form>
      <div class="demo-hint">
        <strong>演示账号</strong>
        <button v-for="account in demoAccounts" :key="account.username" type="button"
          class="account-card" :class="{ active: form.username === account.username }"
          @click="selectAccount(account)">
          <span class="account-role">{{ account.role }}</span>
          <span><b>{{ account.username }}</b><small>{{ account.description }}</small></span>
        </button>
      </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
.login-shell { width: min(980px, 100%); display: grid; grid-template-columns: 1.1fr .9fr; overflow: hidden; border-radius: 24px; background: #fff; box-shadow: 0 24px 80px rgba(15, 23, 42, .15); }
.login-intro { padding: 64px 54px; color: #fff; background: linear-gradient(145deg, #0f766e, #0f3d56 72%, #172554); }
.brand-mark { display: grid; place-items: center; width: 48px; height: 48px; margin-bottom: 50px; border: 1px solid rgba(255,255,255,.35); border-radius: 14px; font-size: 24px; font-weight: 800; background: rgba(255,255,255,.12); }
.login-intro .eyebrow { color: #99f6e4; }
.login-intro h1 { margin: 14px 0 20px; font-size: clamp(34px, 4vw, 48px); line-height: 1.2; }
.login-intro p { max-width: 400px; color: #ccfbf1; line-height: 1.8; }
.feature-list { display: flex; flex-wrap: wrap; gap: 18px; margin-top: 42px; color: #d5faf4; font-size: 14px; }
.auth-panel { width: auto; padding: 48px 42px; border: 0; border-radius: 0; }
.auth-panel h2 { margin: 0 0 8px; font-size: 26px; }
.demo-hint { gap: 8px; }
.account-card { display: grid; grid-template-columns: 56px 1fr; gap: 10px; width: 100%; padding: 9px 10px; border: 1px solid #e2e8f0; border-radius: 10px; color: #334155; text-align: left; background: #fff; cursor: pointer; transition: .2s ease; }
.account-card:hover, .account-card.active { border-color: #0f766e; background: #f0fdfa; transform: translateY(-1px); }
.account-role { align-self: center; color: #0f766e; font-weight: 700; }
.account-card b, .account-card small { display: block; }
.account-card small { margin-top: 2px; color: #94a3b8; }
@media (max-width: 760px) { .login-shell { grid-template-columns: 1fr; }.login-intro { padding: 34px 28px; }.brand-mark { margin-bottom: 24px; }.feature-list { margin-top: 24px; }.auth-panel { padding: 32px 24px; } }
</style>
