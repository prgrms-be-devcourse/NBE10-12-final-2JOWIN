import { http, HttpResponse } from 'msw'
import { demoAccounts } from '../fixtures'
import { currentMember, db, error, noContent, session } from '../store'
import type {
  AcceptInvitationRequest, ApplicationResponse, ChangePasswordRequest, CreateApplicationRequest, ExecutePasswordResetRequest,
  InvitationInfoResponse, LoginRequest, LoginResponse, MeResponse, RefreshTokenResponse,
  UpdateMeRequest,
} from '../../shared/api/types'

/**
 * 인증·계정 목 — 도메인 핸들러의 견본이다 (12-frontend-plan.md §5).
 *
 * 지켜야 할 것 다섯:
 *  1. 응답 형태는 백엔드 DTO record(없으면 docs/08-dto.md)와 1:1 — 느슨하게 흉내내면 실 API 전환 때 터진다
 *  2. 데이터는 `store.ts`(픽스처 복사본)에서만 가져온다 — 핸들러에 값을 적지 않는다
 *  3. 에러는 `error(code)` — 공통 ErrorResponse, 문구는 ErrorCode enum에서 온다
 *  4. 실패 경로를 목에 넣는다 — 성공만 흉내내면 화면의 에러 처리가 검증되지 않는다
 *  5. API 명세서에 있는 경로만 만든다 — 표에 없는 엔드포인트는 v1에 없다 (07-api-spec.md)
 *
 * 역할 전환: 시연 계정 둘(관리자 김서연 · 영업 박지훈)로 번갈아 로그인하면 된다.
 * 목은 Bearer 토큰으로 계정을 구분하므로 담당 스코프(OWNED_ONLY)도 함께 바뀐다.
 */

const LOCK_THRESHOLD = 5 // AU-06·09 — 5회 연속 실패 → 10분 제한

/** 이메일별 연속 실패 횟수. 미가입 이메일도 센다 (Q-30, SC-09 인증 확장) */
const failures = new Map<string, number>()

const loginResponse = (account: (typeof demoAccounts)[number]): LoginResponse => {
  const member = db.members.find((m) => m.id === account.memberId)!
  return { accessToken: account.accessToken, memberId: member.id, name: member.name, role: member.role, companyName: '한빛오피스' }
}

const meResponse = (request: Request): MeResponse => {
  const member = currentMember(request)
  return { memberId: member.id, name: member.name, email: member.email, phone: member.phone, role: member.role, companyId: db.companies[0].id, companyName: db.companies[0].name }
}

export const authHandlers = [
  http.post('/api/v1/auth/login', async ({ request }) => {
    const { email, password } = (await request.json()) as LoginRequest

    if ((failures.get(email) ?? 0) >= LOCK_THRESHOLD) return error('LOGIN_LOCKED')

    const account = demoAccounts.find((a) => a.email === email && a.password === password)
    const member = account && db.members.find((m) => m.id === account.memberId)
    // 미가입·비활성·정지 회사를 구별하지 않는다 (ON-13, MB-10, ON-09 → 전부 같은 응답)
    if (!account || !member || member.status !== 'ACTIVE') {
      failures.set(email, (failures.get(email) ?? 0) + 1)
      return error('LOGIN_FAILED')
    }

    failures.delete(email)
    session.login(account)
    // 실제 서버는 refresh를 Set-Cookie(HttpOnly)로 내린다 — 목에서는 흉내만 내고 바디에 담지 않는다
    return HttpResponse.json(loginResponse(account))
  }),

  /**
   * 재발급 — 쿠키가 곧 자격 증명이라 목에서는 마지막 로그인 계정으로 성공시킨다 (AU-03, Q-32).
   * 만료 경로를 시험하려면 `return error('REFRESH_TOKEN_NOT_ACTIVE')`로 바꾼다 — 로그인 화면 이동(AU-12) 확인용.
   */
  http.post('/api/v1/auth/refresh', () => {
    const body: RefreshTokenResponse = { accessToken: session.current().accessToken }
    return HttpResponse.json(body)
  }),

  http.post('/api/v1/auth/logout', () => noContent()),

  // ── 본인 계정 (AU-07 · AU-04 · NT-07)
  http.get('/api/v1/me', ({ request }) => HttpResponse.json(meResponse(request))),

  http.patch('/api/v1/me', async ({ request }) => {
    const member = currentMember(request)
    const body = (await request.json()) as UpdateMeRequest
    const fieldErrors = [
      ...(!body.name?.trim() ? [{ field: 'name', reason: '이름을 입력해 주세요.' }] : []),
      ...(body.name && body.name.length > 100 ? [{ field: 'name', reason: '100자 이하로 입력해 주세요.' }] : []),
      ...(body.phone && body.phone.length > 30 ? [{ field: 'phone', reason: '30자 이하로 입력해 주세요.' }] : []),
    ]
    if (fieldErrors.length) return error('VALIDATION_FAILED', fieldErrors)
    member.name = body.name.trim()
    member.phone = body.phone?.trim() || null
    return HttpResponse.json(meResponse(request))
  }),

  http.post('/api/v1/me/password', async ({ request }) => {
    const member = currentMember(request)
    const body = (await request.json()) as ChangePasswordRequest
    const account = demoAccounts.find((a) => a.memberId === member.id)
    if (!body.newPassword || body.newPassword.length < 8) {
      return error('VALIDATION_FAILED', [{ field: 'newPassword', reason: '8자 이상 입력해 주세요.' }])
    }
    // 세션은 유효하고 값만 틀렸다 — 401이 아니라 422 (07 v1.6.5)
    if (account && account.password !== body.currentPassword) return error('CURRENT_PASSWORD_MISMATCH')
    if (account) account.password = body.newPassword
    // 성공 시 refresh_token 전 행 폐기 → 이후 refresh는 실패해야 한다. 목은 다음 로그인까지 단순 통과시킨다
    return noContent()
  }),

  // /me/notification-settings(NT-07)는 여기 없다 — 백엔드가 D의 notification 모듈(#127)이라
  // 목도 `notification` 키에 둔다. auth를 실 API로 돌려도 그 탭은 #127 전까지 목으로 남는다.
]

