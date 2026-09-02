import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CreateContactRequest, CreateCustomerRequest, UpdateContactRequest, UpdateCustomerRequest } from '../../shared/api/types'
import {
  createContact, createCustomer, deleteContact, deleteCustomer, fetchCustomer, fetchCustomerActivities, fetchCustomers,
  setPrimaryContact, updateContact, updateCustomer, type CustomerListParams,
} from './api'

/** 고객사 Query 훅 — queryKey 규약 `[도메인, 리소스, 파라미터]` (12 §6.4) */

export const customerKeys = {
  all: ['customer'] as const,
  list: (params: CustomerListParams) => ['customer', 'list', params] as const,
  detail: (id: string) => ['customer', 'detail', id] as const,
  activities: (id: string) => ['customer', 'activities', id] as const,
}

export function useCustomerList(params: CustomerListParams) {
  return useQuery({
    queryKey: customerKeys.list(params),
    queryFn: () => fetchCustomers(params),
    placeholderData: (previous) => previous, // 검색어를 바꿔도 표가 깜빡이지 않는다
  })
}

export function useCustomerDetail(id: string) {
  return useQuery({ queryKey: customerKeys.detail(id), queryFn: () => fetchCustomer(id), retry: false })
}

export function useCustomerActivities(id: string) {
  return useQuery({ queryKey: customerKeys.activities(id), queryFn: () => fetchCustomerActivities(id) })
}

export function useCreateCustomer() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: CreateCustomerRequest) => createCustomer(body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: customerKeys.all }),
  })
}

export function useUpdateCustomer(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateCustomerRequest) => updateCustomer(id, body),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: customerKeys.all }),
  })
}

export function useDeleteCustomer() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => deleteCustomer(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: customerKeys.all }),
  })
}

/** 담당자 변경은 상세 응답에 포함되므로 상세만 무효화한다 */
export function useContactMutations(customerId: string) {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: customerKeys.detail(customerId) })

  const create = useMutation({ mutationFn: (body: CreateContactRequest) => createContact(customerId, body), onSuccess: refresh })
  const update = useMutation({
    mutationFn: ({ contactId, body }: { contactId: string; body: UpdateContactRequest }) => updateContact(customerId, contactId, body),
    onSuccess: refresh,
  })
  const remove = useMutation({ mutationFn: (contactId: string) => deleteContact(customerId, contactId), onSuccess: refresh })
  const setPrimary = useMutation({ mutationFn: (contactId: string) => setPrimaryContact(customerId, contactId), onSuccess: refresh })

  return { create, update, remove, setPrimary }
}
