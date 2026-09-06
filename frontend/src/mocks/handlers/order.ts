import type { RequestHandler } from 'msw'

/** order 도메인 목 자리 — 담당 도메인 PR에서 store.ts 기반 핸들러로 교체된다 */
export const orderHandlers: RequestHandler[] = []
