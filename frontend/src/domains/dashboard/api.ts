import { api } from '../../shared/api/client'
import type { DashboardPerformanceResponse, DashboardSummaryResponse } from '../../shared/api/types'

/** 대시보드 API — dashboard/dto · 07 §D (DB-01~08). 호출은 이 파일 안에서만 한다 (12 §8). */

/** GET /api/v1/dashboard/summary?month=YYYY-MM — 영업은 본인 담당 기준 (🔶) */
export async function fetchDashboardSummary(month: string) {
  const { data } = await api.get<DashboardSummaryResponse>('/dashboard/summary', { params: { month } })
  return data
}

/** GET /api/v1/dashboard/performance?from=&to= — 기업 관리자 전용 (DB-06~08) */
export async function fetchDashboardPerformance(from: string, to: string) {
  const { data } = await api.get<DashboardPerformanceResponse>('/dashboard/performance', { params: { from, to } })
  return data
}
