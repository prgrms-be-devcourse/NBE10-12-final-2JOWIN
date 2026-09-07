import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { updateTask } from '../activity/api'
import { fetchDashboardPerformance, fetchDashboardSummary } from './api'

/** 대시보드 Query 훅 — queryKey 규약 `[도메인, 리소스, 파라미터]` (12 §6.4) */

export const dashboardKeys = {
  all: ['dashboard'] as const,
  summary: (month: string) => ['dashboard', 'summary', month] as const,
  performance: (from: string, to: string) => ['dashboard', 'performance', { from, to }] as const,
}

export function useDashboardSummary(month: string) {
  return useQuery({ queryKey: dashboardKeys.summary(month), queryFn: () => fetchDashboardSummary(month), placeholderData: (previous) => previous })
}

/** 기업 관리자만 호출한다 — 영업 담당자는 403 FORBIDDEN이므로 `enabled`로 막는다 (DB-06~08) */
export function useDashboardPerformance(from: string, to: string, enabled: boolean) {
  return useQuery({ queryKey: dashboardKeys.performance(from, to), queryFn: () => fetchDashboardPerformance(from, to), enabled, placeholderData: (previous) => previous })
}

/** 후속 필요의 완료 처리 — PATCH /tasks/{id} {done:true} (AC-09). 끝나면 요약을 다시 읽는다 */
export function useCompleteTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (taskId: string) => updateTask(taskId, { done: true }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: dashboardKeys.all }),
  })
}
