import { useQuery } from '@tanstack/react-query'
import { fetchAuditLog, fetchAuditLogs, type AuditLogListParams } from './api'

/** 감사 로그 Query 훅 — queryKey 규약 `[도메인, 리소스, 파라미터]` (12 §6.4). 쓰기 없음 — 읽기 전용 리소스 */

export const auditKeys = {
  all: ['audit'] as const,
  list: (params: AuditLogListParams) => ['audit', 'list', params] as const,
  detail: (id: string) => ['audit', 'detail', id] as const,
}

export function useAuditLogList(params: AuditLogListParams) {
  return useQuery({ queryKey: auditKeys.list(params), queryFn: () => fetchAuditLogs(params), placeholderData: (previous) => previous })
}

export function useAuditLogDetail(id: string | null) {
  return useQuery({ queryKey: auditKeys.detail(id ?? ''), queryFn: () => fetchAuditLog(id!), enabled: id !== null, retry: false })
}
