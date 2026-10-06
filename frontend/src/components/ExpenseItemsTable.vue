<script setup lang="ts">
import { expenseTypeLabel } from '@/api/policies'
export type ReviewExpenseItem = { id?: number; expenseType: string; description?: string; amount: number; limitAmount?: number | null; overLimit: boolean; overLimitReason?: string }
defineProps<{ items: ReviewExpenseItem[] }>()
</script>
<template>
  <div>
    <el-alert v-if="items.some(item => item.overLimit)" title="本单包含超限费用，请核对超限原因后再审批。限额按本轮提交时的规则展示。" type="warning" :closable="false" show-icon />
    <el-table :data="items" size="small" row-key="id" default-expand-all>
      <el-table-column type="expand" width="32"><template #default="{ row }"><div class="reason-detail"><p>费用说明：{{ row.description || '—' }}</p><p v-if="row.overLimit || row.overLimitReason" class="over-limit-reason"><strong>超限原因：</strong>{{ row.overLimitReason || '未填写' }}</p></div></template></el-table-column>
      <el-table-column label="类型" min-width="100"><template #default="{ row }">{{ expenseTypeLabel(row.expenseType) }}</template></el-table-column>
      <el-table-column prop="amount" label="金额" width="90" />
      <el-table-column label="单笔限额" width="100"><template #default="{ row }">{{ row.limitAmount ?? '—' }}</template></el-table-column>
      <el-table-column label="限额检查" width="90"><template #default="{ row }"><el-tag :type="row.overLimit ? 'warning' : 'info'">{{ row.overLimit ? '超限' : row.limitAmount == null ? '无限额记录' : '未超限' }}</el-tag></template></el-table-column>
    </el-table>
  </div>
</template>
<style scoped>
.reason-detail { padding: 0 14px; white-space: normal; overflow-wrap: anywhere; }
.reason-detail p { margin: 8px 0; }
.over-limit-reason { color: #92400e; padding: 10px 12px; background: #fffbeb; border-radius: 6px; }
</style>
