import { afterEach, describe, expect, it, vi } from 'vitest'
import type { RequestHandler } from 'msw'
import { quoteHandlers } from './quote'
import { publicQuoteHandlers } from './publicQuote'
import { activeTokenOf, db, session } from '../store'
import { demoAccounts, quoteId } from '../fixtures'
import type { ErrorResponse, PageResponse, QuoteDetailResponse, QuoteResponse } from '../../shared/api/types'

/**
 * 견적 목이 백엔드(QuoteService #101·#154 · CustomerQuoteService #156)와 같은 규칙으로 답하는지.
 *
 * 실 API로 바꾸는 순간 화면이 갈라지는 지점만 고정한다: 목록 기본 정렬(createdAt DESC) ·
 * 영업 담당자는 담당 Deal의 견적만(SC-02 — 남의 견적 상세는 404, 남의 Deal 필터는 0건) ·
 * 재발송·링크 만료는 204(견적 상태 불변, 만료는 멱등) · 고객 열람은 `publicQuote` 키가 답한다 ·
 * 고객 응답의 판정 순서와 길이 제한(이름 50 · 사유 500 · 문의 1000) · 정지 회사는 문의도 409.
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
const get = (path: string, account: (typeof demoAccounts)[number]) =>
  call(quoteHandlers, new Request(`http://localhost/api/v1${path}`, { headers: auth(account) }))
const post = (path: string, account: (typeof demoAccounts)[number], body?: unknown) =>
  call(quoteHandlers, new Request(`http://localhost/api/v1${path}`, {
    method: 'POST', headers: { ...auth(account), 'Content-Type': 'application/json' }, body: body === undefined ? null : JSON.stringify(body),
  }))
const publicPost = (path: string, body: unknown) =>
  call(publicQuoteHandlers, new Request(`http://localhost/public/api/v1${path}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  }))

const errorOf = async (res: Response | null) => (await res!.json()) as ErrorResponse

describe('GET /api/v1/quotes — 목록 (QT-20)', () => {
  it('기본 정렬은 createdAt DESC — 서버 QuoteController.DEFAULT_SORT와 같다', async () => {
    session.login(admin)
    const res = (await get('/quotes?size=100', admin))!
    expect(res.status).toBe(200)
    const page: PageResponse<QuoteResponse> = await res.json()
    const ids = page.content.map((q) => q.id)
    const dates = db.quotes.filter((q) => ids.includes(q.id)).sort((a, b) => ids.indexOf(a.id) - ids.indexOf(b.id)).map((q) => q.createdAt)
    expect(dates).toEqual([...dates].sort().reverse())
  })

  it('영업 담당자는 담당 Deal의 견적만 — 남의 Deal 필터는 0건, 남의 견적 상세는 404 (SC-02·09)', async () => {
    session.login(salesRep)
    const otherDeal = db.deals.find((d) => d.assigneeMemberId !== salesRep.memberId && !d.deleted && db.quotes.some((q) => q.dealId === d.id))!
    const otherQuote = db.quotes.find((q) => q.dealId === otherDeal.id)!
    const list = (await get(`/quotes?dealId=${otherDeal.id}&size=100`, salesRep))!
    expect(((await list.json()) as PageResponse<QuoteResponse>).totalElements).toBe(0)
    expect((await get(`/quotes/${otherQuote.id}`, salesRep))!.status).toBe(404)
    session.login(admin)
    expect((await get(`/quotes/${otherQuote.id}`, admin))!.status).toBe(200)
  })
})

describe('GET /api/v1/quotes/{id} — 대체 견적 supersededByQuoteId (QT-28)', () => {
  /**
   * 서버(QuoteService.supersededBy, #326·#340)와 같은 규칙: 반려·회수된 원본 → DRAFT가 아닌 복제본 중 sentAt 최신.
   * 목이 "복제 즉시·먼저 만든 것"으로 잡으면 목에서는 링크가 뜨는데 실 API에서는 발송해야 뜬다 — 그 갈림만 고정한다.
   */
  const detail = async (id: string) => (await (await get(`/quotes/${id}`, admin))!.json()) as QuoteDetailResponse

  it('시드는 7(반려)→16·9(회수)→14 — 저장된 값이 아니라 clonedFrom의 역방향으로 나온다', async () => {
    session.login(admin)
    expect((await detail(quoteId(7))).supersededByQuoteId).toBe(quoteId(16))
    expect((await detail(quoteId(9))).supersededByQuoteId).toBe(quoteId(14))
    expect((await detail(quoteId(16))).supersededByQuoteId).toBeNull()   // 진행 중인 견적은 대체될 이유가 없다
  })

  it('복제 직후의 DRAFT는 대체가 아니고, 발송되면 가장 최근에 발송된 것이 대체다', async () => {
    session.login(admin)
    const original = db.quotes.find((q) => q.id === quoteId(7))!
    const cloned = (await (await post(`/quotes/${original.id}/clone`, admin))!.json()) as QuoteDetailResponse
    expect((await detail(original.id)).supersededByQuoteId).toBe(quoteId(16))   // 아직 안 보냈다 — 16 그대로

    const deal = db.deals.find((d) => d.id === original.dealId)!
    const recipient = (db.contacts.get(deal.customerId) ?? [])[0]!.id
    expect((await post(`/quotes/${cloned.id}/send`, admin, { recipientContactId: recipient }))!.status).toBe(200)
    expect((await detail(original.id)).supersededByQuoteId).toBe(cloned.id)   // 16(08-26)보다 늦게 발송됐다
  })
})

