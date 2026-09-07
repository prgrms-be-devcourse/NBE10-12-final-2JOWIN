import { delay, http, HttpResponse } from 'msw'
import { demoPlatformAdmin } from '../fixtures'
import { db, error, noContent, notFound, now, paged, recordAudit, session } from '../store'
import type { CompanyResponse, LoginRequest, LoginResponse, RefreshTokenResponse, RejectApplicationRequest, SuspendCompanyRequest } from '../../shared/api/types'

/**
 * 플랫폼 관리자 목 (`/admin/api/v1`) — 07 §A 온보딩 · 08 §A · 전이표 §1·§2.
 *
 * 세션은 구성원과 별개다 (AU-08): 로그인 admin@2jo.io / test1234!, Bearer는 `demoPlatformAdmin.accessToken`.
 * 다른 토큰·미로그인은 전부 401 REFRESH_TOKEN_NOT_ACTIVE (필터 401 겸용, 07 v1.6.5 각주).
 * 실패 경로: LOGIN_FAILED · LOGIN_LOCKED · APPLICATION_ALREADY_DECIDED · COMPANY_BUSINESS_NO_DUPLICATED · 400 · 404.
 */

const BASE = '/admin/api/v1'
const LOCK_THRESHOLD = 5
let failures = 0

/** 관리자 세션 검사 — 통과하면 null */
const guard = (request: Request) => {
  const token = request.headers.get('Authorization')?.replace(/^Bearer\s+/i, '')
  return session.isAdminLoggedIn() && token === demoPlatformAdmin.accessToken ? null : error('REFRESH_TOKEN_NOT_ACTIVE')
}

const findApplication = (id: string) => db.applications.find((a) => a.id === id)
const findCompany = (id: string) => db.companies.find((c) => c.id === id)

