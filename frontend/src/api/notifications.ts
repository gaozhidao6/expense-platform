import { http } from './http'

/** 后端返回的一条通知。 */
export interface NotificationItem {
  id: number
  type: string
  title: string
  content: string
  businessType: string | null
  businessId: number | null
  isRead: number
  readAt: string | null
  createdAt: string
}

/** MyBatis-Plus 分页结果。 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface NotificationQuery {
  page: number
  size: number
  type?: string
  isRead?: number
}

/** 查询当前登录用户的通知，用户身份由后端从登录态取得。 */
export async function getMyNotifications(query: NotificationQuery) {
  const response = await http.get<PageResult<NotificationItem>>('/notifications', { params: query })
  return response.data
}

/** 查询当前用户的未读通知数量。 */
export async function getMyUnreadCount() {
  const response = await http.get<{ count: number }>('/notifications/unread-count')
  return response.data.count
}

/** 将属于当前用户的一条通知标记为已读。 */
export async function markNotificationAsRead(id: number) {
  await http.put(`/notifications/${id}/read`)
}

/** 将当前登录用户的全部未读通知标记为已读。 */
export async function markAllNotificationsAsRead() {
  await http.put('/notifications/read-all')
}
