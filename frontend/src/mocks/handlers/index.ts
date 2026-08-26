import type { RequestHandler } from 'msw'

/**
 * 도메인별 목 핸들러 집결지 (12-frontend-plan.md §5).
 * 각 도메인 담당이 handlers/{도메인}.ts를 만들어 여기서 합친다.
 * 픽스처는 fixtures.ts — 시연 데이터·백엔드 시드와 같은 세트를 쓴다 (§5.2).
 */
export const handlers: RequestHandler[] = [
  // ...auth, ...customer, ...deal, ...
]
