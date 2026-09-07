import { api } from '../../shared/api/client'
import type { NotificationResponse, PageParams, PageResponse } from '../../shared/api/types'

/** 인앱 알림 API — notification/controller/NotificationController (NT-08). 호출은 이 파일 안에서만 한다 (12 §8). */

export interface NotificationListParams extends PageParams {
  unreadOnly?: boolean
}

/** GET /api/v1/notifications?unreadOnly=&page=&size= — 본인 수신분, createdAt DESC 고정 */
export async function fetchNotifications(params: NotificationListParams = {}) {
  const { data } = await api.get<PageResponse<NotificationResponse>>('/notifications', { params })
  return data
}

/** POST /api/v1/notifications/{id}/read — 204 */
export async function markNotificationRead(id: string) {
  await api.post(`/notifications/${id}/read`)
}

/** POST /api/v1/notifications/read-all — 204 */
export async function markAllNotificationsRead() {
  await api.post('/notifications/read-all')
}
