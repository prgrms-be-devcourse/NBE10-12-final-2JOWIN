import { delay, http, HttpResponse } from 'msw'
import { currentMember, db, noContent, notFound, now, paged } from '../store'
import type { NotificationResponse } from '../../shared/api/types'

/**
 * 인앱 알림 목 — notification/controller/NotificationController · NotificationResponse.
 * 본인 수신분만 (NT-08) — 타인 것은 읽음 처리도 404 (SC-09). 정렬 createdAt DESC · id DESC 고정.
 */

const mine = (memberId: string) => db.notifications.filter((n) => n.recipientMemberId === memberId)
const toResponse = ({ recipientMemberId: _omit, ...n }: (typeof db.notifications)[number]): NotificationResponse => n

export const notificationHandlers = [
  http.get('/api/v1/notifications', async ({ request }) => {
    await delay(80)
    const member = currentMember(request)
    const url = new URL(request.url)
    const unreadOnly = url.searchParams.get('unreadOnly') === 'true'
    const list = mine(member.id)
      .filter((n) => !unreadOnly || n.readAt === null)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id))
      .map(toResponse)
    return HttpResponse.json(paged(list, url))
  }),

  http.post('/api/v1/notifications/:id/read', ({ params, request }) => {
    const member = currentMember(request)
    const notification = mine(member.id).find((n) => n.id === params.id)
    if (!notification) return notFound()
    notification.readAt ??= now()
    return noContent()
  }),

  http.post('/api/v1/notifications/read-all', ({ request }) => {
    const member = currentMember(request)
    const at = now()
    for (const n of mine(member.id)) n.readAt ??= at
    return noContent()
  }),
]
