import { http, HttpResponse } from 'msw'
import { db, error, findDeal, findQuote, noContent, notFound, notify, now, recordAudit, recordAuto } from '../store'
import type { ApproveQuoteRequest, CreateInquiryRequest, PublicQuoteResponse, RejectQuoteRequest } from '../../shared/api/types'

/**
 * 고객 열람 목 (`/public/api/v1`) — 토큰이 곧 인증 (SC-07~09) · approval/dto.
 *
 * 데모 링크 (픽스처 viewTokens.rawToken):
 *  - `/q/demo-dodam-14`      메인 시나리오 — 응답 가능 (S-01 5막)
 *  - `/q/demo-hanul-16`      단가 재조정안 — 응답 가능
 *  - `/q/demo-shinyoung-01`  응답 완료 — 열람은 되고 재응답만 막힌다 (AP-11, 전이표 §7)
 *  - `/q/demo-mirae-05`      만료 — 410 LINK_EXPIRED (AP-05)
 *  - 그 외 문자열            404 — 존재 여부를 노출하지 않는다 (SC-09)
 *
 * 승인·반려는 견적 상태를 바꾸고(C의 도메인 메서드에 해당), 담당자에게 알림을 남긴다 (NT-04, AP-12).
 *
 * 판정 순서는 서버 CustomerQuoteService(#156)와 같다 — 400(@Valid) → 404 → 410 LINK_EXPIRED → 409 LINK_ALREADY_RESPONDED
 * → 409 COMPANY_SUSPENDED → 409 QUOTE_NOT_RESPONDABLE. 문의는 응답 완료 뒤에도 되지만 정지 회사는 막힌다 (07 §D).
 * 길이 제한도 서버 DTO 그대로 — 이름·직책 50 · 반려 사유 500 · 문의 1000.
 */

const MAX = { responder: 50, reason: 500, inquiry: 1000 } as const
const tooLong = (value: string | undefined | null, max: number) => Boolean(value && value.length > max)

/** 응답자 정보 @Valid 미러 — ApproveQuoteRequest·RejectQuoteRequest 공통 */
const responderErrors = (body: { responderName?: string; responderTitle?: string | null }) => [
  ...(!body.responderName?.trim() ? [{ field: 'responderName', reason: '이름을 입력해 주세요.' }] : []),
  ...(tooLong(body.responderName, MAX.responder) ? [{ field: 'responderName', reason: `${MAX.responder}자 이하로 입력해 주세요.` }] : []),
  ...(tooLong(body.responderTitle, MAX.responder) ? [{ field: 'responderTitle', reason: `${MAX.responder}자 이하로 입력해 주세요.` }] : []),
]

const tokenOf = (raw: string) => db.viewTokens.find((t) => t.rawToken === raw)

/** PublicQuoteResponse 조립 — 담당자는 Deal의 현재 담당자 (AP-18) */
export function buildPublicQuote(quoteId: string, respondable: boolean): PublicQuoteResponse | null {
  const quote = findQuote(quoteId)
  const deal = quote && findDeal(quote.dealId)
  if (!quote || !deal) return null
  const assignee = db.members.find((m) => m.id === deal.assigneeMemberId)
  const company = db.companies[0]
  return {
    quoteNo: quote.quoteNo,
    status: quote.status,
    companyName: company.name,
    companyBusinessNo: company.businessNo,
    assignee: { name: assignee?.name ?? '', email: assignee?.email ?? '', phone: assignee?.phone ?? '' },
    vatMode: quote.vatMode,
    terms: quote.terms,
    validUntil: quote.validUntil,
    supplyAmount: quote.supplyAmount,
    vatAmount: quote.vatAmount,
    totalAmount: quote.totalAmount,
    items: (db.quoteItems.get(quote.id) ?? [])
      .slice()
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map(({ name, unit, quantity, unitPrice, amount }) => ({ name, unit, quantity, unitPrice, amount })),
    // 정지 회사면 열람만 (SC-10, Q-27)
    respondable: respondable && company.status === 'ACTIVE',
  }
}

/** 응답 가능 조건 — 링크 활성 + 견적이 발송됨·열람됨 */
const respondable = (token: NonNullable<ReturnType<typeof tokenOf>>) => {
  const quote = findQuote(token.quoteId)
  return token.status === 'ACTIVE' && (quote?.status === 'SENT' || quote?.status === 'VIEWED')
}

/**
 * 승인·반려 공통 전처리 (서버 preRespond) — 링크·회사·견적 상태 순으로 거른다.
 * 통과하면 SENT는 VIEWED로 올린다 (전이표 §6 — 응답은 열람됨에서만).
 */
function preRespond(token: NonNullable<ReturnType<typeof tokenOf>>) {
  if (token.status === 'EXPIRED') return error('LINK_EXPIRED')
  if (token.status === 'RESPONDED') return error('LINK_ALREADY_RESPONDED')
  if (db.companies[0].status !== 'ACTIVE') return error('COMPANY_SUSPENDED')
  const quote = findQuote(token.quoteId)!
  if (quote.status === 'SENT') {
    quote.status = 'VIEWED'
    quote.firstViewedAt = now()
    quote.version += 1
  }
  if (quote.status !== 'VIEWED') return error('QUOTE_NOT_RESPONDABLE')
  return null
}

