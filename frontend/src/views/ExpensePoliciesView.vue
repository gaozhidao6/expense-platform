<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { expenseTypeLabel, getExpensePolicies, updateExpensePolicy, type ExpensePolicy } from '@/api/policies'
import { errorMessage } from '@/api/errors'

const policies = ref<ExpensePolicy[]>([])
const loading = ref(false)
const saving = ref<string | null>(null)
const message = ref('')
const messageType = ref<'success' | 'error'>('success')
async function load() {
  loading.value = true
  try { policies.value = await getExpensePolicies() }
  catch (error) { message.value = errorMessage(error, '费用规则加载失败'); messageType.value = 'error' }
  finally { loading.value = false }
}
async function save(policy: ExpensePolicy) {
  saving.value = policy.expenseType
  try {
    Object.assign(policy, await updateExpensePolicy(policy))
    message.value = `${expenseTypeLabel(policy.expenseType)}规则已保存`
    messageType.value = 'success'
  } catch (error) { message.value = errorMessage(error, '费用规则保存失败'); messageType.value = 'error' }
  finally { saving.value = null }
}
onMounted(load)
</script>

<template>
  <main class="app-page">
    <header class="topbar"><div><p class="eyebrow">EXPENSE POLICIES</p><h1>费用规则</h1></div><el-button @click="load">刷新</el-button></header>
    <section class="content-panel policy-panel" v-loading="loading">
      <el-alert title="按人民币费用明细设置单笔限额。超限需填写原因，由审批人核查；规则修改适用于之后的提交。初始金额仅供演示，请按实际政策调整。" type="info" :closable="false" show-icon />
      <el-alert v-if="message" :title="message" :type="messageType" :closable="false" show-icon />
      <el-table :data="policies">
        <el-table-column label="费用类型" min-width="150"><template #default="{ row }">{{ expenseTypeLabel(row.expenseType) }}</template></el-table-column>
        <el-table-column label="单笔限额（元）" min-width="230"><template #default="{ row }"><el-input-number v-model="row.limitAmount" :min="0.01" :max="9999999999999.99" :precision="2" :step="100" :disabled="saving !== null" :aria-label="`${expenseTypeLabel(row.expenseType)}单笔限额`" /></template></el-table-column>
        <el-table-column label="启用" width="100"><template #default="{ row }"><el-switch v-model="row.enabled" :disabled="saving !== null" :aria-label="`${expenseTypeLabel(row.expenseType)}启用限额`" /></template></el-table-column>
        <el-table-column label="最近修改" min-width="180" prop="updatedAt" />
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button type="primary" :loading="saving === row.expenseType" :disabled="saving !== null && saving !== row.expenseType" @click="save(row)">保存</el-button></template></el-table-column>
      </el-table>
    </section>
  </main>
</template>

<style scoped>
.policy-panel { max-width: 1100px; margin: 0 auto; padding: 24px; }
.el-alert { margin-bottom: 16px; }
</style>
