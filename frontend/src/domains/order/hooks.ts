import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { OrderScheduleRequest } from '../../shared/api/types'
import { fetchOrder, fetchOrders, scheduleOrder, type OrderListParams } from './api'

/** 주문 Query 훅 — queryKey 규약 `[도메인, 리소스, 파라미터]` (12 §6.4) */

export const orderKeys = {
  all: ['order'] as const,
  list: (params: OrderListParams) => ['order', 'list', params] as const,
  detail: (id: string) => ['order', 'detail', id] as const,
}

export function useOrderList(params: OrderListParams) {
  return useQuery({ queryKey: orderKeys.list(params), queryFn: () => fetchOrders(params), placeholderData: (previous) => previous })
}

export function useOrderDetail(id: string) {
  return useQuery({ queryKey: orderKeys.detail(id), queryFn: () => fetchOrder(id), retry: false })
}

export function useScheduleOrder(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: OrderScheduleRequest) => scheduleOrder(id, body),
    onSuccess: (data) => {
      queryClient.setQueryData(orderKeys.detail(id), data)
      queryClient.invalidateQueries({ queryKey: orderKeys.all })
    },
  })
}
