import type { RequestHandler } from 'msw'
import { authHandlers, invitationHandlers } from './auth'
import { publicQuoteHandlers } from './publicQuote'

/**
 * 도메인별 목 핸들러 집결지 (12-frontend-plan.md §5).
 *
 * 각 도메인 담당이 `handlers/{도메인}.ts`를 만들어 아래 표에 등록한다.
 * 작성 견본은 `auth.ts` — DTO 1:1 · **픽스처만 사용** · 공통 ErrorResponse ·
 * 실패 경로 포함 · 명세에 있는 경로만.
 *
 * **도메인 단위 on/off** (§5.3) — `VITE_MOCK_DOMAINS`에서 이름을 빼면 그 도메인의 목이
 * 등록되지 않고, 요청은 Vite 프록시를 타고 실 API로 간다. 한 번에 다 갈아타지 않는다.
 *   2주 금 게이트: `auth`를 뺀다(A의 로그인·refresh 완료) · 3주 금: 전부 뺀다
 */
const BY_DOMAIN: Record<string, RequestHandler[]> = {
  auth: [...authHandlers, ...invitationHandlers],
  // 고객 열람(/public/api/v1/quotes)은 견적과 같은 자원이라 quote와 함께 켜고 끈다
  quote: publicQuoteHandlers,
  // member: [], customer: [], product: [], deal: [], order: [],
  // activity: [], notification: [], dashboard: [],
}

const enabledDomains = (import.meta.env.VITE_MOCK_DOMAINS ?? '')
  .split(',')
  .map((name) => name.trim())
  .filter(Boolean)

export const handlers: RequestHandler[] = enabledDomains.flatMap((domain) => BY_DOMAIN[domain] ?? [])

/** 개발 중 어떤 도메인이 목으로 도는지 한눈에 — 전환 사고를 줄인다 */
export const mockedDomains = enabledDomains.filter((domain) => domain in BY_DOMAIN)
