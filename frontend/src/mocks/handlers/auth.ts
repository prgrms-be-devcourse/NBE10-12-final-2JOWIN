import { http, HttpResponse } from 'msw'
import { demoAccounts, invitations } from '../fixtures'

/**
 * 인증 목 — 도메인 핸들러의 견본이다 (12-frontend-plan.md §5).
 *
 * 각 도메인 담당은 이 파일을 본떠 `handlers/{도메인}.ts`를 만들고 `index.ts`에서 합친다.
 * 지켜야 할 것 다섯:
 *  1. 응답 형태는 `docs/08-dto.md`의 record와 1:1 — 느슨하게 흉내내면 실 API 전환 때 터진다
 *  2. 데이터는 `fixtures.ts`에서만 가져온다 — 핸들러에 값을 적지 않는다.
 *     픽스처는 백엔드 시드와 같은 세트이고, 한쪽을 고치면 반드시 함께 고친다 (§5.2)
 *  3. 에러는 공통 `ErrorResponse`(code·message·fieldErrors) 형태로 낸다
 *  4. 실패 경로를 목에 넣는다 — 성공만 흉내내면 화면의 에러 처리가 검증되지 않는다
 *  5. API 명세서에 있는 경로만 만든다 — 표에 없는 엔드포인트는 v1에 없다 (07-api-spec.md)
 */

const LOCK_THRESHOLD = 5 // AU-06·09 — 5회 연속 실패 → 10분 제한

/** 이메일별 연속 실패 횟수. 미가입 이메일도 센다 (Q-30, SC-09 인증 확장) */
const failures = new Map<string, number>()

const error = (code: string, message: string, status: number) =>
  HttpResponse.json({ code, message, fieldErrors: [] }, { status })

export const authHandlers = [
  http.post('/api/v1/auth/login', async ({ request }) => {
    const { email, password } = (await request.json()) as { email: string; password: string }

    if ((failures.get(email) ?? 0) >= LOCK_THRESHOLD) {
      return error('LOGIN_LOCKED', '로그인 시도가 너무 많습니다. 10분 후 다시 시도해 주세요.', 429)
    }

    const account = demoAccounts.find((a) => a.email === email && a.password === password)
    if (!account) {
      failures.set(email, (failures.get(email) ?? 0) + 1)
      // 미가입·비활성·정지 회사를 구별하지 않는다 (ON-13, MB-10, ON-09 → 전부 같은 응답)
      return error('LOGIN_FAILED', '이메일 또는 비밀번호가 올바르지 않습니다.', 401)
    }

    failures.delete(email)
    // 실제 서버는 refresh를 Set-Cookie(HttpOnly)로 내린다 — 목에서는 흉내만 내고 바디에 담지 않는다
    return HttpResponse.json(account.login)
  }),

  /**
   * 재발급 — 쿠키가 곧 자격 증명이라 목에서는 성공시킨다 (AU-03, Q-32).
   *
   * 만료 경로를 시험하려면 이 응답을 아래로 바꾼다:
   * `error('REFRESH_TOKEN_NOT_ACTIVE', '세션이 만료되었습니다. 다시 로그인해 주세요.', 401)`
   * 클라이언트가 로그인 화면으로 보내는지(AU-12) 확인할 수 있다.
   */
  http.post('/api/v1/auth/refresh', () => HttpResponse.json(demoAccounts[0].login)),

  http.post('/api/v1/auth/logout', () => new HttpResponse(null, { status: 204 })),

  http.get('/api/v1/me', () => HttpResponse.json(demoAccounts[0].login)),
]

/**
 * 초대 · 비밀번호 설정 (MB-03·04 · AU-05).
 * 데모 링크는 `/invite/demo-invite` — 픽스처의 `invitations`가 시드 invitation과 같은 값이다.
 * 그 외 토큰은 만료로 응답한다 (MB-04).
 */
export const invitationHandlers = [
  http.get('/public/api/v1/invitations/:token', ({ params }) => {
    const invitation = invitations[String(params.token)]
    if (!invitation) {
      return error('INVITATION_NOT_PENDING', '이 초대는 더 이상 유효하지 않습니다. 관리자에게 재발송을 요청해 주세요.', 409)
    }
    return HttpResponse.json(invitation)
  }),

  http.post('/public/api/v1/invitations/:token/accept', ({ params }) => {
    if (!invitations[String(params.token)]) {
      return error('INVITATION_NOT_PENDING', '이 초대는 더 이상 유효하지 않습니다. 관리자에게 재발송을 요청해 주세요.', 409)
    }
    return new HttpResponse(null, { status: 204 })
  }),

  // 미가입 이메일도 동일 응답 — 존재를 노출하지 않는다 (AU-05, SC-09 인증 확장)
  http.post('/public/api/v1/auth/password-reset-request', () => new HttpResponse(null, { status: 202 })),

  http.post('/public/api/v1/auth/password-reset', async ({ request }) => {
    const { token } = (await request.json()) as { token: string | null }
    if (token === 'expired') {
      return error('RESET_TOKEN_NOT_ACTIVE', '이 재설정 링크는 더 이상 유효하지 않습니다. 재설정을 다시 요청해 주세요.', 409)
    }
    return new HttpResponse(null, { status: 204 })
  }),
]
