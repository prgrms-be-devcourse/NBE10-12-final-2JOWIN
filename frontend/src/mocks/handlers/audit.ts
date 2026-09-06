import { delay, http, HttpResponse } from 'msw'
import { currentMember, db, error, memberName, notFound, paged } from '../store'
import type { AuditLogDetailResponse, AuditLogResponse } from '../../shared/api/types'

/**
 * 감사 로그 목 — 07 §B (AC-11) · activity/dto AuditLogResponse·AuditLogDetailResponse.
 * 기업 관리자 전용 → 영업 담당자는 403 FORBIDDEN (09 구현 위치: 역할 자체로 갈리는 행위).
 * 데이터는 `db.auditLogs` — 다른 도메인 핸들러가 recordAudit로 계속 쌓는다.
 */

const adminOnly = (request: Request) => (currentMember(request).role === 'COMPANY_ADMIN' ? null : error('FORBIDDEN'))

const actorNameOf = (log: (typeof db.auditLogs)[number]) => (log.actorType === 'MEMBER' && log.actorId ? memberName(log.actorId) || null : null)

const toSummary = (log: (typeof db.auditLogs)[number]): AuditLogResponse => ({
  id: log.id, entityType: log.entityType, entityId: log.entityId, eventType: log.eventType,
  actorType: log.actorType, actorId: log.actorId, actorName: actorNameOf(log), occurredAt: log.occurredAt,
})

export const auditHandlers = [
  // 목록 — 요약(payload 제외) · occurredAt DESC · entityType·from·to 필터
  http.get('/api/v1/audit-logs', async ({ request }) => {
    await delay(120)
    const forbidden = adminOnly(request)
    if (forbidden) return forbidden
    const url = new URL(request.url)
    const entityType = url.searchParams.get('entityType') ?? ''
    const from = url.searchParams.get('from') ?? ''
    const to = url.searchParams.get('to') ?? ''
    const list = db.auditLogs
      .filter((l) => !entityType || l.entityType === entityType)
      // from·to는 KST 날짜 — 시각의 날짜 부분(UTC)과 비교하면 하루 차이가 날 수 있어 T 이전 10자만 느슨하게 본다
      .filter((l) => !from || l.occurredAt.slice(0, 10) >= from)
      .filter((l) => !to || l.occurredAt.slice(0, 10) <= to)
      .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
      .map(toSummary)
    return HttpResponse.json(paged(list, url))
  }),

  // 상세 — 변경 전/후 값 포함
  http.get('/api/v1/audit-logs/:id', async ({ params, request }) => {
    await delay(100)
    const forbidden = adminOnly(request)
    if (forbidden) return forbidden
    const log = db.auditLogs.find((l) => l.id === params.id)
    if (!log) return notFound()
    const body: AuditLogDetailResponse = { ...toSummary(log), changes: log.changes }
    return HttpResponse.json(body)
  }),
]
