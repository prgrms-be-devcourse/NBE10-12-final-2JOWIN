import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CreateQuoteRequest, ResendViewTokenRequest, SendQuoteRequest, UpdateQuoteRequest } from '../../shared/api/types'
import { fetchCustomer } from '../customer/api'
import {
  cloneQuote, convertToOrder, createQuote, expireViewToken, fetchDealOfQuote, fetchQuote, fetchQuotePreview, fetchQuotes,
  resendViewToken, sendQuote, updateQuote, withdrawQuote, type QuoteListParams,
} from './api'

/** 견적 Query 훅 — queryKey 규약 `[도메인, 리소스, 파라미터]` (12 §6.4) */

export const quoteKeys = {
  all: ['quote'] as const,
  list: (params: QuoteListParams) => ['quote', 'list', params] as const,
  detail: (id: string) => ['quote', 'detail', id] as const,
  preview: (id: string) => ['quote', 'preview', id] as const,
}

export function useQuoteList(params: QuoteListParams) {
  return useQuery({ queryKey: quoteKeys.list(params), queryFn: () => fetchQuotes(params), placeholderData: (previous) => previous })
}

export function useQuoteDetail(id: string) {
  return useQuery({ queryKey: quoteKeys.detail(id), queryFn: () => fetchQuote(id), retry: false })
}

export function useQuotePreview(id: string) {
  return useQuery({ queryKey: quoteKeys.preview(id), queryFn: () => fetchQuotePreview(id), retry: false })
}

/** 발송·재발송 모달의 수신인 후보 — 딜의 고객사 담당자 (AP-01, CONTACT_NOT_IN_CUSTOMER 방지) */
export function useQuoteRecipients(dealId: string | undefined) {
  return useQuery({
    queryKey: ['quote', 'recipients', dealId],
    queryFn: async () => {
      const deal = await fetchDealOfQuote(dealId!)
      const customer = await fetchCustomer(deal.customerId)
      return { customerName: customer.name, contacts: customer.contacts }
    },
    enabled: Boolean(dealId),
  })
}

/** 견적이 바뀌면 딜 상세의 견적 요약·대시보드 응답 대기도 함께 낡는다 */
function useInvalidateQuote() {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: quoteKeys.all })
    queryClient.invalidateQueries({ queryKey: ['deal'] })
    queryClient.invalidateQueries({ queryKey: ['dashboard'] })
  }
}

export function useCreateQuote() {
  const invalidate = useInvalidateQuote()
  return useMutation({ mutationFn: (body: CreateQuoteRequest) => createQuote(body), onSuccess: invalidate })
}

export function useUpdateQuote(id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (body: UpdateQuoteRequest) => updateQuote(id, body),
    // 저장 응답이 서버 계산값이므로 캐시를 응답으로 바로 덮어쓴다 (QT-08·22)
    onSuccess: (data) => {
      queryClient.setQueryData(quoteKeys.detail(id), data)
      queryClient.invalidateQueries({ queryKey: quoteKeys.preview(id) })
      queryClient.invalidateQueries({ queryKey: ['deal'] })
    },
  })
}

/** 상태 전이 액션 묶음 — 전이표 §6·§7 */
export function useQuoteActions(id: string) {
  const invalidate = useInvalidateQuote()
  const queryClient = useQueryClient()
  const send = useMutation({ mutationFn: (body: SendQuoteRequest) => sendQuote(id, body), onSuccess: invalidate })
  const withdraw = useMutation({ mutationFn: () => withdrawQuote(id), onSuccess: invalidate })
  const clone = useMutation({ mutationFn: () => cloneQuote(id), onSuccess: invalidate })
  const resend = useMutation({ mutationFn: (body: ResendViewTokenRequest) => resendViewToken(id, body), onSuccess: invalidate })
  const expire = useMutation({ mutationFn: () => expireViewToken(id), onSuccess: invalidate })
  const convert = useMutation({
    mutationFn: () => convertToOrder(id),
    onSuccess: () => {
      invalidate()
      queryClient.invalidateQueries({ queryKey: ['order'] })
    },
  })
  return { send, withdraw, clone, resend, expire, convert }
}
