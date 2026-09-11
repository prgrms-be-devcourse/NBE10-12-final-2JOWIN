import { api } from '../../shared/api/client'
import type { AuditLogDetailResponse, AuditLogResponse, PageParams, PageResponse } from '../../shared/api/types'

/** 감사 로그 API — 07 §B (AC-11) · activity/dto AuditLogResponse·AuditLogDetailResponse. 기업 관리자 전용 */

export type AuditLogListParams = PageParams & {
  entityType?: string
  /** YYYY-MM-DD */
  from?: string
  to?: string
}

/**
 * GET /api/v1/audit-logs?entityType=&from=&to= — 목록(payload 제외)
 *
 * **`from`·`to`는 `YYYY-MM-DD` 그대로 보낸다.** 서버가 한국 날짜로 받아 하루 경계로 끊는다 (#289) —
 * `to`는 그날을 포함한다. 시각으로 바꿔 보내면 서버의 날짜 파싱에 실패해 400 `VALIDATION_FAILED`다.
 */
export async function fetchAuditLogs({ from, to, ...rest }: AuditLogListParams) {
  const { data } = await api.get<PageResponse<AuditLogResponse>>('/audit-logs', {
    params: { ...rest, from: from || undefined, to: to || undefined },
  })
  return data
}

/** GET /api/v1/audit-logs/{id} — 상세(변경 전/후 값 포함) */
export async function fetchAuditLog(id: string) {
  const { data } = await api.get<AuditLogDetailResponse>(`/audit-logs/${id}`)
  return data
}
