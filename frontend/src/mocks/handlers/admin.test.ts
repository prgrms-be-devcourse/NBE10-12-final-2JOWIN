import { describe, expect, it, vi } from 'vitest'
import type { RequestHandler } from 'msw'
import { adminHandlers } from './admin'
import { invitationHandlers } from './auth'
import { session } from '../store'
import { demoAccounts, demoPlatformAdmin } from '../fixtures'
import type { ApplicationResponse, LoginResponse } from '../../shared/api/types'

/**
 * 플랫폼 관리자 목이 백엔드(AdminAuthController · AdminApplicationController, #108)와 같은 규칙으로 답하는지.
 *
 * 실 API로 바꾸는 순간 화면이 갈라지는 지점만 고정한다: 로그인 응답의 role은 `PLATFORM_ADMIN`이고 name은 이메일
 * (platform_admin 테이블에 이름 컬럼이 없다) · 구성원 토큰으로 관리자 API를 부르면 401 · 이미 처리한 신청의 반려는 409.
 * 목록 정렬(신청 createdAt ASC · 회사 name ASC)은 onboarding.test.ts가 본다.
 */

vi.stubGlobal('location', { href: 'http://localhost/', origin: 'http://localhost' })

async function call(handlers: RequestHandler[], request: Request): Promise<Response> {
  for (const handler of handlers) {
    const result = await handler.run({ request, requestId: crypto.randomUUID() })
    if (result?.response) return result.response
  }
  throw new Error(`no handler for ${request.method} ${request.url}`)
}

const json = { 'Content-Type': 'application/json' }
const adminAuth = { Authorization: `Bearer ${demoPlatformAdmin.accessToken}` }

const adminLogin = () =>
  call(adminHandlers, new Request('http://localhost/admin/api/v1/auth/login', {
    method: 'POST', headers: json, body: JSON.stringify({ email: demoPlatformAdmin.email, password: demoPlatformAdmin.password, rememberMe: false }),
  }))

describe('POST /admin/api/v1/auth/login (AU-08)', () => {
  it('role은 PLATFORM_ADMIN, name은 이메일 — AdminAuthService가 그렇게 만든다', async () => {
    const res = await adminLogin()
    expect(res.status).toBe(200)
    const body: LoginResponse = await res.json()
    expect(body.role).toBe('PLATFORM_ADMIN')
    expect(body.name).toBe(demoPlatformAdmin.email)
    expect(body.companyName).toBeNull()
  })
})

describe('관리자 API 보호 (AU-08)', () => {
  it('구성원 토큰으로 부르면 401 — 관리자 세션과 구성원 세션은 별개다', async () => {
    await adminLogin()
    const res = await call(adminHandlers, new Request('http://localhost/admin/api/v1/companies', {
      headers: { Authorization: `Bearer ${demoAccounts[0].accessToken}` },
    }))
    expect(res.status).toBe(401)
  })
})

describe('POST /admin/api/v1/applications/{id}/reject (ON-14)', () => {
  it('처리된 신청을 다시 반려하면 409 APPLICATION_ALREADY_DECIDED', async () => {
    await adminLogin()
    session.adminLogin()
    const applied = await call(invitationHandlers, new Request('http://localhost/public/api/v1/applications', {
      method: 'POST', headers: json,
      body: JSON.stringify({ companyName: 'e2e-반려', businessNo: '999-99-99999', applicantName: 'e2e', email: 'e2e-reject@example.test' }),
    }))
    expect(applied.status).toBe(201)
    const application: ApplicationResponse = await applied.json()
    const reject = () => call(adminHandlers, new Request(`http://localhost/admin/api/v1/applications/${application.id}/reject`, {
      method: 'POST', headers: { ...adminAuth, ...json }, body: JSON.stringify({ reason: 'e2e' }),
    }))
    expect((await reject()).status).toBe(200)
    const again = await reject()
    expect(again.status).toBe(409)
    expect((await again.json()).code).toBe('APPLICATION_ALREADY_DECIDED')
  })
})
