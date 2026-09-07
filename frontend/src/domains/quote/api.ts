import { api, publicApi } from '../../shared/api/client'
import type {
  ApproveQuoteRequest, CreateInquiryRequest, CreateQuoteRequest, DealDetailResponse, OrderDetailResponse, PageParams, PageResponse,
  PublicQuoteResponse, QuoteDetailResponse, QuoteResponse, RejectQuoteRequest, ResendViewTokenRequest, SendQuoteRequest,
  SendQuoteResponse, UpdateQuoteRequest,
} from '../../shared/api/types'
import type { QuoteStatus } from '../../shared/ui/status'

/**
 * 견적 API — 07-api-spec.md §C (QT · AP-13·14 · OD-01) 구성원용 + §D 고객 열람(public, 토큰이 곧 인증).
 * 호출은 이 파일 안에서만 한다 (12 §8). 백엔드 QuoteController가 아직 없어 docs 07·08 §C가 정본이다.
 */

// ── 구성원 (/api/v1/quotes) ─────────────────────────────────────────────────

export interface QuoteListParams extends PageParams {
  status?: QuoteStatus
  dealId?: string
}

/** GET /quotes?status=&dealId= — 담당 스코프 (QT-20) */
export async function fetchQuotes(params: QuoteListParams) {
  const { data } = await api.get<PageResponse<QuoteResponse>>('/quotes', { params })
  return data
}

/** GET /quotes/{id} — 항목·추적·supersededByQuoteId (AP-06·07, QT-28) */
export async function fetchQuote(id: string) {
  const { data } = await api.get<QuoteDetailResponse>(`/quotes/${id}`)
  return data
}

/** POST /quotes — 작성 시작 → DRAFT (QT-01). 종결 Deal은 409 QUOTE_DEAL_CLOSED. 응답 형태는 07에 미명시 → 상세로 가정 */
export async function createQuote(body: CreateQuoteRequest) {
  const { data } = await api.post<QuoteDetailResponse>('/quotes', body)
  return data
}

/** PUT /quotes/{id} — 작성 중 전체 갱신 (QT-02~11·23) · version · 금액은 서버 계산 */
export async function updateQuote(id: string, body: UpdateQuoteRequest) {
  const { data } = await api.put<QuoteDetailResponse>(`/quotes/${id}`, body)
  return data
}

/** GET /quotes/{id}/preview — 고객 화면과 동일한 PublicQuoteResponse (QT-12) */
export async function fetchQuotePreview(id: string) {
  const { data } = await api.get<PublicQuoteResponse>(`/quotes/${id}/preview`)
  return data
}

/** POST /quotes/{id}/send — 효과: Deal 자동 승급, 응답에 갱신된 단계 (Q-25) */
export async function sendQuote(id: string, body: SendQuoteRequest) {
  const { data } = await api.post<SendQuoteResponse>(`/quotes/${id}/send`, body)
  return data
}

/** POST /quotes/{id}/withdraw — 회수 → 링크 만료 (QT-17) */
export async function withdrawQuote(id: string) {
  const { data } = await api.post<QuoteDetailResponse>(`/quotes/${id}/withdraw`)
  return data
}

/** POST /quotes/{id}/clone — 새 DRAFT (QT-19) */
export async function cloneQuote(id: string) {
  const { data } = await api.post<QuoteDetailResponse>(`/quotes/${id}/clone`)
  return data
}

/** POST /quotes/{id}/view-token/resend — 수신인 변경 재발송 (AP-13) */
export async function resendViewToken(id: string, body: ResendViewTokenRequest) {
  const { data } = await api.post<QuoteDetailResponse>(`/quotes/${id}/view-token/resend`, body)
  return data
}

/** POST /quotes/{id}/view-token/expire — 링크 수동 만료 (AP-14) */
export async function expireViewToken(id: string) {
  const { data } = await api.post<QuoteDetailResponse>(`/quotes/${id}/view-token/expire`)
  return data
}

/** POST /quotes/{id}/convert-to-order — 스냅샷 + Deal 성사 (OD-01~07). 응답 형태는 07에 미명시 → 주문 상세로 가정 */
export async function convertToOrder(id: string) {
  const { data } = await api.post<OrderDetailResponse>(`/quotes/${id}/convert-to-order`)
  return data
}

/**
 * GET /deals/{id} — 발송 모달의 수신인 후보(딜 고객사의 담당자)를 찾기 위해 customerId가 필요하다.
 * QuoteDetailResponse에는 customerId가 없어 딜 상세를 한 번 거친다 (계약 공백 — 보고서 참조).
 */
export async function fetchDealOfQuote(dealId: string) {
  const { data } = await api.get<DealDetailResponse>(`/deals/${dealId}`)
  return data
}

// ── 고객 열람 (/public/api/v1/quotes) ───────────────────────────────────────

export async function fetchPublicQuote(token: string) {
  const { data } = await publicApi.get<PublicQuoteResponse>(`/quotes/${token}`)
  return data
}

export async function approveQuote(token: string, body: ApproveQuoteRequest) {
  await publicApi.post(`/quotes/${token}/approve`, body)
}

export async function rejectQuote(token: string, body: RejectQuoteRequest) {
  await publicApi.post(`/quotes/${token}/reject`, body)
}

export async function createInquiry(token: string, body: CreateInquiryRequest) {
  await publicApi.post(`/quotes/${token}/inquiries`, body)
}
