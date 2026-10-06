import { http } from '@/api/http'

export const expenseTypes = [
  { value: 'TRAVEL', label: '差旅住宿' }, { value: 'MEAL', label: '餐饮招待' },
  { value: 'TRANSPORT', label: '交通出行' }, { value: 'OFFICE', label: '办公用品' },
  { value: 'OTHER', label: '其他费用' },
]
export type ExpensePolicy = { expenseType: string; limitAmount: number; enabled: boolean; updatedAt?: string }
export async function getExpensePolicies() {
  return (await http.get<ExpensePolicy[]>('/expense-policies')).data
}
export async function updateExpensePolicy(policy: ExpensePolicy) {
  return (await http.put<ExpensePolicy>(`/expense-policies/${policy.expenseType}`, {
    limitAmount: policy.limitAmount, enabled: policy.enabled,
  })).data
}
export function expenseTypeLabel(value: string) {
  return expenseTypes.find(t => t.value === value)?.label || value
}
