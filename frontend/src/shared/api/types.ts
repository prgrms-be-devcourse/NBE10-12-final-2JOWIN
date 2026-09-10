/**
 * DTO 미러 — 백엔드 record와 1:1로 유지한다 (12-frontend-plan.md §5.1).
 *
 * 우선순위: ① backend/src/main/java/com/twojo/{도메인}/dto (실제 record)
 *          ② docs/08-dto.md (아직 백엔드에 record가 없는 도메인)
 * 규칙: UUID=string · 금액=number(원 단위 정수) · 날짜=YYYY-MM-DD · 시각=ISO-8601 string.
 * 화면에서 임의 타입 정의 금지 — 목과 화면이 같은 타입을 쓴다.
 * enum 값은 백엔드 enum 문자열 그대로 (`status.ts`의 상수 배열이 원본).
 */

import type {
  ActivityChannel, ActivityType, ApplicationStatus, AuditActorType, CompanyStatus, DealStage, InvitationStatus,
  MemberStatus, NotificationSettingType, NotificationType, ProductStatus, QuoteStatus, Role, VatMode,
} from '../ui/status'

// ── 공통 (08-dto.md §0 · global/response · global/error)
export interface ErrorResponse {
  code: string
  message: string
  fieldErrors: { field: string; reason: string }[]
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** 목록 공통 파라미터 (07 Q-39) — 0-base · 기본 20 · 최대 100 */
export interface PageParams {
  page?: number
  size?: number
}

// ═══════════════════════════════════════════════════════════════════════════
// A. 인증 · 계정 · 구성원 (auth/dto · member/dto · 08 §A)
// ═══════════════════════════════════════════════════════════════════════════

/** auth/dto/LoginRequest — 구성원·플랫폼 관리자 공용 */
export interface LoginRequest {
  email: string
  password: string
  rememberMe: boolean
}

/**
 * auth/dto/LoginResponse — refresh는 Set-Cookie로만 온다 (v1.6.4).
 * 플랫폼 관리자 로그인(/admin/api/v1/auth/login)은 role이 `PLATFORM_ADMIN`(ActorType, Role enum 밖 — 09 v1.6.3 각주),
 * name은 이메일(platform_admin에 이름 컬럼이 없다), companyName은 null이다.
 */
export interface LoginResponse {
  accessToken: string
  memberId: string
  name: string
  role: Role | 'PLATFORM_ADMIN'
  companyName: string | null
}

/** auth/dto/RefreshTokenResponse */
export interface RefreshTokenResponse {
  accessToken: string
}

/** member/dto/MeResponse — GET /me · PATCH /me 응답 (07 v1.6.10) */
export interface MeResponse {
  memberId: string
  name: string
  email: string
  phone: string | null
  role: Role
  companyId: string
  companyName: string
}

/** 08 §A UpdateMeRequest — name 100 · phone 30 (v1.6.9) */
export interface UpdateMeRequest {
  name: string
  phone?: string | null
}

/** auth/dto/ChangePasswordRequest — 불일치는 422 CURRENT_PASSWORD_MISMATCH · 성공 시 전 세션 폐기 + 204 */
export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

/** auth/dto/RequestPasswordResetRequest — 미가입 이메일도 202 */
export interface RequestPasswordResetRequest {
  email: string
}

/** auth/dto/ExecutePasswordResetRequest — RESET·INITIAL_SETUP 공용 */
export interface ExecutePasswordResetRequest {
  token: string
  newPassword: string
}

/** 08 §A NotificationSettingResponse — 메일 채널만 (NT-07) · 행 없으면 기본 ON */
export interface NotificationSettingResponse {
  settings: NotificationSettingEntry[]
}
export interface NotificationSettingEntry {
  type: NotificationSettingType   // 4값 — 08 v1.6.14, boundary NotificationSettingType
  emailEnabled: boolean
}
/** 08 §A UpdateNotificationSettingsRequest — PUT 전체 교체 */
export interface UpdateNotificationSettingsRequest {
  settings: NotificationSettingEntry[]
}

/** 08 §A MemberResponse */
export interface MemberResponse {
  id: string
  name: string
  email: string
  phone: string | null
  role: Role
  status: MemberStatus
  createdAt: string
}

/** 08 §A MemberOptionResponse — DL-04 배정 선택지, 활성만 */
export interface MemberOptionResponse {
  id: string
  name: string
}

/** 08 §A ChangeRoleRequest */
export interface ChangeRoleRequest {
  role: Role
}

/** 08 §A DeactivateMemberRequest — 담당 Deal 1건 이상이면 transferToMemberId 필수 (MB-14) */
export interface DeactivateMemberRequest {
  transferToMemberId?: string | null
}

/** 08 §A CreateInvitationRequest */
export interface CreateInvitationRequest {
  email: string
  role: Role
}

/** 08 §A InvitationResponse */
export interface InvitationResponse {
  id: string
  email: string
  role: Role
  status: InvitationStatus
  expiresAt: string
  createdAt: string
}

/** 08 §A InvitationInfoResponse — 수락 화면용 (public) */
export interface InvitationInfoResponse {
  companyName: string
  email: string
  role: Role
}

/** 08 §A AcceptInvitationRequest */
export interface AcceptInvitationRequest {
  name: string
  password: string
}

// ── 온보딩 (onboarding/dto — 08 §A v1.6.11, PR #108)

/** onboarding/dto/CreateApplicationRequest — POST /public/api/v1/applications */
export interface CreateApplicationRequest {
  companyName: string
  businessNo: string
  email: string
  /** 승인 시 기업 관리자 계정의 member.name이 된다 (08 v1.6.11 · ON-07) */
  applicantName: string
}

/** onboarding/dto/ApplicationResponse — applicationNo 없음 (v1.6, id로 식별) */
export interface ApplicationResponse {
  id: string
  companyName: string
  businessNo: string
  email: string
  applicantName: string
  status: ApplicationStatus
  rejectReason: string | null
  decidedAt: string | null
  createdAt: string
}

/** 08 §A RejectApplicationRequest — ON-14 사유 필수 */
export interface RejectApplicationRequest {
  reason: string
}

/** 08 §A SuspendCompanyRequest — ON-08 */
export interface SuspendCompanyRequest {
  reason: string
}

/** 08 §A CompanyResponse — ON-12 이용 현황의 v1 범위는 memberCount뿐 (Q-41) */
export interface CompanyResponse {
  id: string
  name: string
  businessNo: string
  status: CompanyStatus
  suspendReason: string | null
  memberCount: number
  createdAt: string
}

// ═══════════════════════════════════════════════════════════════════════════
// B. 고객사 · 상품 · 활동 (customer/dto · product/dto · activity/dto)
// ═══════════════════════════════════════════════════════════════════════════

/** customer/dto/CustomerResponse */
export interface CustomerResponse {
  id: string
  name: string
  industry: string | null
  size: string | null
  note: string | null
  /** 등록자 — 기록용이고 권한 판정에 쓰지 않는다 (SC-03) */
  createdByMemberId: string
  createdAt: string
}

/** customer/dto/ContactResponse */
export interface ContactResponse {
  id: string
  name: string
  title: string | null
  phone: string | null
  email: string
  /** 고객사당 대표 1명 — 견적 수신인 기본값 (CU-11, Q-07) */
  primary: boolean
}

/** customer/dto/CustomerDetailResponse.DealSummary — 고객사 상세의 딜 이력 (CU-12) */
export interface CustomerDealSummary {
  id: string
  title: string
  stage: DealStage
  expectedAmount: number | null
  /** 성사 후 표시 금액 = 주문 합계 (DL-18). 성사 전에는 null */
  wonAmount: number | null
  createdAt: string
}

/** customer/dto/CustomerDetailResponse */
export interface CustomerDetailResponse extends CustomerResponse {
  createdByMemberName: string
  contacts: ContactResponse[]
  deals: CustomerDealSummary[]
}

/** customer/dto/CreateCustomerRequest */
export interface CreateCustomerRequest {
  name: string
  industry?: string | null
  size?: string | null
  note?: string | null
}
/** customer/dto/UpdateCustomerRequest — PATCH: 보내지 않은 필드는 미변경 (08 v1.6.7) */
export interface UpdateCustomerRequest {
  name?: string
  industry?: string | null
  size?: string | null
  note?: string | null
}

/** customer/dto/CreateContactRequest */
export interface CreateContactRequest {
  name: string
  title?: string | null
  phone?: string | null
  email: string
}
/** customer/dto/UpdateContactRequest — PATCH: 보내지 않은(undefined) 필드는 미변경 */
export interface UpdateContactRequest {
  name?: string
  title?: string | null
  phone?: string | null
  email?: string
}

/** product/dto/ProductResponse */
export interface ProductResponse {
  id: string
  name: string
  unit: string
  unitPrice: number
  description: string | null
  status: ProductStatus
}

/** product/dto/CreateProductRequest — 회사 내 이름 유일 → 409 PRODUCT_NAME_DUPLICATED */
export interface CreateProductRequest {
  name: string
  unit: string
  unitPrice: number
  description?: string | null
}

/** product/dto/UpdateProductRequest — PATCH */
export interface UpdateProductRequest {
  name?: string
  unit?: string
  unitPrice?: number
  description?: string | null
}

/** activity/dto/ActivityResponse — type MANUAL/AUTO (AC-07) · channel은 수동 기록에만 */
export interface ActivityResponse {
  id: string
  type: ActivityType
  channel: ActivityChannel | null
  content: string
  authorMemberId: string
  authorMemberName: string
  /** 퇴사·비활성 작성자 표시용 */
  authorActive: boolean
  occurredAt: string
}

/** activity/dto/CreateActivityRequest */
export interface CreateActivityRequest {
  channel: ActivityChannel
  content: string
  occurredAt: string
}

/** activity/dto/UpdateActivityRequest — PATCH · 작성자 본인만 (AC-04) */
export interface UpdateActivityRequest {
  channel?: ActivityChannel
  content?: string
  occurredAt?: string
}

/** activity/dto/TaskResponse — 배정 없음, 담당 Deal 기준 (Q-29) */
export interface TaskResponse {
  id: string
  dealId: string
  content: string
  dueDate: string
  doneAt: string | null
}

/** activity/dto/CreateTaskRequest */
export interface CreateTaskRequest {
  content: string
  dueDate: string
}

/** activity/dto/UpdateTaskRequest — PATCH · done=true로 완료 처리 */
export interface UpdateTaskRequest {
  content?: string
  dueDate?: string
  done?: boolean
}

/** activity/dto/AuditLogResponse — 목록(payload 제외) */
export interface AuditLogResponse {
  id: string
  entityType: string
  entityId: string
  eventType: string
  actorType: AuditActorType
  actorId: string | null
  actorName: string | null
  occurredAt: string
}

/** activity/dto/AuditLogDetailResponse — 상세: 변경된 필드만 before/after */
export interface AuditLogDetailResponse extends AuditLogResponse {
  changes: Record<string, { before: unknown; after: unknown }>
}

// ═══════════════════════════════════════════════════════════════════════════
// C. Deal · 견적 · 주문 (deal/dto · 08 §C)
// ═══════════════════════════════════════════════════════════════════════════

/** deal/dto/DealRequests.CreateDeal — assigneeMemberId null이면 생성자 본인 */
export interface CreateDealRequest {
  customerId: string
  title: string
  expectedAmount?: number | null
  dueDate?: string | null
  assigneeMemberId?: string | null
}

/** deal/dto/DealRequests.UpdateDeal — 실제 record는 title이 선택(@NotBlank 없음), version 필수 */
export interface UpdateDealRequest {
  title?: string
  expectedAmount?: number | null
  dueDate?: string | null
  version: number
}

/** deal/dto/DealRequests.StageMove — advance · revert · reopen 공용 */
export interface StageMoveRequest {
  version: number
}

/** deal/dto/DealRequests.LoseDeal */
export interface LoseDealRequest {
  reason: string
  version: number
}

/** deal/dto/DealRequests.ChangeAssignee — 같은 회사 활성 구성원 */
export interface ChangeAssigneeRequest {
  assigneeMemberId: string
  version: number
}

/** deal/dto/DealResponses.DealItem — 목록·보드 공용 (08의 DealResponse) */
export interface DealResponse {
  id: string
  title: string
  stage: DealStage
  expectedAmount: number | null
  /** DL-18: 주문 합계, 주문 없으면 null. 표시: 성사 전 expected, 성사 후 won */
  wonAmount: number | null
  customerId: string
  customerName: string
  assigneeMemberId: string
  assigneeMemberName: string
  dueDate: string | null
  version: number
  createdAt: string
}

/** deal/dto/DealResponses.DealDetail — 견적·주문 요약만, 활동 이력은 /deals/{id}/activities (v1.6.3) */
export interface DealDetailResponse {
  id: string
  title: string
  stage: DealStage
  expectedAmount: number | null
  wonAmount: number | null
  customerId: string
  customerName: string
  assigneeMemberId: string
  assigneeMemberName: string
  dueDate: string | null
  lostReason: string | null
  quotes: DealQuoteSummary[]
  orders: DealOrderSummary[]
  version: number
  createdAt: string
}
export interface DealQuoteSummary {
  id: string
  quoteNo: string
  status: QuoteStatus
  totalAmount: number
  sentAt: string | null
}
export interface DealOrderSummary {
  id: string
  orderNo: string
  totalAmount: number
  createdAt: string
}

/** 08 §C CreateQuoteRequest — 종결 Deal → 409 QUOTE_DEAL_CLOSED */
export interface CreateQuoteRequest {
  dealId: string
}

/** 08 §C UpdateQuoteRequest — PUT · DRAFT만 · 금액은 서버 계산 */
export interface UpdateQuoteRequest {
  validUntil: string
  vatMode: VatMode
  terms?: string | null
  items: UpdateQuoteItem[]
  version: number
}
export interface UpdateQuoteItem {
  /** null = 직접 입력 (QT-03) */
  productId: string | null
  name: string
  unit: string
  quantity: number
  unitPrice: number
  sortOrder: number
}

/** 08 §C SendQuoteRequest — 수신인은 같은 고객사 담당자 (CONTACT_NOT_IN_CUSTOMER) */
export interface SendQuoteRequest {
  recipientContactId: string
  message?: string | null
}

/** 08 §C ResendViewTokenRequest — AP-13 수신인 변경 재발송 */
export interface ResendViewTokenRequest {
  recipientContactId: string
}

/** 08 §C SendQuoteResponse — dealStage는 Q-25 자동 승급 반영값 */
export interface SendQuoteResponse {
  quoteId: string
  status: QuoteStatus
  dealStage: DealStage
  version: number
}

/** 08 §C QuoteResponse — 목록 */
export interface QuoteResponse {
  id: string
  quoteNo: string
  dealId: string
  status: QuoteStatus
  totalAmount: number
  validUntil: string
  sentAt: string | null
  firstViewedAt: string | null
  version: number
}

/** 08 §C QuoteDetailResponse */
export interface QuoteDetailResponse {
  id: string
  quoteNo: string
  dealId: string
  dealTitle: string
  status: QuoteStatus
  vatMode: VatMode
  terms: string | null
  validUntil: string
  supplyAmount: number
  vatAmount: number
  totalAmount: number
  items: QuoteItemResponse[]
  /** QT-19 복제 원본 */
  clonedFromQuoteId: string | null
  /** QT-28 반려·회수 → 대체 견적 */
  supersededByQuoteId: string | null
  rejectReason: string | null
  /** AP-19 자기 신고 — 인증된 신원처럼 표시하지 않는다 */
  responderName: string | null
  responderTitle: string | null
  sentAt: string | null
  firstViewedAt: string | null
  respondedAt: string | null
  version: number
  createdAt: string
}
export interface QuoteItemResponse {
  id: string
  productId: string | null
  name: string
  unit: string
  quantity: number
  unitPrice: number
  amount: number
  /** QT-24 작성 시점 카탈로그 단가 — 직접 입력이면 null */
  catalogPriceAtCreation: number | null
  sortOrder: number
}

/** 08 §C OrderResponse — 목록 · dealId는 quote 조인 */
export interface OrderResponse {
  id: string
  orderNo: string
  quoteId: string
  quoteNo: string
  dealId: string
  dealTitle: string
  customerId: string
  customerName: string
  supplyAmount: number
  vatAmount: number
  totalAmount: number
  startDate: string | null
  deliveryDate: string | null
  createdAt: string
}

/** 08 §C OrderDetailResponse — 스냅샷 항목 (OD-04, FK 없음) */
export interface OrderDetailResponse extends OrderResponse {
  /** 전환 직후에는 항상 WON — 자동 성사(OD-06)의 확인용. 목록에는 없다 (08 v1.6.18) */
  dealStage: DealStage
  items: OrderItemResponse[]
}
export interface OrderItemResponse {
  name: string
  unit: string
  quantity: number
  unitPrice: number
  amount: number
}

/**
 * 08 §C OrderScheduleRequest — OD-10.
 *
 * **PATCH지만 null은 "미변경"이 아니라 "지움"이다** — 두 날짜는 하나의 일정이라 서버가 함께
 * 덮어쓴다 (`Order.updateSchedule`). 08 §B의 "안 보내면 미변경"과 다른 자리라 필드를 선택으로
 * 두지 않는다 — 하나만 보내면 나머지가 지워진다.
 */
export interface OrderScheduleRequest {
  startDate: string | null
  deliveryDate: string | null
}

// ═══════════════════════════════════════════════════════════════════════════
// D. 고객 열람 · 알림 · 대시보드 (approval/dto · notification/dto · dashboard/dto)
// ═══════════════════════════════════════════════════════════════════════════

/** boundary/PublicQuoteResponse — 고객 열람(D)과 구성원 미리보기(C, QT-12)가 공유. 미리보기는 #114 전까지 회사·담당자 없는 PublicQuoteView 모양 */
export interface PublicQuoteResponse {
  quoteNo: string
  status: QuoteStatus
  /** 발송 회사 — 고객이 "누가 보냈는지"를 0.5초 안에 알아야 한다 (GAP-05) */
  companyName: string
  /** 발송 회사 사업자등록번호 — 회사명과 함께 최상단 표시 (10 §5.6, 08 v1.6.5) */
  companyBusinessNo: string
  /** Deal의 현재 담당자를 동적 조회한다 — 발송자 스냅샷이 아니다 (AP-18) */
  assignee: { name: string; email: string; phone: string }
  vatMode: VatMode
  terms: string | null
  validUntil: string
  /** 3분리 표시 (QT-25) */
  supplyAmount: number
  vatAmount: number
  totalAmount: number
  items: { name: string; unit: string; quantity: number; unitPrice: number; amount: number }[]
  /** false면 버튼 비활성 + 사유 안내 — 정지 회사 또는 응답 완료 */
  respondable: boolean
}

/** approval/dto/ApproveQuoteRequest — AP-19 · Q-44 자기 신고 */
export interface ApproveQuoteRequest {
  responderName: string
  responderTitle?: string
}

/** approval/dto/RejectQuoteRequest */
export interface RejectQuoteRequest {
  reason: string
  responderName: string
  responderTitle?: string
}

/** approval/dto/CreateInquiryRequest — AP-15 */
export interface CreateInquiryRequest {
  content: string
}

/** notification/dto/NotificationResponse — refType은 현재 QUOTE만 (NotificationCommand.RefType) */
export interface NotificationResponse {
  id: string
  type: NotificationType
  message: string
  refType: 'QUOTE'
  refId: string
  readAt: string | null
  createdAt: string
}

/** dashboard/dto/DashboardSummaryResponse */
export interface DashboardSummaryResponse {
  /** DB-01 진행 단계(리드~협상)만 — WON은 monthWonAmount, LOST 제외 (v1.6.1) */
  pipeline: { stage: DealStage; count: number; expectedAmountSum: number }[]
  /** DB-02 이달 성사 = 주문 합계 (DL-18) */
  monthWonAmount: number
  monthWonCount: number
  /** DB-03 응답 대기 — firstViewedAt null이면 미열람 (GAP-08) */
  waitingQuotes: DashboardWaitingQuote[]
  /** DB-05 후속 필요 */
  followUps: DashboardFollowUp[]
  /** DB-04 최근 활동 */
  recentActivities: DashboardRecentActivity[]
}
export interface DashboardWaitingQuote {
  quoteId: string
  quoteNo: string
  /** 서버가 아직 null을 준다 — B의 회사 스코프 이름 조회 창구 대기 (#218) */
  customerName: string | null
  sentAt: string
  firstViewedAt: string | null
  validUntil: string
}
export interface DashboardFollowUp {
  taskId: string
  dealId: string
  dealTitle: string
  content: string
  dueDate: string
}
export interface DashboardRecentActivity {
  dealId: string
  dealTitle: string
  summary: string
  occurredAt: string
}

/** dashboard/dto/DashboardPerformanceResponse — 기업 관리자 전용 (DB-06~08) */
export interface DashboardPerformanceResponse {
  members: { memberId: string; name: string; wonCount: number; wonAmount: number; activeDealCount: number }[]
  conversions: { fromStage: DealStage; toStage: DealStage; rate: number }[]
}
