import { api } from '../../shared/api/client'
import type {
  ChangeAssigneeRequest, CreateDealRequest, DealDetailResponse, DealResponse, LoseDealRequest, PageParams, PageResponse,
  StageMoveRequest, UpdateDealRequest,
} from '../../shared/api/types'
import type { DealStage } from '../../shared/ui/status'

/**
 * Deal API — deal/controller/DealController · deal/dto (07 §C DL). 호출은 이 파일 안에서만 한다 (12 §8).
 * 영업 담당자는 본인 담당 Deal만 (🔶 SC-02) — 서버가 스코프를 자르고, 범위 밖은 404다.
 */

export interface DealListParams extends PageParams {
  stage?: DealStage
  assigneeId?: string
  customerId?: string
}

/** GET /api/v1/deals?stage=&assigneeId=&customerId= — 기본 정렬 createdAt DESC · size 최대 100 (DL-13·14) */
export async function fetchDeals(params: DealListParams) {
  const { data } = await api.get<PageResponse<DealResponse>>('/deals', { params })
  return data
}

/** GET /api/v1/deals/{id} — 견적·주문 요약만, 활동 이력은 /deals/{id}/activities (DL-15·18) */
export async function fetchDeal(id: string) {
  const { data } = await api.get<DealDetailResponse>(`/deals/${id}`)
  return data
}

/** POST /api/v1/deals — assigneeMemberId 없으면 생성자 본인 (DL-01~04) */
export async function createDeal(body: CreateDealRequest) {
  const { data } = await api.post<DealResponse>('/deals', body)
  return data
}

/** PATCH /api/v1/deals/{id} — 제목·예상 금액·마감일 · version 필수 (DL-02·03) */
export async function updateDeal(id: string, body: UpdateDealRequest) {
  const { data } = await api.patch<DealResponse>(`/deals/${id}`, body)
  return data
}

/** POST /api/v1/deals/{id}/advance — 인접 다음 단계만 · NEGOTIATION에서는 DEAL_WON_REQUIRES_ORDER (DL-07) */
export async function advanceDeal(id: string, body: StageMoveRequest) {
  const { data } = await api.post<DealResponse>(`/deals/${id}/advance`, body)
  return data
}

/** POST /api/v1/deals/{id}/revert — LEAD에서는 DEAL_NO_PREVIOUS_STAGE (DL-08) */
export async function revertDeal(id: string, body: StageMoveRequest) {
  const { data } = await api.post<DealResponse>(`/deals/${id}/revert`, body)
  return data
}

/** POST /api/v1/deals/{id}/lose — 효과: 진행 중 견적 EXPIRED + 열람 링크 만료(DEAL_LOST) (DL-10·11) */
export async function loseDeal(id: string, body: LoseDealRequest) {
  const { data } = await api.post<DealResponse>(`/deals/${id}/lose`, body)
  return data
}

/** POST /api/v1/deals/{id}/reopen — 실패 직전 단계로 · 만료된 견적·링크는 복원되지 않음 (DL-12) */
export async function reopenDeal(id: string, body: StageMoveRequest) {
  const { data } = await api.post<DealResponse>(`/deals/${id}/reopen`, body)
  return data
}

/** PATCH /api/v1/deals/{id}/assignee — 기업 관리자 · 같은 회사 활성 구성원 · version (DL-05, SC-06) */
export async function changeDealAssignee(id: string, body: ChangeAssigneeRequest) {
  const { data } = await api.patch<DealResponse>(`/deals/${id}/assignee`, body)
  return data
}

/**
 * DELETE /api/v1/deals/{id} — 소프트 삭제 · 견적 연결 시 DEAL_HAS_QUOTES (DL-16·17).
 * 07 §C에는 있으나 DealController에는 아직 없다 — 실 API 전환 시 C 구현 확인 필요.
 */
export async function deleteDeal(id: string) {
  await api.delete(`/deals/${id}`)
}
