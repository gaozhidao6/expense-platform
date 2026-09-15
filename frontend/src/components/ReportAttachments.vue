<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  deleteAttachment,
  downloadAttachment,
  listAttachments,
  uploadAttachment,
  type Attachment,
} from '@/api/attachments'
import {
  confirmInvoiceSuggestion,
  recognizeInvoice,
  rejectInvoiceSuggestion,
  type ConfirmInvoiceSuggestionRequest,
  type InvoiceSuggestion,
} from '@/api/invoices'

interface ExpenseItemOption {
  id?: number
  expenseDate?: string
  expenseType?: string
  merchant?: string
  amount?: number | string
}

const props = defineProps<{
  reportId: number
  editable: boolean
  recognitionEnabled: boolean
  expenseItems: ExpenseItemOption[]
}>()

const emit = defineEmits<{
  confirmed: []
}>()

const attachments = ref<Attachment[]>([])
const selectedFile = ref<File | null>(null)
const loading = ref(false)
const uploading = ref(false)
const message = ref('')
const messageType = ref<'success' | 'info' | 'warning' | 'error'>('info')
const recognizingAttachmentId = ref<number | null>(null)
const processingSuggestion = ref(false)
const suggestion = ref<InvoiceSuggestion | null>(null)
const suggestionVisible = ref(false)
const confirmation = ref<ConfirmInvoiceSuggestionRequest>({
  expenseItemId: 0,
  invoiceNumber: '',
  invoiceDate: '',
  amount: 0,
  merchantName: '',
})

function showMessage(
  text: string,
  type: 'success' | 'info' | 'warning' | 'error' = 'info',
) {
  message.value = text
  messageType.value = type
}

/** 只有已经保存到数据库的费用明细才能接收识别结果。 */
const savedExpenseItems = computed(() =>
  props.expenseItems.filter((item) => typeof item.id === 'number'),
)

function expenseItemLabel(item: ExpenseItemOption) {
  const merchant = item.merchant?.trim() || '未填写商户'
  return `${item.expenseDate || '无日期'} · ${item.expenseType || 'OTHER'} · ${merchant} · ${Number(item.amount || 0).toFixed(2)}`
}

function fillConfirmation(value: InvoiceSuggestion) {
  suggestion.value = value
  confirmation.value = {
    expenseItemId: value.expenseItemId ?? savedExpenseItems.value[0]?.id ?? 0,
    invoiceNumber: value.invoiceNumber,
    invoiceDate: value.invoiceDate,
    amount: Number(value.amount),
    merchantName: value.merchantName,
  }
  suggestionVisible.value = true
}

async function recognize(item: Attachment) {
  if (!savedExpenseItems.value.length) {
    showMessage('请先保存至少一条费用明细，再识别发票', 'warning')
    return
  }
  recognizingAttachmentId.value = item.id
  showMessage(`正在识别“${item.originalFilename}”，请稍候…`)
  try {
    fillConfirmation(await recognizeInvoice(props.reportId, item.id))
    showMessage('识别完成，请核对原始发票后确认', 'success')
  } catch (error: any) {
    showMessage(
      error.userMessage || error.response?.data?.message || '发票识别失败，请稍后重试',
      'error',
    )
  } finally {
    recognizingAttachmentId.value = null
  }
}

function validateConfirmation() {
  if (!confirmation.value.expenseItemId) return '请选择费用明细'
  if (!confirmation.value.invoiceNumber.trim()) return '发票号码不能为空'
  if (!confirmation.value.invoiceDate) return '发票日期不能为空'
  if (confirmation.value.invoiceDate > new Date().toISOString().slice(0, 10)) return '发票日期不能晚于今天'
  if (Number(confirmation.value.amount) <= 0) return '发票金额必须大于 0'
  if (!confirmation.value.merchantName.trim()) return '商户名称不能为空'
  return ''
}

