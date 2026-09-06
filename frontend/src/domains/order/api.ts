import { api } from '../../shared/api/client'
import type { OrderDetailResponse, OrderResponse, OrderScheduleRequest, PageParams, PageResponse } from '../../shared/api/types'

/**
 * 주문 API — 07-api-spec.md §C (OD-08~10) · 08 §C. 호출은 이 파일 안에서만 한다 (12 §8).
 * 주문 생성은 견적의 `convert-to-order`(domains/quote/api.ts)다 — 주문에는 상태가 없다 (Q-09).
 */

export interface OrderListParams extends PageParams {
  /** 생성일 기준 YYYY-MM-DD */
  from?: string
  to?: string
}

/** GET /orders?from=&to= — 담당 스코프 (OD-08) */
export async function fetchOrders(params: OrderListParams) {
  const { data } = await api.get<PageResponse<OrderResponse>>('/orders', { params })
  return data
}

/** GET /orders/{id} — 스냅샷 항목 (OD-09) */
export async function fetchOrder(id: string) {
  const { data } = await api.get<OrderDetailResponse>(`/orders/${id}`)
  return data
}

/** PATCH /orders/{id}/schedule — 착수일·납기 (OD-10). 응답 형태는 07에 미명시 → 상세로 가정 */
export async function scheduleOrder(id: string, body: OrderScheduleRequest) {
  const { data } = await api.patch<OrderDetailResponse>(`/orders/${id}/schedule`, body)
  return data
}
