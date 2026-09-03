import { publicApi } from '../../shared/api/client'
import type { ApproveQuoteRequest, CreateInquiryRequest, PublicQuoteResponse, RejectQuoteRequest } from '../../shared/api/types'

/** 고객 열람 API — 07-api-spec.md §D (public, 토큰이 곧 인증). 구성원용 견적 API는 C 담당이 이 파일에 추가한다. */

export async function fetchPublicQuote(token: string) {
  const { data } = await publicApi.get<PublicQuoteResponse>(`/quotes/${token}`)
  return data
}

export async function approveQuote(token: string, body: ApproveQuoteRequest) {
  await publicApi.post(`/quotes/${token}/approve`, body)
}

export async function rejectQuote(token: string, body: RejectQuoteRequest) {
  await publicApi.post(`/quotes/${token}/reject`, body)
}

export async function createInquiry(token: string, body: CreateInquiryRequest) {
  await publicApi.post(`/quotes/${token}/inquiries`, body)
}
