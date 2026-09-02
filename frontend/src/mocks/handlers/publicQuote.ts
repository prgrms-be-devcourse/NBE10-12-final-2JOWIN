import { http, HttpResponse } from 'msw'
import { COMPANY, contacts, customers, deals, members, quoteExtras, quoteItems, quotes, viewTokens } from '../fixtures'
import type { PublicQuoteResponse } from '../../shared/api/types'

/**
 * 고객 열람 목 (`/public/api/v1`) — 토큰이 곧 인증 (SC-07~09).
 *
 * 데모 링크:
 *  - `/q/demo-dodam-14`      메인 시나리오 — 응답 가능 (S-01 3막)
 *  - `/q/demo-hanul-16`      단가 재조정안 — 응답 가능
 *  - `/q/demo-shinyoung-01`  응답 완료 — 열람은 되고 재응답만 막힌다 (AP-11, 전이표 §7)
 *  - `/q/demo-mirae-05`      만료 — 410 LINK_EXPIRED (AP-05)
 *  - 그 외 문자열            404 — 존재 여부를 노출하지 않는다 (SC-09)
 */

const error = (code: string, message: string, status: number) =>
  HttpResponse.json({ code, message, fieldErrors: [] }, { status })

/** 응답 완료 표시 — 목에서만 쓰는 상태. 실제로는 quote.status가 바뀐다 */
const responded = new Set<string>()

function build(token: string): PublicQuoteResponse | null {
  const entry = viewTokens[token]
  if (!entry) return null

  const quote = quotes.find((q) => q.id === entry.quoteId)
  if (!quote) return null

  const deal = deals.find((d) => d.id === quote.dealId)
  const assignee = members.find((m) => m.id === deal?.assigneeMemberId)
  const extras = quoteExtras[quote.id]
  const items = quoteItems[quote.id] ?? []
  // 금액은 픽스처가 원본이다 — 합계는 quotes에서 오고, 부가세는 그 차액이다.
  // 여기서 다시 계산하면 픽스처와 어긋날 수 있고, 그 순간 목과 시연 데이터가 갈라진다
  const supplyAmount = items.reduce((sum, item) => sum + item.amount, 0)

  return {
    quoteNo: quote.quoteNo,
    status: quote.status,
    companyName: COMPANY.name,
    companyBusinessNo: COMPANY.businessNo,
    assignee: {
      // AP-18 — 발송자가 아니라 Deal의 현재 담당자
      name: assignee?.name ?? '',
      email: assignee?.email ?? '',
      phone: assignee?.phone ?? '',
    },
    vatMode: extras?.vatMode ?? 'EXCLUDED',
    terms: extras?.terms ?? null,
    validUntil: quote.validUntil,
    supplyAmount,
    vatAmount: quote.totalAmount - supplyAmount,
    totalAmount: quote.totalAmount,
    items: items.map(({ name, unit, quantity, unitPrice, amount }) => ({
      name, unit, quantity, unitPrice, amount,
    })),
    respondable: entry.respondable && !responded.has(token),
  }
}

/** 고객사 이름 — 열람 화면의 "OOO 님 귀하"에 쓴다 */
export function recipientOf(token: string): { customerName: string; contactName: string } | null {
  const entry = viewTokens[token]
  const quote = quotes.find((q) => q.id === entry?.quoteId)
  const deal = deals.find((d) => d.id === quote?.dealId)
  const customer = customers.find((c) => c.id === deal?.customerId)
  if (!customer) return null
  const contact = (contacts[customer.id] ?? []).find((c) => c.primary)
  return { customerName: customer.name, contactName: contact?.name ?? '' }
}

export const publicQuoteHandlers = [
  http.get('/public/api/v1/quotes/:token', ({ params }) => {
    const token = String(params.token)
    const quote = build(token)
    if (!quote) return error('RESOURCE_NOT_FOUND', '요청한 대상을 찾을 수 없습니다.', 404)
    if (quote.status === 'EXPIRED') {
      return error('LINK_EXPIRED', '만료된 링크입니다. 담당자에게 재발송을 요청해 주세요.', 410)
    }
    return HttpResponse.json(quote)
  }),

  http.post('/public/api/v1/quotes/:token/approve', ({ params }) => {
    const token = String(params.token)
    const quote = build(token)
    if (!quote) return error('RESOURCE_NOT_FOUND', '요청한 대상을 찾을 수 없습니다.', 404)
    if (!quote.respondable) {
      return error('LINK_ALREADY_RESPONDED', '이미 응답이 완료된 견적입니다.', 409)
    }
    responded.add(token)
    return new HttpResponse(null, { status: 204 })
  }),

  http.post('/public/api/v1/quotes/:token/reject', ({ params }) => {
    const token = String(params.token)
    const quote = build(token)
    if (!quote) return error('RESOURCE_NOT_FOUND', '요청한 대상을 찾을 수 없습니다.', 404)
    if (!quote.respondable) {
      return error('LINK_ALREADY_RESPONDED', '이미 응답이 완료된 견적입니다.', 409)
    }
    responded.add(token)
    return new HttpResponse(null, { status: 204 })
  }),

  // 문의는 응답 완료 후에도 남길 수 있다 — 재응답 차단(AP-11)과 무관하다
  http.post('/public/api/v1/quotes/:token/inquiries', () => new HttpResponse(null, { status: 204 })),
]