/**
 * 초대 수락 · 비밀번호 재설정 · 사용 신청 (public — MB-03·04 · AU-05 · ON-01).
 * 데모 링크는 `/invite/demo-invite` — 시드 invitation과 같은 값. 그 외 토큰은 만료로 응답한다 (MB-04).
 */
export const invitationHandlers = [
  http.get('/public/api/v1/invitations/:token', ({ params }) => {
    const invitation = db.invitations.find((i) => i.rawToken === params.token && i.status === 'PENDING')
    if (!invitation) return error('INVITATION_NOT_PENDING')
    const body: InvitationInfoResponse = { companyName: db.companies[0].name, email: invitation.email, role: invitation.role }
    return HttpResponse.json(body)
  }),

  http.post('/public/api/v1/invitations/:token/accept', async ({ params, request }) => {
    const invitation = db.invitations.find((i) => i.rawToken === params.token && i.status === 'PENDING')
    if (!invitation) return error('INVITATION_NOT_PENDING')
    const body = (await request.json()) as AcceptInvitationRequest
    if (!body.name?.trim() || !body.password || body.password.length < 8) {
      return error('VALIDATION_FAILED', [{ field: 'password', reason: '8자 이상 입력해 주세요.' }])
    }
    invitation.status = 'ACCEPTED'
    db.members.push({ id: crypto.randomUUID(), name: body.name.trim(), email: invitation.email, phone: null, role: invitation.role, status: 'ACTIVE', createdAt: new Date().toISOString() })
    return noContent()
  }),

  // 미가입 이메일도 동일 응답 — 존재를 노출하지 않는다 (AU-05, SC-09 인증 확장)
  http.post('/public/api/v1/auth/password-reset-request', () => new HttpResponse(null, { status: 202 })),

  http.post('/public/api/v1/auth/password-reset', async ({ request }) => {
    const { token, newPassword } = (await request.json()) as ExecutePasswordResetRequest
    if (!newPassword || newPassword.length < 8) return error('VALIDATION_FAILED', [{ field: 'newPassword', reason: '8자 이상 입력해 주세요.' }])
    // `?token=expired`로 만료 경로를 시험한다
    if (!token || token === 'expired') return error('RESET_TOKEN_NOT_ACTIVE')
    return noContent()
  }),

  // 사용 신청 (ON-01·02) — 검토 대기로 접수
  http.post('/public/api/v1/applications', async ({ request }) => {
    const body = (await request.json()) as CreateApplicationRequest
    const fieldErrors = [
      ...(!body.companyName?.trim() ? [{ field: 'companyName', reason: '회사명을 입력해 주세요.' }] : []),
      ...(!body.businessNo?.trim() ? [{ field: 'businessNo', reason: '사업자등록번호를 입력해 주세요.' }] : []),
      ...(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email ?? '') ? [{ field: 'email', reason: '올바른 이메일 형식이 아닙니다.' }] : []),
      ...(!body.applicantName?.trim() ? [{ field: 'applicantName', reason: '신청자 이름을 입력해 주세요.' }] : []),
    ]
    if (fieldErrors.length) return error('VALIDATION_FAILED', fieldErrors)
    // 서버는 저장·조회 모두 소문자로 정규화한다 (ApplicationService.normalize) — 구성원 검사가 먼저다 (Q-14)
    const email = body.email.trim().toLowerCase()
    if (db.members.some((m) => m.email.toLowerCase() === email)) return error('EMAIL_ALREADY_MEMBER')
    if (db.applications.some((a) => a.email === email && a.status === 'PENDING')) return error('APPLICATION_ALREADY_PENDING')
    const created: ApplicationResponse = {
      id: crypto.randomUUID(), companyName: body.companyName.trim(), businessNo: body.businessNo.trim(), email,
      applicantName: body.applicantName.trim(),
      status: 'PENDING', rejectReason: null, decidedAt: null, createdAt: new Date().toISOString(),
    }
    db.applications.unshift(created)
    return HttpResponse.json(created, { status: 201 })
  }),
]
