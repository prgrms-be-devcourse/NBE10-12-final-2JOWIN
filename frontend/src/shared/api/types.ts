/**
 * DTO 미러 — docs/08-dto.md의 record와 1:1로 유지한다 (12-frontend-plan.md §5.1).
 * 규칙: UUID=string · 금액=number(원 단위 정수) · 날짜/시각=ISO-8601 string.
 * 화면에서 임의 타입 정의 금지 — 목과 화면이 같은 타입을 쓴다.
 */

// ── 공통 (08-dto.md §0)
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

// ── 인증 (A · 07-api-spec.md §A)
export interface LoginResponse {
  accessToken: string
  memberId: string
  name: string
  role: 'COMPANY_ADMIN' | 'SALES_REP'
  companyName: string
}

// ── 고객 열람 (D 도메인 · 화면은 E — 12-frontend-plan.md §3)
export interface PublicQuoteResponse {
  quoteNo: string
  status: string
  /** 발송 회사 — 고객이 "누가 보냈는지"를 0.5초 안에 알아야 한다 (GAP-05) */
  companyName: string
  /** Deal의 **현재** 담당자를 동적 조회한다 — 발송자 스냅샷이 아니다 (AP-18) */
  assignee: { name: string; email: string; phone: string }
  vatMode: string
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

/** AP-19 · Q-44 — 응답자가 직접 밝힌 정보. 시스템은 검증하지 않는다 */
export interface ApproveQuoteRequest {
  responderName: string
  responderTitle?: string
}

export interface RejectQuoteRequest {
  reason: string
  responderName: string
  responderTitle?: string
}

export interface CreateInquiryRequest {
  content: string
}

// TODO: 각 도메인 담당이 자기 도메인 DTO를 docs/08-dto.md에서 옮겨 적는다.
