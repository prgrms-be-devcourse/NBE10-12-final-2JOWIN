import { describe, expect, it, vi } from 'vitest'
import type { RequestHandler } from 'msw'
import { quoteHandlers } from './quote'
import { publicQuoteHandlers } from './publicQuote'
import { db, session } from '../store'
import { demoAccounts } from '../fixtures'
import type { PageResponse, QuoteResponse } from '../../shared/api/types'

/**
 * 견적 목이 백엔드(QuoteController · QuoteService, #101)와 같은 규칙으로 답하는지.
 *
 * 실 API로 바꾸는 순간 화면이 갈라지는 지점만 고정한다: 목록 기본 정렬(createdAt DESC) ·
 * 영업 담당자는 담당 Deal의 견적만(SC-02 — 남의 견적 상세는 404, 남의 Deal 필터는 0건) ·
 * 고객 열람(/public/api/v1/quotes)은 `quote`가 아니라 `publicQuote` 키가 답한다.
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

describe('GET /public/api/v1/quotes/{token} — 고객 열람 (AP-02)', () => {
  it('quote 핸들러는 답하지 않고 publicQuote 핸들러가 답한다 — 견적을 실 API로 돌려도 고객 링크 화면은 목', async () => {
    const request = () => new Request('http://localhost/public/api/v1/quotes/demo-token')
    expect(await call(quoteHandlers, request())).toBeNull()
    const res = await call(publicQuoteHandlers, request())
    expect(res).not.toBeNull()
  })
})