async function confirmSuggestion() {
  if (!suggestion.value) return
  const validation = validateConfirmation()
  if (validation) {
    showMessage(validation, 'warning')
    return
  }
  processingSuggestion.value = true
  message.value = ''
  try {
    const result = await confirmInvoiceSuggestion(
      props.reportId,
      suggestion.value.id,
      {
        ...confirmation.value,
        invoiceNumber: confirmation.value.invoiceNumber.trim(),
        merchantName: confirmation.value.merchantName.trim(),
      },
    )
    fillConfirmation(result)
    showMessage('识别建议已确认，费用明细和总金额已更新', 'success')
    emit('confirmed')
  } catch (error: any) {
    showMessage(error.userMessage || error.response?.data?.message || '确认识别建议失败', 'error')
  } finally {
    processingSuggestion.value = false
  }
}

async function rejectSuggestion() {
  if (!suggestion.value) return
  processingSuggestion.value = true
  message.value = ''
  try {
    fillConfirmation(await rejectInvoiceSuggestion(
      props.reportId,
      suggestion.value.id,
    ))
    showMessage('识别建议已拒绝，费用明细未修改', 'success')
  } catch (error: any) {
    showMessage(error.userMessage || error.response?.data?.message || '拒绝识别建议失败', 'error')
  } finally {
    processingSuggestion.value = false
  }
}

/** 将字节数转换成更容易阅读的文字。 */
function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function handleFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  selectedFile.value = input.files?.[0] ?? null
}

async function loadAttachments() {
  loading.value = true
  message.value = ''
  try {
    attachments.value = await listAttachments(props.reportId)
  } catch (error: any) {
    showMessage(error.response?.data?.message || '附件列表加载失败', 'error')
  } finally {
    loading.value = false
  }
}

async function upload() {
  if (!selectedFile.value) {
    showMessage('请先选择附件', 'warning')
    return
  }

  uploading.value = true
  message.value = ''
  try {
    await uploadAttachment(props.reportId, selectedFile.value)
    selectedFile.value = null
    await loadAttachments()
    showMessage('附件上传成功', 'success')
  } catch (error: any) {
    showMessage(error.response?.data?.message || '附件上传失败', 'error')
  } finally {
    uploading.value = false
  }
}

async function download(item: Attachment) {
  message.value = ''
  try {
    const response = await downloadAttachment(props.reportId, item.id)
    const objectUrl = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = item.originalFilename
    link.click()
    URL.revokeObjectURL(objectUrl)
  } catch (error: any) {
    showMessage(error.response?.data?.message || '附件下载失败', 'error')
  }
}

async function remove(item: Attachment) {
  if (!window.confirm(`确定删除附件“${item.originalFilename}”吗？`)) return

  message.value = ''
  try {
    await deleteAttachment(props.reportId, item.id)
    await loadAttachments()
    showMessage('附件已删除', 'success')
  } catch (error: any) {
    showMessage(error.response?.data?.message || '附件删除失败', 'error')
  }
}

onMounted(loadAttachments)
watch(() => props.reportId, loadAttachments)
</script>

