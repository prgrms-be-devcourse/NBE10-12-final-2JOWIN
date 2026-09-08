import { delay, http, HttpResponse } from 'msw'
import { currentMember, db, error, noContent, notFound, now, paged } from '../store'
import type { NotificationResponse, NotificationSettingResponse, UpdateNotificationSettingsRequest } from '../../shared/api/types'
import { MAIL_SETTING_TYPES } from '../../shared/ui/status'

/**
 * 인앱 알림 목 — notification/controller/NotificationController · NotificationResponse.
 * 본인 수신분만 (NT-08) — 타인 것은 읽음 처리도 404 (SC-09). 정렬 createdAt DESC · id DESC 고정.
 *
 * 알림 수신 설정(`/me/notification-settings`, NT-07)도 여기다 — 경로는 /me지만 소유는 D의 notification
 * 모듈(#127)이라 `notification` 키로 켜고 끈다. auth를 실 API로 돌려도 이 둘은 #127 전까지 목이다.
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

/**
 * 알림 수신 설정 (NT-07, Q-23 메일 채널만) — 인앱 알림과 별도 키 `notificationSettings`로 켜고 끈다.
 *
 * 인앱 알림 API(NotificationController)는 develop에 있어 실 API로 갔지만, `/me/notification-settings`는
 * D의 #132가 경계 계약·서비스만 넣었고 HTTP 엔드포인트는 A 몫(11 §2)이라 아직 없다. 그때까지 이 둘만 목이다.
 */
export const notificationSettingHandlers = [
  http.get('/api/v1/me/notification-settings', ({ request }) => {
    const member = currentMember(request)
    const saved = db.notificationSettings.get(member.id)
    // 행 없으면 기본 ON
    const body: NotificationSettingResponse = { settings: saved ?? MAIL_SETTING_TYPES.map((type) => ({ type, emailEnabled: true })) }
    return HttpResponse.json(body)
  }),

  http.put('/api/v1/me/notification-settings', async ({ request }) => {
    const member = currentMember(request)
    const body = (await request.json()) as UpdateNotificationSettingsRequest
    if (!body.settings?.length) return error('VALIDATION_FAILED', [{ field: 'settings', reason: '설정을 입력해 주세요.' }])
    db.notificationSettings.set(member.id, body.settings)
    const response: NotificationSettingResponse = { settings: body.settings }
    return HttpResponse.json(response)
  }),
]
