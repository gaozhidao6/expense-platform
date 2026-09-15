import { http } from '@/api/http'

/** 后端返回的附件展示信息。 */
export interface Attachment {
  id: number
  reportId: number
  originalFilename: string
  fileSize: number
  contentType: string
  downloadUrl: string
  createdAt: string
}

/** 查询指定报销单的附件列表。 */
export async function listAttachments(reportId: number) {
  const response = await http.get<Attachment[]>(
    `/reports/${reportId}/attachments`,
  )
  return response.data
}

/** 使用 multipart/form-data 上传单个附件。 */
export async function uploadAttachment(
  reportId: number,
  file: File,
) {
  const formData = new FormData()
  formData.append('file', file)

  // 不手动设置 Content-Type，让浏览器自动补充 multipart boundary。
  const response = await http.post<Attachment>(
    `/reports/${reportId}/attachments`,
    formData,
  )
  return response.data
}

/** 下载附件 Blob；认证信息仍由 Axios 拦截器统一添加。 */
export async function downloadAttachment(
  reportId: number,
  attachmentId: number,
) {
  return http.get<Blob>(
    `/reports/${reportId}/attachments/${attachmentId}/download`,
    { responseType: 'blob' },
  )
}

/** 删除指定附件。 */
export async function deleteAttachment(
  reportId: number,
  attachmentId: number,
) {
  await http.delete(
    `/reports/${reportId}/attachments/${attachmentId}`,
  )
}
