import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  ChangeAssigneeRequest, CreateDealRequest, LoseDealRequest, StageMoveRequest, UpdateDealRequest,
} from '../../shared/api/types'
import { DEAL_STAGES, type DealStage } from '../../shared/ui/status'
import { fetchMemberOptions } from '../member/api'
import {
  advanceDeal, changeDealAssignee, createDeal, deleteDeal, fetchDeal, fetchDeals, loseDeal, reopenDeal, revertDeal, updateDeal,
  type DealListParams,
} from './api'

/** Deal Query 훅 — queryKey 규약 `[도메인, 리소스, 파라미터]` (12 §6.4) */

export const dealKeys = {
  all: ['deal'] as const,
  list: (params: DealListParams) => ['deal', 'list', params] as const,
  detail: (id: string) => ['deal', 'detail', id] as const,
}

/** 보드 한 칸에 담을 수 있는 최대 — 목록 공통 규칙의 size 상한 (Q-39) */
const BOARD_PAGE_SIZE = 100

export interface BoardFilters {
  assigneeId?: string
  customerId?: string
}

/**
 * 딜 보드 — 단계별로 `GET /deals?stage=`를 나눠 호출한다 (C 결정, DealController javadoc · 10 §5.2).
 * 6단계를 한 번에 요청하고 칸마다 독립적으로 로딩된다. 실패(LOST)도 같은 방식으로 받아 접힌 영역에 둔다.
 */
export function useDealBoard(filters: BoardFilters) {
  const results = useQueries({
    queries: DEAL_STAGES.map((stage) => {
      const params: DealListParams = { stage, size: BOARD_PAGE_SIZE, ...filters }
      return {
        queryKey: dealKeys.list(params),
        queryFn: () => fetchDeals(params),
        placeholderData: (previous: Awaited<ReturnType<typeof fetchDeals>> | undefined) => previous,
      }
    }),
  })
  const byStage = Object.fromEntries(DEAL_STAGES.map((stage, i) => [stage, results[i]])) as Record<DealStage, (typeof results)[number]>
  return {
    byStage,
    isPending: results.some((r) => r.isPending),
    isFetching: results.some((r) => r.isFetching),
    error: results.find((r) => r.error)?.error ?? null,
    refetch: () => results.forEach((r) => r.refetch()),
  }
}

export function useDealList(params: DealListParams) {
  return useQuery({
    queryKey: dealKeys.list(params),
    queryFn: () => fetchDeals(params),
    placeholderData: (previous) => previous,
  })
}

export function useDealDetail(id: string) {
  return useQuery({ queryKey: dealKeys.detail(id), queryFn: () => fetchDeal(id), retry: false })
}

/** 담당자 선택지 — 활성 구성원 이름·id만 (DL-04, /members/options) */
export function useMemberOptions(enabled = true) {
  return useQuery({ queryKey: ['member', 'options'], queryFn: fetchMemberOptions, staleTime: 60_000, enabled })
}

export function useCreateDeal() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateDealRequest) => createDeal(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: dealKeys.all }),
  })
}

/**
 * 단건 변경 묶음 — 전부 version을 실어 보내고(낙관적 락), 성공하면 상세·목록을 함께 무효화한다.
 * 409 STALE_VERSION은 화면이 ErrorCallout onRetry로 재조회한다 (10 §6.3).
 */
export function useDealMutations(id: string) {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: dealKeys.all })

  const update = useMutation({ mutationFn: (body: UpdateDealRequest) => updateDeal(id, body), onSuccess: refresh })
  const advance = useMutation({ mutationFn: (body: StageMoveRequest) => advanceDeal(id, body), onSuccess: refresh })
  const revert = useMutation({ mutationFn: (body: StageMoveRequest) => revertDeal(id, body), onSuccess: refresh })
  const lose = useMutation({
    mutationFn: (body: LoseDealRequest) => loseDeal(id, body),
    // 실패 처리는 견적·링크까지 만료시킨다 — 견적 캐시도 비운다 (DL-10)
    onSuccess: () => {
      refresh()
      queryClient.invalidateQueries({ queryKey: ['quote'] })
    },
  })
  const reopen = useMutation({ mutationFn: (body: StageMoveRequest) => reopenDeal(id, body), onSuccess: refresh })
  const changeAssignee = useMutation({ mutationFn: (body: ChangeAssigneeRequest) => changeDealAssignee(id, body), onSuccess: refresh })
  const remove = useMutation({ mutationFn: () => deleteDeal(id), onSuccess: refresh })

  return { update, advance, revert, lose, reopen, changeAssignee, remove }
}
