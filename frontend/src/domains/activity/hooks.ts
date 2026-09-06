import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CreateActivityRequest, CreateTaskRequest, UpdateActivityRequest, UpdateTaskRequest } from '../../shared/api/types'
import { createActivity, createTask, deleteActivity, fetchDealActivities, updateActivity, updateTask, type ActivityListParams } from './api'

/** 상담 기록 · 할 일 Query 훅 — queryKey `[activity, 리소스, 파라미터]` (12 §6.4) */

export const activityKeys = {
  all: ['activity'] as const,
  deal: (dealId: string, params: ActivityListParams) => ['activity', 'deal', dealId, params] as const,
}

/** Deal 타임라인 — 목록 공통 size 상한(100)까지 한 번에 (AC-06) */
export function useDealActivities(dealId: string, params: ActivityListParams = { size: 100 }) {
  return useQuery({ queryKey: activityKeys.deal(dealId, params), queryFn: () => fetchDealActivities(dealId, params) })
}

/** 상담 기록 작성·수정·삭제 — 성공 시 해당 Deal의 타임라인과 고객사 이력을 무효화한다 (AC-10은 같은 데이터) */
export function useActivityMutations(dealId: string) {
  const queryClient = useQueryClient()
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: activityKeys.all })
    queryClient.invalidateQueries({ queryKey: ['customer', 'activities'] })
  }
  const create = useMutation({ mutationFn: (body: CreateActivityRequest) => createActivity(dealId, body), onSuccess: refresh })
  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateActivityRequest }) => updateActivity(id, body),
    onSuccess: refresh,
  })
  const remove = useMutation({ mutationFn: (id: string) => deleteActivity(id), onSuccess: refresh })
  return { create, update, remove }
}

/** 할 일 등록 — 목록 API가 없어 무효화할 캐시는 대시보드 후속 필요뿐이다 (DB-05) */
export function useCreateTask(dealId: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateTaskRequest) => createTask(dealId, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
  })
}

/** 할 일 완료·수정 — 대시보드 후속 필요 목록에서 쓴다 */
export function useUpdateTask() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateTaskRequest }) => updateTask(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
  })
}