export const adminHandlers = [
  // ── 세션 (AU-08)
  http.post(`${BASE}/auth/login`, async ({ request }) => {
    const { email, password } = (await request.json()) as LoginRequest
    if (failures >= LOCK_THRESHOLD) return error('LOGIN_LOCKED')
    if (email !== demoPlatformAdmin.email || password !== demoPlatformAdmin.password) {
      failures += 1
      return error('LOGIN_FAILED')
    }
    failures = 0
    session.adminLogin()
    // role은 08 §A LoginResponse 형태를 맞추기 위한 값일 뿐 — 플랫폼 관리자는 Role enum 밖이다 (09 v1.6.3 각주)
    const body: LoginResponse = { accessToken: demoPlatformAdmin.accessToken, memberId: demoPlatformAdmin.id, name: demoPlatformAdmin.name, role: 'COMPANY_ADMIN', companyName: null }
    return HttpResponse.json(body)
  }),

  http.post(`${BASE}/auth/refresh`, () => {
    if (!session.isAdminLoggedIn()) return error('REFRESH_TOKEN_NOT_ACTIVE')
    const body: RefreshTokenResponse = { accessToken: demoPlatformAdmin.accessToken }
    return HttpResponse.json(body)
  }),

  http.post(`${BASE}/auth/logout`, () => {
    session.adminLogout()
    return noContent()
  }),

  // ── 가입 신청 (ON-03~07·14)
  http.get(`${BASE}/applications`, async ({ request }) => {
    const denied = guard(request)
    if (denied) return denied
    await delay(120)
    const url = new URL(request.url)
    const status = url.searchParams.get('status')
    // 심사 대기열이라 오래된 것이 위다 — 서버 기본 정렬 createdAt ASC (AdminApplicationController, #108)
    const list = db.applications
      .filter((a) => !status || a.status === status)
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt))
    return HttpResponse.json(paged(list, url))
  }),

  http.get(`${BASE}/applications/:id`, async ({ params, request }) => {
    const denied = guard(request)
    if (denied) return denied
    await delay(100)
    const application = findApplication(String(params.id))
    return application ? HttpResponse.json(application) : notFound()
  }),

  // 승인 → 회사 생성 + 기업 관리자 계정(비밀번호 미설정) + 설정 링크 메일 (전이표 §1). 사업자번호 전역 UNIQUE
  http.post(`${BASE}/applications/:id/approve`, ({ params, request }) => {
    const denied = guard(request)
    if (denied) return denied
    const application = findApplication(String(params.id))
    if (!application) return notFound()
    if (application.status !== 'PENDING') return error('APPLICATION_ALREADY_DECIDED')
    if (db.companies.some((c) => c.businessNo === application.businessNo)) return error('COMPANY_BUSINESS_NO_DUPLICATED')
    application.status = 'APPROVED'
    application.decidedAt = now()
    const company: CompanyResponse = {
      id: crypto.randomUUID(), name: application.companyName, businessNo: application.businessNo,
      status: 'ACTIVE', suspendReason: null, memberCount: 1, createdAt: now(),
    }
    db.companies.push(company)
    recordAudit({ entityType: 'APPLICATION', entityId: application.id, eventType: 'APPROVED', actorType: 'PLATFORM_ADMIN', actorId: demoPlatformAdmin.id, changes: { status: { before: 'PENDING', after: 'APPROVED' } } })
    return HttpResponse.json(application)
  }),

  // 반려 — 사유 필수 (ON-14), 이력 보존·재신청 가능 (Q-15)
  http.post(`${BASE}/applications/:id/reject`, async ({ params, request }) => {
    const denied = guard(request)
    if (denied) return denied
    const application = findApplication(String(params.id))
    if (!application) return notFound()
    const body = (await request.json()) as RejectApplicationRequest
    if (!body.reason?.trim()) return error('VALIDATION_FAILED', [{ field: 'reason', reason: '반려 사유를 입력해 주세요.' }])
    if (application.status !== 'PENDING') return error('APPLICATION_ALREADY_DECIDED')
    application.status = 'REJECTED'
    application.rejectReason = body.reason.trim()
    application.decidedAt = now()
    recordAudit({ entityType: 'APPLICATION', entityId: application.id, eventType: 'REJECTED', actorType: 'PLATFORM_ADMIN', actorId: demoPlatformAdmin.id, changes: { status: { before: 'PENDING', after: 'REJECTED' } } })
    return HttpResponse.json(application)
  }),

  // ── 회사 (ON-08~10·12)
  http.get(`${BASE}/companies`, async ({ request }) => {
    const denied = guard(request)
    if (denied) return denied
    await delay(120)
    const url = new URL(request.url)
    // 찾는 대상이라 이름순 — 서버 기본 정렬 name ASC (AdminCompanyController, #108)
    const list = db.companies.slice().sort((a, b) => a.name.localeCompare(b.name, 'ko'))
    return HttpResponse.json(paged(list, url))
  }),

  // 정지 — 구성원 차단·refresh 폐기·고객 링크 열람만·배치 알림 중단 (publicQuote.ts가 companies[0].status를 읽는다)
  http.post(`${BASE}/companies/:id/suspend`, async ({ params, request }) => {
    const denied = guard(request)
    if (denied) return denied
    const company = findCompany(String(params.id))
    if (!company) return notFound()
    const body = (await request.json()) as SuspendCompanyRequest
    if (!body.reason?.trim()) return error('VALIDATION_FAILED', [{ field: 'reason', reason: '정지 사유를 입력해 주세요.' }])
    company.status = 'SUSPENDED'
    company.suspendReason = body.reason.trim()
    recordAudit({ entityType: 'COMPANY', entityId: company.id, eventType: 'SUSPENDED', actorType: 'PLATFORM_ADMIN', actorId: demoPlatformAdmin.id, changes: { status: { before: 'ACTIVE', after: 'SUSPENDED' } } })
    return HttpResponse.json(company)
  }),

  http.post(`${BASE}/companies/:id/reactivate`, ({ params, request }) => {
    const denied = guard(request)
    if (denied) return denied
    const company = findCompany(String(params.id))
    if (!company) return notFound()
    company.status = 'ACTIVE'
    company.suspendReason = null
    recordAudit({ entityType: 'COMPANY', entityId: company.id, eventType: 'REACTIVATED', actorType: 'PLATFORM_ADMIN', actorId: demoPlatformAdmin.id, changes: { status: { before: 'SUSPENDED', after: 'ACTIVE' } } })
    return HttpResponse.json(company)
  }),
]