describe('POST /api/v1/quotes/{id}/view-token/* — 링크 재발송·수동 만료 (AP-13·14)', () => {
  it('재발송은 204 — 견적 상태·version은 그대로, 기존 링크는 RESENT로 닫히고 새 링크가 열린다', async () => {
    session.login(admin)
    const token = db.viewTokens.find((t) => t.rawToken === 'demo-taesung-10')!
    const quote = db.quotes.find((q) => q.id === token.quoteId)!
    const deal = db.deals.find((d) => d.id === quote.dealId)!
    const recipient = (db.contacts.get(deal.customerId) ?? []).find((c) => c.id !== token.recipientContactId)?.id ?? token.recipientContactId
    const before = { status: quote.status, version: quote.version }
    const res = (await post(`/quotes/${quote.id}/view-token/resend`, admin, { recipientContactId: recipient }))!
    expect(res.status).toBe(204)
    expect({ status: quote.status, version: quote.version }).toEqual(before)
    expect(token.status).toBe('EXPIRED')
    expect(token.expiredReason).toBe('RESENT')
    expect(activeTokenOf(quote.id)).toBeDefined()
  })

  it('수동 만료는 204이고 멱등 — 두 번 눌러도 성공, 견적 상태는 그대로 (서버 expireViewToken)', async () => {
    session.login(admin)
    const token = db.viewTokens.find((t) => t.rawToken === 'demo-sungwon-11')!
    const quote = db.quotes.find((q) => q.id === token.quoteId)!
    const status = quote.status
    expect((await post(`/quotes/${quote.id}/view-token/expire`, admin))!.status).toBe(204)
    expect(token.status).toBe('EXPIRED')
    expect(token.expiredReason).toBe('MANUAL')
    expect((await post(`/quotes/${quote.id}/view-token/expire`, admin))!.status).toBe(204)
    expect(quote.status).toBe(status)
  })
})

