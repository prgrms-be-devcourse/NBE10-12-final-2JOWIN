/**
 * 공통 컴포넌트 (10-screen-design.md §6 · 12-frontend-plan.md §6.5).
 *
 * 화면은 여기 있는 것으로 조립한다. 같은 뜻을 다른 모양으로 만들지 않기 위한 규약이고,
 * 색·간격을 Radix 토큰으로만 쓴다는 규칙(§2.2~2.3)이 지켜지는 지점이기도 하다.
 * 필요한 것이 없으면 화면에서 새로 만들지 말고 여기에 추가한다.
 */
export { PageHeader } from './PageHeader'
export { EmptyState } from './EmptyState'
export { ErrorCallout } from './ErrorCallout'
export { ConfirmDialog } from './ConfirmDialog'
export { Money } from './Money'
export { DealStageBadge, QuoteStatusBadge, ViewedBadge, AutoBadge, RemainingBadge } from './StatusBadge'
export { DEAL_STAGES, QUOTE_STATUSES } from './status'
export type { DealStage, QuoteStatus } from './status'
