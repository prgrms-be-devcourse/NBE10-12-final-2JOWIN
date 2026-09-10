import { api } from '../../shared/api/client'
import { kstDayEnd, kstDayStart } from '../../shared/lib/format'
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
 * **`from`·`to`는 날짜로 받아 시각으로 바꿔 보낸다.** 화면은 날짜 선택기라 `YYYY-MM-DD`를 주는데
 * 서버 파라미터는 `Instant`라 그대로 보내면 400 `VALIDATION_FAILED`다. 경계는 KST 하루로 잡는다 —
 * 서버가 UTC로 비교하므로 여기서 바꾸지 않으면 한국 시간 기준의 "그 날"과 9시간 어긋난다.
 */
export async function fetchAuditLogs({ from, to, ...rest }: AuditLogListParams) {
  const { data } = await api.get<PageResponse<AuditLogResponse>>('/audit-logs', {
    params: { ...rest, from: from ? kstDayStart(from) : undefined, to: to ? kstDayEnd(to) : undefined },
  })
  return data
}

/** GET /api/v1/audit-logs/{id} — 상세(변경 전/후 값 포함) */
export async function fetchAuditLog(id: string) {
  const { data } = await api.get<AuditLogDetailResponse>(`/audit-logs/${id}`)
  return data
}
