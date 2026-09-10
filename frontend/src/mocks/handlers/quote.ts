import { delay, http, HttpResponse } from 'msw'
import {
  activeTokenOf, bumpVersion, canSee, contactsOf, currentMember, db, error, findDeal, findQuote, moveDealStage, nextDocNo,
  noContent, notFound, now, paged, recordAudit, recordAuto, today,
} from '../store'
import { buildPublicQuote } from './publicQuote'
import type {
  CreateQuoteRequest, OrderDetailResponse, QuoteDetailResponse, QuoteResponse, ResendViewTokenRequest, SendQuoteRequest,
  SendQuoteResponse, UpdateQuoteRequest,
} from '../../shared/api/types'
import { VAT_MODES, isOpenStage } from '../../shared/ui/status'

/**
 * 견적 목 (구성원용 `/api/v1/quotes`) — 07-api-spec.md §C (QT · AP-13·14 · OD-01~07) · 08-dto.md §C.
 * 정본은 백엔드 QuoteService(#101·#154)다 — 응답 형태·판정 순서를 그쪽에 맞춘다. 복제·주문 전환만 아직 서버에 없어 docs 기준이다.
 *
 * 실패 경로: QUOTE_NOT_DRAFT · QUOTE_EMPTY_ITEMS · QUOTE_VALID_UNTIL_PASSED · CONTACT_NOT_IN_CUSTOMER ·
 * QUOTE_DEAL_CLOSED · PRODUCT_DISCONTINUED · QUOTE_NOT_WITHDRAWABLE · QUOTE_NOT_RESENDABLE ·
 * QUOTE_NOT_APPROVED · QUOTE_ALREADY_CONVERTED · STALE_VERSION · 404(담당 스코프 밖 포함, SC-09).
 *
 * 금액 3필드는 항상 서버 계산(QT-08·22) — 요청의 금액은 무시하고 여기서 다시 계산한다.
 */

const BASE = '/api/v1/quotes'
type QuoteRow = (typeof db.quotes)[number]

/** 담당 스코프 — 영업 담당자는 본인 담당 Deal의 견적만. 밖이면 404 (SC-04·09) */
function visibleQuote(request: Request, id: string): QuoteRow | undefined {
  const quote = findQuote(id)
  const deal = quote && findDeal(quote.dealId)
  if (!quote || !deal || !canSee(currentMember(request), deal)) return undefined
  return quote
}

const itemsOf = (quoteId: string) => (db.quoteItems.get(quoteId) ?? []).slice().sort((a, b) => a.sortOrder - b.sortOrder)

const toList = (q: QuoteRow): QuoteResponse => ({
  id: q.id, quoteNo: q.quoteNo, dealId: q.dealId, status: q.status, totalAmount: q.totalAmount,
  validUntil: q.validUntil, sentAt: q.sentAt, firstViewedAt: q.firstViewedAt, version: q.version,
})

const toDetail = (q: QuoteRow): QuoteDetailResponse => ({
  id: q.id, quoteNo: q.quoteNo, dealId: q.dealId, dealTitle: findDeal(q.dealId)?.title ?? '', status: q.status,
  vatMode: q.vatMode, terms: q.terms, validUntil: q.validUntil,
  supplyAmount: q.supplyAmount, vatAmount: q.vatAmount, totalAmount: q.totalAmount,
  items: itemsOf(q.id),
  clonedFromQuoteId: q.clonedFromQuoteId, supersededByQuoteId: q.supersededByQuoteId,
  rejectReason: q.rejectReason, responderName: q.responderName, responderTitle: q.responderTitle,
  sentAt: q.sentAt, firstViewedAt: q.firstViewedAt, respondedAt: q.respondedAt,
  version: q.version, createdAt: q.createdAt,
})

/**
 * 서버 계산 미러 (quote/entity/QuoteAmounts — QT-08·22, Q-46).
 * 단가는 항상 세전: supply = 항목 합계 · vat = round(supply × 10%) · total = supply + vat.
 * vatMode는 견적서 표시 기준일 뿐 금액에 영향이 없다 (Q-46).
 */
