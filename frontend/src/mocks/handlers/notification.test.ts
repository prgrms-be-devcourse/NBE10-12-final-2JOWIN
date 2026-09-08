import { describe, expect, it, vi } from 'vitest'
import type { RequestHandler } from 'msw'
import { notificationHandlers, notificationSettingHandlers } from './notification'
import { db, session } from '../store'
import { demoAccounts } from '../fixtures'
import type { NotificationResponse, PageResponse } from '../../shared/api/types'

/**
 * 인앱 알림 목이 백엔드(NotificationController · NotificationService, #75)와 같은 규칙으로 답하는지.
 *
 * 실 API로 바꾸는 순간 화면이 갈라지는 지점만 고정한다: 정렬 createdAt DESC · id DESC ·
 * 본인 수신분만(타인 것은 읽음 처리도 404, SC-09) · 읽음 처리는 멱등(204) ·
 * 알림 수신 설정은 `notification` 키가 아니라 `notificationSettings` 키가 답한다.
 */

vi.stubGlobal('location', { href: 'http://localhost/', origin: 'http://localhost' })

async function call(handlers: RequestHandler[], request: Request): Promise<Response | null> {
  for (const handler of handlers) {
    const result = await handler.run({ request, requestId: crypto.randomUUID() })
    if (result?.response) return result.response
  }
  return null
}

const [admin, salesRep] = demoAccounts
const auth = (account: (typeof demoAccounts)[number]) => ({ Authorization: `Bearer ${account.accessToken}` })
const url = (path: string) => `http://localhost/api/v1${path}`

describe('GET /api/v1/notifications — 목록 (NT-08)', () => {
  it('정렬은 createdAt DESC, 같으면 id DESC — 서버 DEFAULT_SORT와 같다', async () => {
    session.login(salesRep)
    const res = (await call(notificationHandlers, new Request(url('/notifications?size=100'), { headers: auth(salesRep) })))!
    expect(res.status).toBe(200)
    const page: PageResponse<NotificationResponse> = await res.json()
    const keys = page.content.map((n) => `${n.createdAt}|${n.id}`)
    expect(keys).toEqual([...keys].sort().reverse())
  })

  it('unreadOnly=true면 readAt이 null인 것만', async () => {
    session.login(salesRep)
    const res = (await call(notificationHandlers, new Request(url('/notifications?unreadOnly=true&size=100'), { headers: auth(salesRep) })))!
    const page: PageResponse<NotificationResponse> = await res.json()
    expect(page.content.every((n) => n.readAt === null)).toBe(true)
  })
})

describe('POST /api/v1/notifications/{id}/read — 읽음 (NT-08, SC-09)', () => {
  it('타인 수신분은 404 · 본인 것은 204이고 두 번 불러도 204', async () => {
    const mine = db.notifications.find((n) => n.recipientMemberId === salesRep.memberId)!
    session.login(admin)
    expect((await call(notificationHandlers, new Request(url(`/notifications/${mine.id}/read`), { method: 'POST', headers: auth(admin) })))!.status).toBe(404)
    session.login(salesRep)
    expect((await call(notificationHandlers, new Request(url(`/notifications/${mine.id}/read`), { method: 'POST', headers: auth(salesRep) })))!.status).toBe(204)
    expect((await call(notificationHandlers, new Request(url(`/notifications/${mine.id}/read`), { method: 'POST', headers: auth(salesRep) })))!.status).toBe(204)
    expect(mine.readAt).not.toBeNull()
  })
})

describe('GET /api/v1/me/notification-settings — 수신 설정 (NT-07)', () => {
  it('notification 핸들러는 답하지 않고 notificationSettings 핸들러가 답한다 — 인앱 알림을 실 API로 돌려도 설정 탭은 목', async () => {
    session.login(salesRep)
    const request = () => new Request(url('/me/notification-settings'), { headers: auth(salesRep) })
    expect(await call(notificationHandlers, request())).toBeNull()
    expect((await call(notificationSettingHandlers, request()))?.status).toBe(200)
  })
})
