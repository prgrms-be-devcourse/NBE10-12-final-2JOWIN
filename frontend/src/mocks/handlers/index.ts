import type { RequestHandler } from 'msw'
import { authHandlers, invitationHandlers } from './auth'
import { publicQuoteHandlers } from './publicQuote'
import { customerHandlers } from './customer'
import { memberHandlers } from './member'
import { productHandlers } from './product'
import { dealHandlers } from './deal'
import { quoteHandlers } from './quote'
import { orderHandlers } from './order'
import { activityHandlers } from './activity'
import { auditHandlers } from './audit'
import { notificationHandlers, notificationSettingHandlers } from './notification'
import { dashboardHandlers } from './dashboard'
import { adminHandlers } from './admin'

/**
 * 도메인별 목 핸들러 집결지 (12-frontend-plan.md §5).
 *
 * 각 도메인은 `handlers/{도메인}.ts` — 상태는 전부 `store.ts`를 공유한다.
 * 작성 견본은 `auth.ts` — DTO 1:1 · 저장소만 사용 · 공통 ErrorResponse ·
 * 실패 경로 포함 · 명세에 있는 경로만.
 *
 * 도메인 단위 on/off (§5.3) — `VITE_MOCK_DOMAINS`에서 이름을 빼면 그 도메인의 목이
 * 등록되지 않고, 요청은 Vite 프록시를 타고 실 API로 간다. 한 번에 다 갈아타지 않는다.
 *   2주 금 게이트: `auth`를 뺀다(A의 로그인·refresh 완료) · 3주 금: 전부 뺀다
 */
const BY_DOMAIN: Record<string, RequestHandler[]> = {
  auth: [...authHandlers, ...invitationHandlers],
  member: memberHandlers,
  customer: customerHandlers,
  product: productHandlers,
  deal: dealHandlers,
  quote: quoteHandlers,
  // 고객 열람(/public/api/v1/quotes — 조회·승인·반려·문의)은 같은 자원이지만 소유가 D(#156)라 따로 켜고 끈다 —
  // 구성원 견적(C)만 실 API로 두고 고객 링크 화면을 목으로 볼 수 있어야 한다 (둘 다 실 API인 지금은 둘 다 뺀다)
  publicQuote: publicQuoteHandlers,
  order: orderHandlers,
  activity: [...activityHandlers, ...auditHandlers],
  notification: notificationHandlers,
  // 알림 수신 설정(/me/notification-settings)만 따로 — 인앱 알림 API는 있지만 이 엔드포인트는 아직 없다(A 몫, 11 §2)
  notificationSettings: notificationSettingHandlers,
  dashboard: dashboardHandlers,
  // 플랫폼 관리자 (/admin/api/v1) — 별도 세션 (AU-08)
  admin: adminHandlers,
}

const enabledDomains = (import.meta.env.VITE_MOCK_DOMAINS ?? '')
  .split(',')
  .map((name: string) => name.trim())
  .filter(Boolean)

export const handlers: RequestHandler[] = enabledDomains.flatMap((domain: string) => BY_DOMAIN[domain] ?? [])

/** 개발 중 어떤 도메인이 목으로 도는지 한눈에 — 전환 사고를 줄인다 */
export const mockedDomains = enabledDomains.filter((domain: string) => domain in BY_DOMAIN)