describe('/public/api/v1/quotes/{token} — 고객 열람·응답 (AP-02·08~11·15)', () => {
  const company = () => db.companies[0]
  const tokenOf = (raw: string) => db.viewTokens.find((t) => t.rawToken === raw)!
  // 픽스처 만료일은 시드와 같은 고정 날짜라 시간이 지나면 410이 된다 — 테스트는 만료일을 미래로 두고 돌린다
  const FUTURE = '2999-12-31T14:59:59Z'
  const expiresAt = new Map<string, string>()
  const keepAlive = (raw: string) => {
    const token = tokenOf(raw)
    if (!expiresAt.has(raw)) expiresAt.set(raw, token.expiresAt)
    token.expiresAt = FUTURE
    return raw
  }
  afterEach(() => {
    company().status = 'ACTIVE'
    for (const [raw, original] of expiresAt) tokenOf(raw).expiresAt = original
    expiresAt.clear()
  })

  it('quote 핸들러는 답하지 않고 publicQuote 핸들러가 답한다 — 견적과 고객 링크는 따로 켜고 끈다', async () => {
    const request = () => new Request('http://localhost/public/api/v1/quotes/demo-token')
    expect(await call(quoteHandlers, request())).toBeNull()
    expect(await call(publicQuoteHandlers, request())).not.toBeNull()
  })

  it('길이 제한은 서버 DTO와 같다 — 사유 501자·이름 51자는 400 VALIDATION_FAILED, 링크 판정보다 먼저', async () => {
    const res = await publicPost('/quotes/no-such-link/reject', { reason: 'x'.repeat(501), responderName: 'y'.repeat(51) })
    expect(res!.status).toBe(400)
    const body = await errorOf(res)
    expect(body.code).toBe('VALIDATION_FAILED')
    expect(body.fieldErrors.map((f) => f.field).sort()).toEqual(['reason', 'responderName'])
    const inquiry = await publicPost('/quotes/no-such-link/inquiries', { content: 'x'.repeat(1001) })
    expect(inquiry!.status).toBe(400)
  })

  it('응답 완료 링크는 열람 200(respondable=false)·승인 409 LINK_ALREADY_RESPONDED·문의 204 (AP-11 · 07 §D)', async () => {
    const responded = `/quotes/${keepAlive('demo-shinyoung-01')}`
    const view = await call(publicQuoteHandlers, new Request(`http://localhost/public/api/v1${responded}`))
    expect(view!.status).toBe(200)
    expect(((await view!.json()) as { respondable: boolean }).respondable).toBe(false)
    expect((await errorOf(await publicPost(`${responded}/approve`, { responderName: '김서연' }))).code).toBe('LINK_ALREADY_RESPONDED')
    expect((await publicPost(`${responded}/inquiries`, { content: '납기 문의' }))!.status).toBe(204)
  })

  it('유효기간이 지난 링크는 status가 ACTIVE·RESPONDED여도 410 — 열람·응답·문의 전부 (QuoteViewToken.isViewable)', async () => {
    const token = tokenOf('demo-hanul-16')
    const original = token.expiresAt
    token.expiresAt = '2000-01-01T00:00:00Z'
    try {
      const view = await call(publicQuoteHandlers, new Request('http://localhost/public/api/v1/quotes/demo-hanul-16'))
      expect(view!.status).toBe(410)
      expect((await errorOf(await publicPost('/quotes/demo-hanul-16/approve', { responderName: '서동윤' }))).code).toBe('LINK_EXPIRED')
      expect((await errorOf(await publicPost('/quotes/demo-hanul-16/inquiries', { content: '문의' }))).code).toBe('LINK_EXPIRED')
    } finally {
      token.expiresAt = original
    }
  })

  it('정지 회사는 승인·반려·문의 전부 409 COMPANY_SUSPENDED — 열람 응답의 respondable도 false', async () => {
    company().status = 'SUSPENDED'
    const active = `/quotes/${keepAlive('demo-dodam-14')}`
    expect((await errorOf(await publicPost(`${active}/approve`, { responderName: '이수정' }))).code).toBe('COMPANY_SUSPENDED')
    expect((await errorOf(await publicPost(`${active}/reject`, { reason: '예산 초과', responderName: '이수정' }))).code).toBe('COMPANY_SUSPENDED')
    expect((await errorOf(await publicPost(`${active}/inquiries`, { content: '문의' }))).code).toBe('COMPANY_SUSPENDED')
    const view = await call(publicQuoteHandlers, new Request(`http://localhost/public/api/v1${active}`))
    expect(((await view!.json()) as { respondable: boolean }).respondable).toBe(false)
  })
})
