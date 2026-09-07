import { describe, expect, it, vi } from 'vitest'
import type { RequestHandler } from 'msw'
import { memberHandlers } from './member'
import { db, session } from '../store'
import { demoAccounts } from '../fixtures'
import type { MemberOptionResponse, MemberResponse, PageResponse } from '../../shared/api/types'

/**
 * 구성원 목이 백엔드(#79 · MemberController)와 같은 규칙으로 답하는지.
 *
 * 실 API로 바꾸는 순간 화면이 갈라지는 지점만 고정한다: 목록 기본 정렬(name ASC) ·
 * 담당자 선택지는 활성 구성원만 · 영업 담당자의 목록 조회는 403.
 */

// 핸들러 경로가 상대 경로라 msw가 location.href 기준으로 절대 URL을 만든다 — Node에는 location이 없다
vi.stubGlobal('location', { href: 'http://localhost/', origin: 'http://localhost' })

async function call(handlers: RequestHandler[], request: Request): Promise<Response> {
  for (const handler of handlers) {
    const result = await handler.run({ request, requestId: crypto.randomUUID() })
    if (result?.response) return result.response
  }
  throw new Error(`no handler for ${request.method} ${request.url}`)
}

const get = (path: string, account: (typeof demoAccounts)[number]) =>
  call(memberHandlers, new Request(`http://localhost/api/v1${path}`, {
    headers: { Authorization: `Bearer ${account.accessToken}` },
  }))

const [admin, salesRep] = demoAccounts

describe('GET /api/v1/members — 목록 (MB-07)', () => {
  it('기본 정렬은 name ASC — 서버 MemberController.DEFAULT_SORT와 같다', async () => {
    session.login(admin)
    const res = await get('/members?size=100', admin)
    expect(res.status).toBe(200)
    const page: PageResponse<MemberResponse> = await res.json()
    const names = page.content.map((m) => m.name)
    expect(names).toEqual([...names].sort((a, b) => a.localeCompare(b, 'ko')))
  })

  it('영업 담당자는 403 FORBIDDEN — 기업 관리자 전용', async () => {
    session.login(salesRep)
    const res = await get('/members', salesRep)
    expect(res.status).toBe(403)
    expect((await res.json()).code).toBe('FORBIDDEN')
  })
})

describe('GET /api/v1/members/options — 담당자 선택지 (DL-04)', () => {
  it('활성 구성원만, 이름·id만 — 영업 담당자도 조회할 수 있다', async () => {
    session.login(salesRep)
    const inactive = db.members.find((m) => m.status !== 'ACTIVE')
    const res = await get('/members/options', salesRep)
    expect(res.status).toBe(200)
    const options: MemberOptionResponse[] = await res.json()
    expect(options.length).toBe(db.members.filter((m) => m.status === 'ACTIVE').length)
    if (inactive) expect(options.map((o) => o.id)).not.toContain(inactive.id)
    for (const option of options) expect(Object.keys(option).sort()).toEqual(['id', 'name'])
  })
})