<template>
  <section class="attachment-section">
    <div class="attachment-heading">
      <div>
        <h2>报销附件</h2>
        <p>支持 JPG、PNG 和 PDF，单个文件不超过 10 MB。</p>
      </div>
      <el-button text :loading="loading" @click="loadAttachments">刷新</el-button>
    </div>

    <el-alert
      v-if="message"
      :title="message"
      :type="messageType"
      :closable="false"
      show-icon
    />

    <div v-if="editable" class="upload-row">
      <input
        type="file"
        accept=".jpg,.jpeg,.png,.pdf"
        @change="handleFileChange"
      />
      <el-button
        type="primary"
        :loading="uploading"
        :disabled="!selectedFile"
        @click="upload"
      >
        {{ uploading ? '正在上传…' : '上传附件' }}
      </el-button>
    </div>

    <el-table v-loading="loading" :data="attachments" border empty-text="暂无附件">
      <el-table-column prop="originalFilename" label="文件名" min-width="220" />
      <el-table-column label="大小" width="110">
        <template #default="scope">
          {{ formatFileSize(scope.row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column prop="contentType" label="类型" min-width="150" />
      <el-table-column prop="createdAt" label="上传时间" min-width="180" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="download(scope.row)">下载</el-button>
          <el-button
            v-if="recognitionEnabled"
            link
            type="success"
            :loading="recognizingAttachmentId === scope.row.id"
            :disabled="recognizingAttachmentId !== null"
            @click="recognize(scope.row)"
          >
            {{ recognizingAttachmentId === scope.row.id ? '正在识别…' : '识别发票' }}
          </el-button>
          <el-button
            v-if="editable"
            link
            type="danger"
            :disabled="recognizingAttachmentId !== null"
            @click="remove(scope.row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-card v-if="suggestionVisible && suggestion" class="suggestion-card">
      <template #header>
        <div class="suggestion-heading">
          <div>
            <strong>发票识别建议 #{{ suggestion.id }}</strong>
            <p>AI 结果仅供参考，请核对原始发票后再确认。</p>
          </div>
          <el-tag
            :type="suggestion.status === 'CONFIRMED' ? 'success' : suggestion.status === 'REJECTED' ? 'info' : 'warning'"
          >
            {{ suggestion.status }}
          </el-tag>
        </div>
      </template>

      <el-form label-width="100px" class="suggestion-form">
        <el-form-item label="写入明细">
          <el-select
            v-model="confirmation.expenseItemId"
            placeholder="请选择费用明细"
            :disabled="suggestion.status !== 'PENDING_CONFIRMATION'"
          >
            <el-option
              v-for="item in savedExpenseItems"
              :key="item.id"
              :label="expenseItemLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="发票号码">
          <el-input
            v-model="confirmation.invoiceNumber"
            maxlength="80"
            :disabled="suggestion.status !== 'PENDING_CONFIRMATION'"
          />
        </el-form-item>
        <el-form-item label="发票日期">
          <el-date-picker
            v-model="confirmation.invoiceDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="suggestion.status !== 'PENDING_CONFIRMATION'"
          />
        </el-form-item>
        <el-form-item label="发票金额">
          <el-input-number
            v-model="confirmation.amount"
            :min="0.01"
            :precision="2"
            :disabled="suggestion.status !== 'PENDING_CONFIRMATION'"
          />
        </el-form-item>
        <el-form-item label="商户名称">
          <el-input
            v-model="confirmation.merchantName"
            maxlength="200"
            :disabled="suggestion.status !== 'PENDING_CONFIRMATION'"
          />
        </el-form-item>
        <el-form-item label="AI 置信度">
          <el-progress
            :percentage="Math.round(Number(suggestion.confidence) * 100)"
            :status="Number(suggestion.confidence) < 0.6 ? 'warning' : 'success'"
          />
        </el-form-item>
      </el-form>

      <div
        v-if="suggestion.status === 'PENDING_CONFIRMATION'"
        class="suggestion-actions"
      >
        <el-button
          type="primary"
          :loading="processingSuggestion"
          @click="confirmSuggestion"
        >
          确认写入
        </el-button>
        <el-button
          :disabled="processingSuggestion"
          @click="rejectSuggestion"
        >
          拒绝建议
        </el-button>
      </div>
    </el-card>
  </section>
</template>

<style scoped>
.attachment-section {
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid #e5e7eb;
}

.attachment-heading,
.upload-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.attachment-heading h2 {
  margin: 0;
  font-size: 20px;
}

.attachment-heading p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.upload-row {
  justify-content: flex-start;
  margin: 16px 0;
}

.attachment-section :deep(.el-alert) {
  margin: 14px 0;
}

.suggestion-card {
  margin-top: 18px;
}

.suggestion-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.suggestion-heading p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.suggestion-form {
  max-width: 680px;
}

.suggestion-form :deep(.el-select),
.suggestion-form :deep(.el-date-editor) {
  width: 100%;
}

.suggestion-form :deep(.el-progress) {
  width: 100%;
}

.suggestion-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 700px) {
  .attachment-heading,
  .upload-row {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