export const publicQuoteHandlers = [
  http.get('/public/api/v1/quotes/:token', ({ params }) => {
    const token = tokenOf(String(params.token))
    if (!token) return notFound()
    // 410은 만료 링크만 — 응답 완료(RESPONDED) 링크도 열람은 허용 (전이표 §7, v1.6.1)
    if (token.status === 'EXPIRED') return error('LINK_EXPIRED')
    const quote = findQuote(token.quoteId)!
    // 첫 열람 시각 기록 + SENT → VIEWED (AP-02·07) + 담당자 알림 (NT-03)
    if (quote.status === 'SENT' && token.status === 'ACTIVE') {
      quote.status = 'VIEWED'
      quote.firstViewedAt = now()
      quote.version += 1
      recordAudit({ entityType: 'QUOTE', entityId: quote.id, eventType: 'QUOTE_VIEWED', actorType: 'CUSTOMER_LINK', actorId: null, changes: { status: { before: 'SENT', after: 'VIEWED' } } })
      recordAuto(quote.dealId, `고객이 견적을 열람했습니다 — ${quote.quoteNo}`)
      const deal = findDeal(quote.dealId)
      notify(quote.dealId, 'QUOTE_VIEWED', `${deal?.customerName ?? ''} 담당자가 견적을 열람했습니다 (${quote.quoteNo})`, quote.id)
    }
    const body = buildPublicQuote(token.quoteId, respondable(token))
    return body ? HttpResponse.json(body) : notFound()
  }),

  http.post('/public/api/v1/quotes/:token/approve', async ({ params, request }) => {
    const body = (await request.json()) as ApproveQuoteRequest
    const fieldErrors = responderErrors(body)
    if (fieldErrors.length) return error('VALIDATION_FAILED', fieldErrors)
    const token = tokenOf(String(params.token))
    if (!token) return notFound()
    const blocked = preRespond(token)
    if (blocked) return blocked
    const quote = findQuote(token.quoteId)!
    const before = quote.status
    quote.status = 'APPROVED'
    quote.respondedAt = now()
    quote.responderName = body.responderName.trim()
    quote.responderTitle = body.responderTitle?.trim() || null
    quote.version += 1
    token.status = 'RESPONDED'
    recordAudit({ entityType: 'QUOTE', entityId: quote.id, eventType: 'QUOTE_APPROVED', actorType: 'CUSTOMER_LINK', actorId: null, changes: { status: { before, after: 'APPROVED' } } })
    recordAuto(quote.dealId, `고객이 견적을 승인했습니다 — ${quote.quoteNo}`)
    const deal = findDeal(quote.dealId)
    notify(quote.dealId, 'QUOTE_APPROVED', `${deal?.customerName ?? ''} 담당자가 견적을 승인했습니다 (${quote.quoteNo})`, quote.id)
    return noContent()
  }),

  http.post('/public/api/v1/quotes/:token/reject', async ({ params, request }) => {
    const body = (await request.json()) as RejectQuoteRequest
    const fieldErrors = [
      ...(!body.reason?.trim() ? [{ field: 'reason', reason: '반려 사유를 입력해 주세요.' }] : []),
      ...(tooLong(body.reason, MAX.reason) ? [{ field: 'reason', reason: `${MAX.reason}자 이하로 입력해 주세요.` }] : []),
      ...responderErrors(body),
    ]
    if (fieldErrors.length) return error('VALIDATION_FAILED', fieldErrors)
    const token = tokenOf(String(params.token))
    if (!token) return notFound()
    const blocked = preRespond(token)
    if (blocked) return blocked
    const quote = findQuote(token.quoteId)!
    const before = quote.status
    quote.status = 'REJECTED'
    quote.respondedAt = now()
    quote.rejectReason = body.reason.trim()
    quote.responderName = body.responderName.trim()
    quote.responderTitle = body.responderTitle?.trim() || null
    quote.version += 1
    token.status = 'RESPONDED'
    recordAudit({ entityType: 'QUOTE', entityId: quote.id, eventType: 'QUOTE_REJECTED', actorType: 'CUSTOMER_LINK', actorId: null, changes: { status: { before, after: 'REJECTED' } } })
    recordAuto(quote.dealId, `고객이 견적을 반려했습니다 — ${quote.quoteNo}`)
    const deal = findDeal(quote.dealId)
    notify(quote.dealId, 'QUOTE_REJECTED', `${deal?.customerName ?? ''} 담당자가 견적을 반려했습니다 (${quote.quoteNo})`, quote.id)
    return noContent()
  }),

  // 문의는 응답 완료 후에도 남길 수 있다 — 재응답 차단(AP-11)과 무관하다. 정지 회사만 막힌다 (07 §D, 서버 createInquiry).
  // 담당 구성원 + 기업 관리자 알림 (NT-10)
  http.post('/public/api/v1/quotes/:token/inquiries', async ({ params, request }) => {
    const body = (await request.json()) as CreateInquiryRequest
    const fieldErrors = [
      ...(!body.content?.trim() ? [{ field: 'content', reason: '문의 내용을 입력해 주세요.' }] : []),
      ...(tooLong(body.content, MAX.inquiry) ? [{ field: 'content', reason: `${MAX.inquiry}자 이하로 입력해 주세요.` }] : []),
    ]
    if (fieldErrors.length) return error('VALIDATION_FAILED', fieldErrors)
    const token = tokenOf(String(params.token))
    if (!token) return notFound()
    if (token.status === 'EXPIRED') return error('LINK_EXPIRED')
    if (db.companies[0].status !== 'ACTIVE') return error('COMPANY_SUSPENDED')
    const quote = findQuote(token.quoteId)!
    db.inquiries.push({ id: crypto.randomUUID(), quoteId: quote.id, content: body.content.trim(), createdAt: now() })
    const deal = findDeal(quote.dealId)
    notify(quote.dealId, 'INQUIRY_RECEIVED', `${deal?.customerName ?? ''} 담당자가 문의를 남겼습니다 (${quote.quoteNo})`, quote.id)
    return noContent()
  }),
]
