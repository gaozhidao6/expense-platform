import { http } from '@/api/http'

/** 后端返回的 AI 发票识别建议。 */
export interface InvoiceSuggestion {
  id: number
  reportId: number
  expenseItemId: number | null
  attachmentId: number
  invoiceNumber: string
  invoiceDate: string
  amount: number
  merchantName: string
  confidence: number
  status: 'PENDING_CONFIRMATION' | 'CONFIRMED' | 'REJECTED'
  createdAt: string | null
  updatedAt: string | null
}

/** 用户核对后提交的最终发票字段。 */
export interface ConfirmInvoiceSuggestionRequest {
  expenseItemId: number
  invoiceNumber: string
  invoiceDate: string
  amount: number
  merchantName: string
}

/** 读取附件并触发一次 AI 识别；成功后后端会保存待确认建议。 */
export async function recognizeInvoice(
  reportId: number,
  attachmentId: number,
) {
  const response = await http.post<InvoiceSuggestion>(
    `/reports/${reportId}/attachments/${attachmentId}/recognition`,
  )
  return response.data
}

/** 查询一条已保存的识别建议。 */
export async function getInvoiceSuggestion(
  reportId: number,
  suggestionId: number,
) {
  const response = await http.get<InvoiceSuggestion>(
    `/reports/${reportId}/invoice-suggestions/${suggestionId}`,
  )
  return response.data
}

/** 使用用户核对后的字段确认建议，并写入正式费用数据。 */
export async function confirmInvoiceSuggestion(
  reportId: number,
  suggestionId: number,
  request: ConfirmInvoiceSuggestionRequest,
) {
  const response = await http.put<InvoiceSuggestion>(
    `/reports/${reportId}/invoice-suggestions/${suggestionId}/confirm`,
    request,
  )
  return response.data
}

/** 拒绝建议；正式费用数据不会被修改。 */
export async function rejectInvoiceSuggestion(
  reportId: number,
  suggestionId: number,
) {
  const response = await http.post<InvoiceSuggestion>(
    `/reports/${reportId}/invoice-suggestions/${suggestionId}/reject`,
  )
  return response.data
}
