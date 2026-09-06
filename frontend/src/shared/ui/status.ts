/**
 * 상태 값 — 백엔드 enum과 1:1 (backend/src/main/java/com/twojo/** 의 enum · docs/05-state-transitions.md).
 *
 * 컴포넌트 파일과 나눠 둔 이유는 Fast Refresh다 — 상수와 컴포넌트를 한 파일에 두면
 * 편집할 때마다 모듈이 통째로 다시 평가된다.
 * 한글 라벨은 전이표의 `한글 이름(영문 코드)` 표기를 그대로 쓴다.
 */

/** boundary/Role — /api/v1의 2종뿐. 플랫폼 관리자·고객 링크는 Role이 아니라 경로로 갈린다 (09 v1.6.3) */
export const ROLES = ['COMPANY_ADMIN', 'SALES_REP'] as const
export type Role = (typeof ROLES)[number]
export const ROLE_LABEL: Record<Role, string> = { COMPANY_ADMIN: '기업 관리자', SALES_REP: '영업 담당자' }

/** deal/entity/Deal.Stage — 전사 고정 6단계 (Q-11) */
export const DEAL_STAGES = ['LEAD', 'CONSULT', 'QUOTE', 'NEGOTIATION', 'WON', 'LOST'] as const
export type DealStage = (typeof DEAL_STAGES)[number]
export const DEAL_STAGE_LABEL: Record<DealStage, string> = {
  LEAD: '리드', CONSULT: '상담', QUOTE: '견적', NEGOTIATION: '협상', WON: '성사', LOST: '실패',
}
/** Deal.OPEN_STAGES — 진행 중 (리드~협상) */
export const OPEN_DEAL_STAGES = ['LEAD', 'CONSULT', 'QUOTE', 'NEGOTIATION'] as const satisfies readonly DealStage[]
export const isOpenStage = (stage: DealStage): boolean => (OPEN_DEAL_STAGES as readonly DealStage[]).includes(stage)
/** Deal.NEXT_STAGE — 인접 다음 단계만 (DL-07). NEGOTIATION→WON은 주문 전환 자동 (DL-09) */
export const NEXT_STAGE: Partial<Record<DealStage, DealStage>> = { LEAD: 'CONSULT', CONSULT: 'QUOTE', QUOTE: 'NEGOTIATION' }
/** Deal.PREVIOUS_STAGE — LEAD에서는 불가 (DEAL_NO_PREVIOUS_STAGE) */
export const PREVIOUS_STAGE: Partial<Record<DealStage, DealStage>> = { CONSULT: 'LEAD', QUOTE: 'CONSULT', NEGOTIATION: 'QUOTE' }

/** quote/entity/Quote.Status — 견적 7상태 */
export const QUOTE_STATUSES = ['DRAFT', 'SENT', 'VIEWED', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'EXPIRED'] as const
export type QuoteStatus = (typeof QUOTE_STATUSES)[number]
export const QUOTE_STATUS_LABEL: Record<QuoteStatus, string> = {
  DRAFT: '작성 중', SENT: '발송됨', VIEWED: '열람됨', APPROVED: '승인됨', REJECTED: '반려됨', WITHDRAWN: '회수됨', EXPIRED: '기간 만료',
}

/** quote/entity/Quote.VatMode — 기본 EXCLUDED (Q-16) */
export const VAT_MODES = ['EXCLUDED', 'INCLUDED'] as const
export type VatMode = (typeof VAT_MODES)[number]
export const VAT_MODE_LABEL: Record<VatMode, string> = { EXCLUDED: '별도', INCLUDED: '포함' }

/** product/entity/Product.Status */
export const PRODUCT_STATUSES = ['ACTIVE', 'DISCONTINUED'] as const
export type ProductStatus = (typeof PRODUCT_STATUSES)[number]
export const PRODUCT_STATUS_LABEL: Record<ProductStatus, string> = { ACTIVE: '판매 중', DISCONTINUED: '판매 중지' }

