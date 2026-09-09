import { describe, expect, it, vi } from 'vitest'
import type { RequestHandler } from 'msw'
import { dashboardHandlers } from './dashboard'
import { session } from '../store'
import { demoAccounts } from '../fixtures'
import type { DashboardSummaryResponse, ErrorResponse } from '../../shared/api/types'

/**
 * 대시보드 목이 백엔드(DashboardController · DashboardService, #211)와 같은 규칙으로 답하는지.
 *
 * 실 API 전환(#129)에서 서버 실응답과 대조해 어긋난 자리만 고정한다:
 * 기간이 뒤집혔거나 366일 이상이면 `fieldErrors` 없이 400 · 후속 필요는 10건까지(FOLLOWUP_LIMIT).
 *
 * performance의 `to` 기본값도 월말 → **오늘**로 맞췄지만 응답으로는 구별되지 않아 테스트로 고정하지
 * 못했다 — 서버 실응답과 손으로 대조했다 (DashboardController).
 *
 * `monthWonAmount`·`members`·`conversions`는 서버가 아직 자리표시자라(#216) 값 자체를 맞추지
 * 않는다 — 화면이 그 셋을 "집계 준비 중"으로 가리는 것은 `domains/dashboard/pending.ts`가 판정한다.
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
const get = (path: string, account: (typeof demoAccounts)[number]) =>
  call(dashboardHandlers, new Request(url(path), { headers: auth(account) }))

describe('GET /api/v1/dashboard/summary (DB-01~05)', () => {
  it('후속 필요는 10건까지 — 서버 FOLLOWUP_LIMIT', async () => {
    session.login(admin)
    const res = (await get('/dashboard/summary', admin))!
    expect(res.status).toBe(200)
    const body: DashboardSummaryResponse = await res.json()
    expect(body.followUps.length).toBeLessThanOrEqual(10)
    expect(body.recentActivities.length).toBeLessThanOrEqual(10)
  })

  it('월 형식이 틀리면 400 VALIDATION_FAILED + month fieldError', async () => {
    session.login(admin)
    const res = (await get('/dashboard/summary?month=2026-9', admin))!
    expect(res.status).toBe(400)
    const body: ErrorResponse = await res.json()
    expect(body.code).toBe('VALIDATION_FAILED')
    expect(body.fieldErrors.map((f) => f.field)).toContain('month')
  })

  it('파이프라인은 진행 4단계가 항상 선다 — 건수 0인 단계도 0으로', async () => {
    session.login(admin)
    const res = (await get('/dashboard/summary', admin))!
    const body: DashboardSummaryResponse = await res.json()
    expect(body.pipeline.map((p) => p.stage)).toEqual(['LEAD', 'CONSULT', 'QUOTE', 'NEGOTIATION'])
  })
})

describe('GET /api/v1/dashboard/performance (DB-06~08)', () => {
  it('영업 담당자는 403 FORBIDDEN', async () => {
    session.login(salesRep)
    const res = (await get('/dashboard/performance', salesRep))!
    expect(res.status).toBe(403)
  })

  it('from > to이면 fieldErrors 없이 400 — 서버 DashboardService와 같다', async () => {
    session.login(admin)
    const res = (await get('/dashboard/performance?from=2026-09-30&to=2026-09-01', admin))!
    expect(res.status).toBe(400)
    const body: ErrorResponse = await res.json()
    expect(body.code).toBe('VALIDATION_FAILED')
    expect(body.fieldErrors).toEqual([])
  })

  it('기간이 366일 이상이면 400', async () => {
    session.login(admin)
    const res = (await get('/dashboard/performance?from=2025-01-01&to=2026-09-09', admin))!
    expect(res.status).toBe(400)
  })

  it('365일은 통과한다 — 경계는 366일', async () => {
    session.login(admin)
    const res = (await get('/dashboard/performance?from=2025-09-09&to=2026-09-09', admin))!
    expect(res.status).toBe(200)
  })
})
