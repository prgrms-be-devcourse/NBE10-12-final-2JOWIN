import { describe, expect, it, vi } from 'vitest'
import type { RequestHandler } from 'msw'
import { orderHandlers } from './order'
import { db, session } from '../store'
import { demoAccounts } from '../fixtures'
import type { OrderDetailResponse, OrderResponse, PageResponse } from '../../shared/api/types'

/**
 * 주문 목이 백엔드(OrderController · OrderService, #160·#197)와 같은 규칙으로 답하는지.
 *
 * 실 API 전환(#129)에서 서버 실응답과 대조해 어긋난 자리를 고정한다:
 * **착수일·납기 PATCH는 두 날짜를 함께 덮어쓴다** — 하나만 보내면 나머지가 지워진다.
 * 08 §B의 "안 보내면 미변경"과 다른 자리라, 목이 생략을 미변경으로 받으면 실 API에서 값이 사라진다.
 * `dealStage`는 상세에만 실린다 (08 v1.6.18) · 목록의 `from`·`to`는 전환일 기준으로 양끝을 포함한다.
 */

vi.stubGlobal('location', { href: 'http://localhost/', origin: 'http://localhost' })

async function call(handlers: RequestHandler[], request: Request): Promise<Response | null> {
  for (const handler of handlers) {
    const result = await handler.run({ request, requestId: crypto.randomUUID() })
    if (result?.response) return result.response
  }
  return null
}

const [admin] = demoAccounts
const auth = () => ({ Authorization: `Bearer ${admin.accessToken}` })
const url = (path: string) => `http://localhost/api/v1${path}`
const get = (path: string) => call(orderHandlers, new Request(url(path), { headers: auth() }))
const patchSchedule = (id: string, body: unknown) =>
  call(orderHandlers, new Request(url(`/orders/${id}/schedule`), {
    method: 'PATCH', headers: { ...auth(), 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  }))

describe('GET /api/v1/orders — 목록 (OD-08)', () => {
  it('정렬은 createdAt DESC — 서버 DEFAULT_SORT와 같다', async () => {
    session.login(admin)
    const res = (await get('/orders?size=100'))!
    expect(res.status).toBe(200)
    const page: PageResponse<OrderResponse> = await res.json()
    const keys = page.content.map((o) => o.createdAt)
    expect(keys).toEqual([...keys].sort().reverse())
  })

  it('목록 줄에는 dealStage가 없다 — 상세에만 실린다 (08 v1.6.18)', async () => {
    session.login(admin)
    const res = (await get('/orders?size=100'))!
    const page: PageResponse<OrderResponse> = await res.json()
    expect(page.content.length).toBeGreaterThan(0)
    expect(page.content.every((o) => !('dealStage' in o))).toBe(true)
  })

  it('from·to는 전환일 기준으로 양끝을 포함한다', async () => {
    session.login(admin)
    const all: PageResponse<OrderResponse> = await (await get('/orders?size=100'))!.json()
    const day = all.content[0].createdAt.slice(0, 10)
    const res = (await get(`/orders?from=${day}&to=${day}&size=100`))!
    const page: PageResponse<OrderResponse> = await res.json()
    expect(page.content.length).toBeGreaterThan(0)
    expect(page.content.every((o) => o.createdAt.slice(0, 10) === day)).toBe(true)
  })
})

describe('GET /api/v1/orders/{id} — 상세 (OD-09)', () => {
  it('dealStage가 실린다 — 전환의 자동 성사(OD-06) 확인용', async () => {
    session.login(admin)
    const all: PageResponse<OrderResponse> = await (await get('/orders?size=100'))!.json()
    const res = (await get(`/orders/${all.content[0].id}`))!
    const detail: OrderDetailResponse = await res.json()
    expect(detail.dealStage).toBeTruthy()
    expect(detail.items.length).toBeGreaterThan(0)
  })

  it('없는 주문은 404', async () => {
    session.login(admin)
    const res = (await get('/orders/00000000-0000-4000-8000-000000000000'))!
    expect(res.status).toBe(404)
  })
})

describe('PATCH /api/v1/orders/{id}/schedule — 착수일·납기 (OD-10)', () => {
  it('두 날짜를 함께 덮어쓴다 — 하나만 보내면 나머지가 지워진다', async () => {
    session.login(admin)
    const target = db.orders[0]
    await patchSchedule(target.id, { startDate: '2026-09-01', deliveryDate: '2026-09-12' })

    // deliveryDate를 빼고 보낸다 — 08 §B라면 미변경이지만 여기서는 지움이다
    const res = (await patchSchedule(target.id, { startDate: '2026-09-05' }))!
    const detail: OrderDetailResponse = await res.json()
    expect(detail.startDate).toBe('2026-09-05')
    expect(detail.deliveryDate).toBeNull()
  })

  it('빈 문자열도 지움으로 받는다', async () => {
    session.login(admin)
    const target = db.orders[0]
    await patchSchedule(target.id, { startDate: '2026-09-01', deliveryDate: '2026-09-12' })
    const res = (await patchSchedule(target.id, { startDate: '', deliveryDate: '' }))!
    const detail: OrderDetailResponse = await res.json()
    expect(detail.startDate).toBeNull()
    expect(detail.deliveryDate).toBeNull()
  })
})