/** member/entity/Member.Status */
export const MEMBER_STATUSES = ['ACTIVE', 'INACTIVE'] as const
export type MemberStatus = (typeof MEMBER_STATUSES)[number]
export const MEMBER_STATUS_LABEL: Record<MemberStatus, string> = { ACTIVE: '활성', INACTIVE: '비활성' }

/** member/entity/Invitation.Status */
export const INVITATION_STATUSES = ['PENDING', 'ACCEPTED', 'CANCELED', 'EXPIRED'] as const
export type InvitationStatus = (typeof INVITATION_STATUSES)[number]
export const INVITATION_STATUS_LABEL: Record<InvitationStatus, string> = {
  PENDING: '대기', ACCEPTED: '수락됨', CANCELED: '취소됨', EXPIRED: '만료됨',
}

/** onboarding/entity/Application.Status */
export const APPLICATION_STATUSES = ['PENDING', 'APPROVED', 'REJECTED'] as const
export type ApplicationStatus = (typeof APPLICATION_STATUSES)[number]
export const APPLICATION_STATUS_LABEL: Record<ApplicationStatus, string> = {
  PENDING: '검토 대기', APPROVED: '승인됨', REJECTED: '반려됨',
}

/** onboarding/entity/Company.Status */
export const COMPANY_STATUSES = ['ACTIVE', 'SUSPENDED'] as const
export type CompanyStatus = (typeof COMPANY_STATUSES)[number]
export const COMPANY_STATUS_LABEL: Record<CompanyStatus, string> = { ACTIVE: '운영 중', SUSPENDED: '정지됨' }

/** activity/entity/Activity.Channel — 상담 수단 (AC-02) */
export const ACTIVITY_CHANNELS = ['CALL', 'MEETING', 'EMAIL'] as const
export type ActivityChannel = (typeof ACTIVITY_CHANNELS)[number]
export const ACTIVITY_CHANNEL_LABEL: Record<ActivityChannel, string> = { CALL: '전화', MEETING: '미팅', EMAIL: '이메일' }

/** ActivityResponse.type — MANUAL(사람) / AUTO(시스템) (AC-07) */
export const ACTIVITY_TYPES = ['MANUAL', 'AUTO'] as const
export type ActivityType = (typeof ACTIVITY_TYPES)[number]

/** notification/entity/Notification.Type — NotificationCommand.NotificationType과 동일 */
export const NOTIFICATION_TYPES = [
  'QUOTE_VIEWED', 'QUOTE_APPROVED', 'QUOTE_REJECTED', 'REMIND_NO_RESPONSE', 'INQUIRY_RECEIVED', 'EMAIL_FAILED',
] as const
export type NotificationType = (typeof NOTIFICATION_TYPES)[number]
export const NOTIFICATION_TYPE_LABEL: Record<NotificationType, string> = {
  QUOTE_VIEWED: '열람', QUOTE_APPROVED: '승인', QUOTE_REJECTED: '반려',
  REMIND_NO_RESPONSE: '무응답', INQUIRY_RECEIVED: '문의', EMAIL_FAILED: '메일 실패',
}
/** 메일 수신 설정 대상 — EMAIL_FAILED는 인앱 전용이라 끌 수 없다 (Q-35, NT-07) */
export const MAIL_SETTING_TYPES = NOTIFICATION_TYPES.filter((t) => t !== 'EMAIL_FAILED')

/** boundary/AuditActorType — 감사 행위자 */
export const AUDIT_ACTOR_TYPES = ['MEMBER', 'PLATFORM_ADMIN', 'CUSTOMER_LINK', 'SYSTEM'] as const
export type AuditActorType = (typeof AUDIT_ACTOR_TYPES)[number]
export const AUDIT_ACTOR_TYPE_LABEL: Record<AuditActorType, string> = {
  MEMBER: '구성원', PLATFORM_ADMIN: '플랫폼 관리자', CUSTOMER_LINK: '고객 링크', SYSTEM: '시스템',
}