function calc(items: { quantity: number; unitPrice: number }[]) {
  const supplyAmount = items.reduce((acc, i) => acc + i.quantity * i.unitPrice, 0)
  const vatAmount = Math.round(supplyAmount * 0.1)
  return { supplyAmount, vatAmount, totalAmount: supplyAmount + vatAmount }
}

/** 열람 링크 발급 — 만료일 = 유효기간 당일 23:59:59 KST (Q-17). 목 전용 원문 토큰은 `link-{quoteId}` */
function issueToken(quote: QuoteRow, recipientContactId: string) {
  db.viewTokens.push({
    id: crypto.randomUUID(), quoteId: quote.id, recipientContactId, status: 'ACTIVE', expiredReason: null,
    expiresAt: `${quote.validUntil}T14:59:59Z`, rawToken: `link-${quote.id}`,
  })
}

export const quoteHandlers = [
  // 목록 · 상태 조회 (QT-20) — 담당 스코프 · createdAt DESC
  http.get(BASE, async ({ request }) => {
    await delay(120)
    const member = currentMember(request)
    const url = new URL(request.url)
    const status = url.searchParams.get('status')
    const dealId = url.searchParams.get('dealId')
    const list = db.quotes
      .filter((q) => {
        const deal = findDeal(q.dealId)
        return deal && canSee(member, deal)
      })
      .filter((q) => !status || q.status === status)
      .filter((q) => !dealId || q.dealId === dealId)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(toList)
    return HttpResponse.json(paged(list, url))
  }),

  // 작성 시작 (QT-01) → DRAFT. 종결 Deal 불가 (Q-25)
  http.post(BASE, async ({ request }) => {
    const member = currentMember(request)
    const body = (await request.json()) as CreateQuoteRequest
    if (!body.dealId) return error('VALIDATION_FAILED', [{ field: 'dealId', reason: 'Deal을 지정해 주세요.' }])
    const deal = findDeal(body.dealId)
    if (!deal || !canSee(member, deal)) return notFound()
    if (!isOpenStage(deal.stage)) return error('QUOTE_DEAL_CLOSED')
    const validUntil = new Date(Date.now() + 14 * 86_400_000).toISOString().slice(0, 10)
    const created: QuoteRow = {
      id: crypto.randomUUID(), quoteNo: nextDocNo('QUOTE'), dealId: deal.id, status: 'DRAFT', vatMode: 'EXCLUDED', terms: null,
      validUntil, supplyAmount: 0, vatAmount: 0, totalAmount: 0, clonedFromQuoteId: null, supersededByQuoteId: null,
      rejectReason: null, responderName: null, responderTitle: null, sentAt: null, firstViewedAt: null, respondedAt: null,
      version: 0, createdAt: now(),
    }
    db.quotes.unshift(created)
    db.quoteItems.set(created.id, [])
    recordAuto(deal.id, `견적 작성을 시작했습니다 — ${created.quoteNo}`, member.id)
    return HttpResponse.json(toDetail(created), { status: 201 })
  }),

  // 상세 (AP-06·07, QT-28)
  http.get(`${BASE}/:id`, async ({ params, request }) => {
    await delay(120)
    const quote = visibleQuote(request, String(params.id))
    if (!quote) return notFound()
    return HttpResponse.json(toDetail(quote))
  }),

  // 작성 중 전체 갱신 (QT-02~11·23) — PUT · DRAFT만 · version
  http.put(`${BASE}/:id`, async ({ params, request }) => {
    const quote = visibleQuote(request, String(params.id))
    if (!quote) return notFound()
    if (quote.status !== 'DRAFT') return error('QUOTE_NOT_DRAFT')
    const body = (await request.json()) as UpdateQuoteRequest
    const fieldErrors = [
      ...(!body.validUntil || body.validUntil <= today() ? [{ field: 'validUntil', reason: '오늘 이후 날짜여야 합니다.' }] : []),
      ...(!VAT_MODES.includes(body.vatMode) ? [{ field: 'vatMode', reason: '부가세 방식을 선택해 주세요.' }] : []),
      ...(!body.items?.length ? [{ field: 'items', reason: '항목을 1개 이상 추가해 주세요.' }] : []),
      ...(body.terms && body.terms.length > 2000 ? [{ field: 'terms', reason: '2000자 이하로 입력해 주세요.' }] : []),
      ...((body.items ?? []).flatMap((it, i) => [
        ...(!it.name?.trim() ? [{ field: `items[${i}].name`, reason: '품목명을 입력해 주세요.' }] : []),
        ...(!it.unit?.trim() ? [{ field: `items[${i}].unit`, reason: '단위를 입력해 주세요.' }] : []),
        ...(!(it.quantity > 0) ? [{ field: `items[${i}].quantity`, reason: '수량은 1 이상이어야 합니다.' }] : []),
        ...(it.unitPrice < 0 ? [{ field: `items[${i}].unitPrice`, reason: '단가는 0원 이상이어야 합니다.' }] : []),
      ])),
    ]
    if (fieldErrors.length) return error('VALIDATION_FAILED', fieldErrors)
    // 판매 중지 상품은 항목에 추가할 수 없다 (PR-06)
    if (body.items.some((it) => it.productId && db.products.find((p) => p.id === it.productId)?.status === 'DISCONTINUED')) return error('PRODUCT_DISCONTINUED')
    if (!bumpVersion(quote, body.version)) return error('STALE_VERSION')

    const items = body.items
      .slice()
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map((it, i) => {
        const product = it.productId ? db.products.find((p) => p.id === it.productId) : undefined
        return {
          id: crypto.randomUUID(), productId: product?.id ?? null, name: it.name.trim(), unit: it.unit.trim(),
          quantity: it.quantity, unitPrice: it.unitPrice, amount: it.quantity * it.unitPrice,
          catalogPriceAtCreation: product?.unitPrice ?? null, // QT-24 작성 시점 카탈로그 단가
          sortOrder: i,
        }
      })
    db.quoteItems.set(quote.id, items)
    quote.validUntil = body.validUntil
    quote.vatMode = body.vatMode
    quote.terms = body.terms?.trim() || null
    Object.assign(quote, calc(items))
    return HttpResponse.json(toDetail(quote))
  }),

  // 고객 화면 미리보기 (QT-12) — D의 PublicQuoteResponse 재사용
  http.get(`${BASE}/:id/preview`, ({ params, request }) => {
    const quote = visibleQuote(request, String(params.id))
    if (!quote) return notFound()
    const body = buildPublicQuote(quote.id, false)
    return body ? HttpResponse.json(body) : notFound()
  }),

  // 발송 (QT-13~16, AP-01, Q-25) — 링크 발급은 같은 트랜잭션 (Q-40)
  http.post(`${BASE}/:id/send`, async ({ params, request }) => {
    const member = currentMember(request)
    const quote = visibleQuote(request, String(params.id))
    if (!quote) return notFound()
    const deal = findDeal(quote.dealId)!
    if (quote.status !== 'DRAFT') return error('QUOTE_NOT_DRAFT')
    if (!isOpenStage(deal.stage)) return error('QUOTE_DEAL_CLOSED')
    const items = itemsOf(quote.id)
    if (items.length === 0) return error('QUOTE_EMPTY_ITEMS')
    if (quote.validUntil < today()) return error('QUOTE_VALID_UNTIL_PASSED')
    if (items.some((it) => it.productId && db.products.find((p) => p.id === it.productId)?.status === 'DISCONTINUED')) return error('PRODUCT_DISCONTINUED')
    const body = (await request.json()) as SendQuoteRequest
    if (!body.recipientContactId) return error('VALIDATION_FAILED', [{ field: 'recipientContactId', reason: '수신인을 선택해 주세요.' }])
    if (body.message && body.message.length > 500) return error('VALIDATION_FAILED', [{ field: 'message', reason: '500자 이하로 입력해 주세요.' }])
    // quote→deal→customer_id와 contact→customer_id 일치 검증 — 서비스 검증이 유일 방어
    if (!contactsOf(deal.customerId).some((c) => c.id === body.recipientContactId)) return error('CONTACT_NOT_IN_CUSTOMER')

    quote.status = 'SENT'
    quote.sentAt = now()
    quote.version += 1
    issueToken(quote, body.recipientContactId)
    // 단계가 견적(QUOTE) 미만이면 자동 승급 (Q-25) — DL-07의 예외인 시스템 전이
    if (deal.stage === 'LEAD' || deal.stage === 'CONSULT') moveDealStage(deal.id, 'QUOTE', null)
    recordAudit({ entityType: 'QUOTE', entityId: quote.id, eventType: 'QUOTE_SENT', actorType: 'MEMBER', actorId: member.id, changes: { status: { before: 'DRAFT', after: 'SENT' } } })
    recordAuto(deal.id, `견적을 발송했습니다 — ${quote.quoteNo}`, member.id)
    const response: SendQuoteResponse = { quoteId: quote.id, status: 'SENT', dealStage: findDeal(deal.id)!.stage, version: quote.version }
    return HttpResponse.json(response)
  }),

  // 회수 (QT-17) — 발송됨·열람됨만 · 링크 즉시 만료 · 종결 Deal에서도 가능(정리 목적)
  http.post(`${BASE}/:id/withdraw`, ({ params, request }) => {
    const member = currentMember(request)
    const quote = visibleQuote(request, String(params.id))
    if (!quote) return notFound()
    if (quote.status !== 'SENT' && quote.status !== 'VIEWED') return error('QUOTE_NOT_WITHDRAWABLE')
    const before = quote.status
    quote.status = 'WITHDRAWN'
    quote.version += 1
    const token = activeTokenOf(quote.id)
    if (token) {
      token.status = 'EXPIRED'
      token.expiredReason = 'WITHDRAWN'
    }
    recordAudit({ entityType: 'QUOTE', entityId: quote.id, eventType: 'QUOTE_WITHDRAWN', actorType: 'MEMBER', actorId: member.id, changes: { status: { before, after: 'WITHDRAWN' } } })
    recordAuto(quote.dealId, `견적을 회수했습니다 — ${quote.quoteNo}`, member.id)
    return HttpResponse.json(toDetail(quote))
  }),

  // 복제 (QT-19, Q-18·25) — 모든 상태에서 · 새 DRAFT · 종결 Deal 불가
  http.post(`${BASE}/:id/clone`, ({ params, request }) => {
    const member = currentMember(request)
    const quote = visibleQuote(request, String(params.id))
    if (!quote) return notFound()
    const deal = findDeal(quote.dealId)!
    if (!isOpenStage(deal.stage)) return error('QUOTE_DEAL_CLOSED')
    // 원본 기간을 물려받지 않는다 — QT-31("유효기간 연장은 v1에서 복제로 우회")이 성립하려면
    // 항상 새로 정해야 한다. 서버도 오늘 + 30일이다 (QuoteService.DEFAULT_VALIDITY_DAYS).
    const validUntil = new Date(Date.now() + 30 * 86_400_000).toISOString().slice(0, 10)
    const created: QuoteRow = {
      ...quote, id: crypto.randomUUID(), quoteNo: nextDocNo('QUOTE'), status: 'DRAFT', validUntil,
      clonedFromQuoteId: quote.id, supersededByQuoteId: null, rejectReason: null, responderName: null, responderTitle: null,
      sentAt: null, firstViewedAt: null, respondedAt: null, version: 0, createdAt: now(),
    }
    db.quotes.unshift(created)
    db.quoteItems.set(created.id, itemsOf(quote.id).map((it) => ({ ...it, id: crypto.randomUUID() })))
    // 반려·회수된 원본의 대체 견적 링크 (QT-28)
    if ((quote.status === 'REJECTED' || quote.status === 'WITHDRAWN') && !quote.supersededByQuoteId) quote.supersededByQuoteId = created.id
    recordAuto(deal.id, `견적을 복제했습니다 — ${quote.quoteNo} → ${created.quoteNo}`, member.id)
    return HttpResponse.json(toDetail(created), { status: 201 })
  }),

  // 수신인 변경 재발송 (AP-13) — 판정 축은 견적 상태 (QUOTE_NOT_RESENDABLE, v1.6.7). 견적은 그대로라 204 (서버 resendViewToken)
  http.post(`${BASE}/:id/view-token/resend`, async ({ params, request }) => {
    const member = currentMember(request)
    const quote = visibleQuote(request, String(params.id))
    if (!quote) return notFound()
    if (quote.status !== 'SENT' && quote.status !== 'VIEWED') return error('QUOTE_NOT_RESENDABLE')
    if (quote.validUntil < today()) return error('QUOTE_VALID_UNTIL_PASSED')
    const body = (await request.json()) as ResendViewTokenRequest
    if (!body.recipientContactId) return error('VALIDATION_FAILED', [{ field: 'recipientContactId', reason: '수신인을 선택해 주세요.' }])
    const deal = findDeal(quote.dealId)!
    if (!contactsOf(deal.customerId).some((c) => c.id === body.recipientContactId)) return error('CONTACT_NOT_IN_CUSTOMER')
    const current = activeTokenOf(quote.id)
    if (current) {
      current.status = 'EXPIRED'
      current.expiredReason = 'RESENT'
    }
    issueToken(quote, body.recipientContactId)
    recordAuto(deal.id, `견적을 다른 수신인에게 재발송했습니다 — ${quote.quoteNo}`, member.id)
    return noContent()
  }),

  // 열람 링크 수동 만료 (AP-14) — 견적 상태는 그대로, 링크만 닫는다. 멱등 — 활성 링크가 없어도 204 (서버 expireViewToken)
  http.post(`${BASE}/:id/view-token/expire`, ({ params, request }) => {
    const member = currentMember(request)
    const quote = visibleQuote(request, String(params.id))
    if (!quote) return notFound()
    const token = activeTokenOf(quote.id)
    if (token) {
      token.status = 'EXPIRED'
      token.expiredReason = 'MANUAL'
      recordAuto(quote.dealId, `열람 링크를 수동 만료했습니다 — ${quote.quoteNo}`, member.id)
    }
    return noContent()
  }),

  // 주문 전환 (OD-01~07) — 승인 견적만 · 1회만 · Deal은 단계 무관 성사 (멱등)
  http.post(`${BASE}/:id/convert-to-order`, ({ params, request }) => {
    const member = currentMember(request)
    const quote = visibleQuote(request, String(params.id))
    if (!quote) return notFound()
    if (quote.status !== 'APPROVED') return error('QUOTE_NOT_APPROVED')
    if (db.orders.some((o) => o.quoteId === quote.id)) return error('QUOTE_ALREADY_CONVERTED')
    const deal = findDeal(quote.dealId)!
    const order = {
      id: crypto.randomUUID(), orderNo: nextDocNo('ORDER'), quoteId: quote.id, quoteNo: quote.quoteNo,
      dealId: deal.id, dealTitle: deal.title, customerId: deal.customerId, customerName: deal.customerName,
      supplyAmount: quote.supplyAmount, vatAmount: quote.vatAmount, totalAmount: quote.totalAmount,
      startDate: null as string | null, deliveryDate: null as string | null, createdAt: now(),
    }
    db.orders.unshift(order)
    // 스냅샷 — FK 없는 값 복사 (OD-04)
    db.orderItems.set(order.id, itemsOf(quote.id).map(({ name, unit, quantity, unitPrice, amount }) => ({ name, unit, quantity, unitPrice, amount })))
    if (isOpenStage(deal.stage)) moveDealStage(deal.id, 'WON', null)
    deal.wonAmount = db.orders.filter((o) => o.dealId === deal.id).reduce((acc, o) => acc + o.totalAmount, 0) // DL-18
    recordAudit({ entityType: 'ORDER', entityId: order.id, eventType: 'ORDER_CREATED', actorType: 'MEMBER', actorId: member.id, changes: { orderNo: { before: null, after: order.orderNo } } })
    recordAuto(deal.id, `주문으로 전환했습니다 — ${order.orderNo}`, member.id)
    const body: OrderDetailResponse = { ...order, dealStage: deal.stage, items: db.orderItems.get(order.id) ?? [] }
    return HttpResponse.json(body, { status: 201 })
  }),
]
