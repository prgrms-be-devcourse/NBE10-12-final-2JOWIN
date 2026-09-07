import { api } from '../../shared/api/client'
import type { AuditLogDetailResponse, AuditLogResponse, PageParams, PageResponse } from '../../shared/api/types'

/** 감사 로그 API — 07 §B (AC-11) · activity/dto AuditLogResponse·AuditLogDetailResponse. 기업 관리자 전용 */

export type AuditLogListParams = PageParams & {
  entityType?: string
  /** YYYY-MM-DD */
  from?: string
  to?: string
}

/** GET /api/v1/audit-logs?entityType=&from=&to= — 목록(payload 제외) */
export async function fetchAuditLogs(params: AuditLogListParams) {
  const { data } = await api.get<PageResponse<AuditLogResponse>>('/audit-logs', { params })
  return data
}

/** GET /api/v1/audit-logs/{id} — 상세(변경 전/후 값 포함) */
export async function fetchAuditLog(id: string) {
  const { data } = await api.get<AuditLogDetailResponse>(`/audit-logs/${id}`)
  return data
}
