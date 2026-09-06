import { delay, http, HttpResponse } from 'msw'
import { canSee, currentMember, db, findDeal, notFound, paged } from '../store'
import type { OrderDetailResponse, OrderResponse, OrderScheduleRequest } from '../../shared/api/types'

/**
 * 주문 목 (`/api/v1/orders`) — 07-api-spec.md §C (OD-08~10) · 08-dto.md §C.
 * 주문은 상태가 없다(Q-09) — 생성·조회·착수일/납기 기록만 존재한다. 전환은 quote.ts의 convert-to-order.
 * 담당 스코프는 주문이 딸린 Deal의 담당자 기준 (SC-04).
 */

const BASE = '/api/v1/orders'
type OrderRow = (typeof db.orders)[number]

function visibleOrder(request: Request, id: string): OrderRow | undefined {
  const order = db.orders.find((o) => o.id === id)
  const deal = order && findDeal(order.dealId)
  if (!order || !deal || !canSee(currentMember(request), deal)) return undefined
  return order
}

const toResponse = (o: OrderRow): OrderResponse => o
const toDetail = (o: OrderRow): OrderDetailResponse => ({ ...o, items: db.orderItems.get(o.id) ?? [] })

export const orderHandlers = [
  // 목록 (OD-08) — from·to는 생성일 기준 (YYYY-MM-DD) · createdAt DESC
  http.get(BASE, async ({ request }) => {
    await delay(120)
    const member = currentMember(request)
    const url = new URL(request.url)
    const from = url.searchParams.get('from')
    const to = url.searchParams.get('to')
    const list = db.orders
      .filter((o) => {
        const deal = findDeal(o.dealId)
        return deal && canSee(member, deal)
      })
      .filter((o) => !from || o.createdAt.slice(0, 10) >= from)
      .filter((o) => !to || o.createdAt.slice(0, 10) <= to)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(toResponse)
    return HttpResponse.json(paged(list, url))
  }),

  // 상세 (OD-09) — 스냅샷 항목
  http.get(`${BASE}/:id`, async ({ params, request }) => {
    await delay(120)
    const order = visibleOrder(request, String(params.id))
    if (!order) return notFound()
    return HttpResponse.json(toDetail(order))
  }),

  // 착수일·납기 기록 (OD-10) — 날짜 필드이지 상태가 아니다
  http.patch(`${BASE}/:id/schedule`, async ({ params, request }) => {
    const order = visibleOrder(request, String(params.id))
    if (!order) return notFound()
    const body = (await request.json()) as OrderScheduleRequest
    if (body.startDate !== undefined) order.startDate = body.startDate || null
    if (body.deliveryDate !== undefined) order.deliveryDate = body.deliveryDate || null
    return HttpResponse.json(toDetail(order))
  }),
]
