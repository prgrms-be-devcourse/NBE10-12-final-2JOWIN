/**
 * 상태 값 — 전이표(docs/05-state-transitions.md)의 enum과 1:1.
 *
 * 컴포넌트 파일과 나눠 둔 이유는 Fast Refresh다 — 상수와 컴포넌트를 한 파일에 두면
 * 편집할 때마다 모듈이 통째로 다시 평가된다.
 */

/** 전사 고정 6단계 (Q-11) — 리드 → 상담 → 견적 → 협상 → 성사, 어디서든 → 실패 */
export const DEAL_STAGES = ['LEAD', 'CONSULT', 'QUOTE', 'NEGOTIATION', 'WON', 'LOST'] as const
export type DealStage = (typeof DEAL_STAGES)[number]

/** 견적 7상태 */
export const QUOTE_STATUSES = [
  'DRAFT', 'SENT', 'VIEWED', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'EXPIRED',
] as const
export type QuoteStatus = (typeof QUOTE_STATUSES)[number]
