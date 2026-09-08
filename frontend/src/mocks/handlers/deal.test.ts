import { describe, expect, it, vi } from 'vitest'
import type { RequestHandler } from 'msw'
import { dealHandlers } from './deal'
import { db, session } from '../store'
import { demoAccounts } from '../fixtures'
import type { DealResponse, PageResponse } from '../../shared/api/types'

/**
 * Deal 목이 백엔드(DealController · DealService · Deal 엔티티)와 같은 규칙으로 답하는지.
 *
 * 실 API로 바꾸는 순간 화면이 갈라지는 지점만 고정한다: 목록 기본 정렬(createdAt DESC) ·
 * 영업 담당자의 assigneeId는 본인으로 고정(SC-02, 남의 id를 넣어도 0건이 아니라 내 것) ·
 * 범위 밖 Deal은 404(SC-09) · 전이 오류 코드(07 §C — 리드 revert · 진행 중 reopen · 실패 advance).
 */

vi.stubGlobal('location', { href: 'http://localhost/', origin: 'http://localhost' })

async function call(handlers: RequestHandler[], request: Request): Promise<Response> {
  for (const handler of handlers) {
    const result = await handler.run({ request, requestId: crypto.randomUUID() })
    if (result?.response) return result.response
  }
  throw new Error(`no handler for ${request.method} ${request.url}`)
}

const [admin, salesRep] = demoAccounts
const auth = (account: (typeof demoAccounts)[number]) => ({ Authorization: `Bearer ${account.accessToken}` })

const get = (path: string, account: (typeof demoAccounts)[number]) =>
  call(dealHandlers, new Request(`http://localhost/api/v1${path}`, { headers: auth(account) }))

const post = (path: string, account: (typeof demoAccounts)[number], body: unknown) =>
  call(dealHandlers, new Request(`http://localhost/api/v1${path}`, {
    method: 'POST', headers: { ...auth(account), 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  }))

const list = async (path: string, account: (typeof demoAccounts)[number]) => {
  const res = await get(path, account)
  expect(res.status).toBe(200)
  return (await res.json()) as PageResponse<DealResponse>
}

describe('GET /api/v1/deals — 목록·보드 (DL-06·13·14)', () => {
  it('기본 정렬은 createdAt DESC — 서버 DealController.DEFAULT_SORT와 같다', async () => {
    session.login(admin)
    const page = await list('/deals?size=100', admin)
    const dates = page.content.map((d) => d.createdAt)
    expect(dates).toEqual([...dates].sort().reverse())
  })

  it('영업 담당자는 assigneeId를 무엇으로 보내든 본인 담당만 — 남의 id면 0건이 아니라 내 것 (SC-02)', async () => {
    session.login(salesRep)
    const me = db.members.find((m) => m.id === salesRep.memberId)!
    const other = db.members.find((m) => m.id !== me.id && m.status === 'ACTIVE')!
    const mine = await list('/deals?size=100', salesRep)
    const withOther = await list(`/deals?assigneeId=${other.id}&size=100`, salesRep)
    expect(withOther.totalElements).toBe(mine.totalElements)
    expect(withOther.content.every((d) => d.assigneeMemberId === me.id)).toBe(true)
  })

  it('기업 관리자는 assigneeId 필터가 그대로 먹는다', async () => {
    session.login(admin)
    const rep = db.members.find((m) => m.id === salesRep.memberId)!
    const page = await list(`/deals?assigneeId=${rep.id}&size=100`, admin)
    expect(page.content.length).toBeGreaterThan(0)
    expect(page.content.every((d) => d.assigneeMemberId === rep.id)).toBe(true)
  })

  it('영업 담당자가 남의 Deal 상세를 열면 404 (SC-09)', async () => {
    session.login(salesRep)
    const other = db.deals.find((d) => d.assigneeMemberId !== salesRep.memberId && !d.deleted)!
    expect((await get(`/deals/${other.id}`, salesRep)).status).toBe(404)
  })
})

describe('단계 전이 오류 코드 (07 §C)', () => {
  const version = (id: string) => db.deals.find((d) => d.id === id)!.version

  it('리드에서 revert → 409 DEAL_NO_PREVIOUS_STAGE', async () => {
    session.login(admin)
    const lead = db.deals.find((d) => d.stage === 'LEAD' && !d.deleted)!
    const res = await post(`/deals/${lead.id}/revert`, admin, { version: version(lead.id) })
    expect(res.status).toBe(409)
    expect((await res.json()).code).toBe('DEAL_NO_PREVIOUS_STAGE')
  })

  it('진행 중 Deal의 reopen → 409 DEAL_NOT_LOST', async () => {
    session.login(admin)
    const open = db.deals.find((d) => d.stage === 'CONSULT' && !d.deleted)!
    const res = await post(`/deals/${open.id}/reopen`, admin, { version: version(open.id) })
    expect(res.status).toBe(409)
    expect((await res.json()).code).toBe('DEAL_NOT_LOST')
  })

  it('실패(LOST) Deal의 advance → 409 DEAL_NOT_OPEN', async () => {
    session.login(admin)
    const lost = db.deals.find((d) => d.stage === 'LOST' && !d.deleted)!
    const res = await post(`/deals/${lost.id}/advance`, admin, { version: version(lost.id) })
    expect(res.status).toBe(409)
    expect((await res.json()).code).toBe('DEAL_NOT_OPEN')
  })

  it('낡은 version → 409 STALE_VERSION', async () => {
    session.login(admin)
    const open = db.deals.find((d) => d.stage === 'LEAD' && !d.deleted)!
    const res = await post(`/deals/${open.id}/advance`, admin, { version: version(open.id) + 5 })
    expect(res.status).toBe(409)
    expect((await res.json()).code).toBe('STALE_VERSION')
  })
})
