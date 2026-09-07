import { describe, expect, it, vi } from 'vitest'
import type { RequestHandler } from 'msw'
import { invitationHandlers } from './auth'
import { adminHandlers } from './admin'
import { db, session } from '../store'
import { demoPlatformAdmin } from '../fixtures'
import type { ApplicationResponse, CompanyResponse, PageResponse } from '../../shared/api/types'

/**
 * 온보딩 목이 백엔드(PR #108 · 08 v1.6.11)와 같은 규칙으로 답하는지 — #110.
 *
 * 실 API로 바꾸는 순간 화면이 갈라지는 지점만 고정한다: 신청자 이름 필수 · 이메일 소문자 정규화와
 * 관문 순서(구성원 → 대기 신청, Q-14) · 목록 기본 정렬(신청 createdAt ASC · 회사 name ASC).
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

const apply = (body: Record<string, string>) =>
  // 접수 핸들러는 public 경로 묶음(invitationHandlers)에 있다
  call(invitationHandlers, new Request('http://localhost/public/api/v1/applications', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  }))

const adminGet = (path: string) =>
  call(adminHandlers, new Request(`http://localhost/admin/api/v1${path}`, {
    headers: { Authorization: `Bearer ${demoPlatformAdmin.accessToken}` },
  }))

const 신청 = { companyName: '도담테크', businessNo: '456-78-90123', applicantName: '박지훈', email: 'jihun@dodam.co.kr' }

describe('POST /public/api/v1/applications — 접수 (ON-01 · 08 v1.6.11)', () => {
  it('신청자 이름이 비면 400 VALIDATION_FAILED — 필드는 applicantName', async () => {
    const res = await apply({ ...신청, applicantName: '  ' })
    expect(res.status).toBe(400)
    const body = await res.json()
    expect(body.code).toBe('VALIDATION_FAILED')
    expect(body.fieldErrors.map((f: { field: string }) => f.field)).toContain('applicantName')
  })

  it('접수되면 201 — 신청자 이름이 실리고 이메일은 소문자로 저장된다 (ApplicationService.normalize)', async () => {
    const res = await apply({ ...신청, email: 'Jihun@Dodam.co.kr', applicantName: ' 박지훈 ' })
    expect(res.status).toBe(201)
    const created: ApplicationResponse = await res.json()
    expect(created.status).toBe('PENDING')
    expect(created.applicantName).toBe('박지훈')
    expect(created.email).toBe('jihun@dodam.co.kr')
    expect(db.applications.find((a) => a.id === created.id)?.applicantName).toBe('박지훈')
  })

  it('같은 이메일의 대기 신청이 있으면 표기가 달라도 409 APPLICATION_ALREADY_PENDING', async () => {
    const res = await apply({ ...신청, email: 'JIHUN@DODAM.CO.KR' })
    expect(res.status).toBe(409)
    expect((await res.json()).code).toBe('APPLICATION_ALREADY_PENDING')
  })

  it('이미 구성원인 이메일은 422 EMAIL_ALREADY_MEMBER — 대기 신청보다 먼저 본다 (Q-14)', async () => {
    const member = db.members[0]
    db.applications.push({ ...신청, id: crypto.randomUUID(), email: member.email, status: 'PENDING', rejectReason: null, decidedAt: null, createdAt: new Date().toISOString() })
    const res = await apply({ ...신청, email: member.email.toUpperCase() })
    expect(res.status).toBe(422)
    expect((await res.json()).code).toBe('EMAIL_ALREADY_MEMBER')
  })
})

describe('관리자 목록 — 서버 기본 정렬을 그대로 보여준다 (#108 컨트롤러)', () => {
  it('신청 목록은 createdAt ASC — 먼저 온 신청이 위 (대기열)', async () => {
    session.adminLogin()
    const res = await adminGet('/applications?size=100')
    expect(res.status).toBe(200)
    const page: PageResponse<ApplicationResponse> = await res.json()
    const dates = page.content.map((a) => a.createdAt)
    expect(dates).toEqual([...dates].sort())
    expect(page.content.every((a) => typeof a.applicantName === 'string' && a.applicantName !== '')).toBe(true)
  })

  it('회사 목록은 name ASC', async () => {
    session.adminLogin()
    db.companies.push({ id: crypto.randomUUID(), name: '가나상사', businessNo: '111-11-11111', status: 'ACTIVE', suspendReason: null, memberCount: 0, createdAt: new Date().toISOString() })
    const res = await adminGet('/companies?size=100')
    const page: PageResponse<CompanyResponse> = await res.json()
    const names = page.content.map((c) => c.name)
    expect(names).toEqual([...names].sort((a, b) => a.localeCompare(b, 'ko')))
    expect(names[0]).toBe('가나상사')
  })
})
