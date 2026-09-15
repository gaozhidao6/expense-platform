<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { http } from '@/api/http'

const backendStatus = ref('检查中')
const router = useRouter()

// 欢迎页只做轻量健康检查，不在这里验证数据库和外部服务的全部状态。
onMounted(async () => {
  try {
    const { data } = await http.get('/health')
    backendStatus.value = data.status === 'UP' ? '连接正常' : '状态异常'
  } catch {
    backendStatus.value = '后端未启动'
  }
})
</script>

<template>
  <main class="setup-page">
    <section class="setup-panel">
      <div class="setup-logo">¥</div>
      <p class="eyebrow">EXPENSE MANAGEMENT</p>
      <h1>智能费用报销平台</h1>
      <p class="muted">覆盖填报、审批、财务复核和付款的完整业务闭环。</p>
      <div class="status-line">
        <span class="status-dot" :class="{ online: backendStatus === '连接正常' }"></span>
        后端服务：{{ backendStatus }}
      </div>
      <el-button type="primary" size="large" :disabled="backendStatus !== '连接正常'" @click="router.push('/login')">
        进入系统
      </el-button>
    </section>
  </main>
</template>

<style scoped>
.setup-page { background: radial-gradient(circle at 25% 15%, #ccfbf1 0, transparent 30%), linear-gradient(145deg, #f8fafc, #e2e8f0); }
.setup-panel { padding: 52px; border: 0; border-radius: 24px; text-align: center; box-shadow: 0 24px 70px rgba(15, 23, 42, .12); }
.setup-logo { display: grid; place-items: center; width: 62px; height: 62px; margin: 0 auto 24px; border-radius: 18px; color: white; font-size: 30px; font-weight: 800; background: linear-gradient(135deg, #0f766e, #0f3d56); }
.setup-panel h1 { font-size: 30px; }
.status-line { display: flex; align-items: center; justify-content: center; gap: 8px; margin: 28px 0; padding: 12px; border-radius: 10px; color: #475569; background: #f8fafc; }
.status-dot { width: 9px; height: 9px; border-radius: 50%; background: #94a3b8; }.status-dot.online { background: #10b981; box-shadow: 0 0 0 4px #d1fae5; }
.el-button { width: 100%; }
</style>
