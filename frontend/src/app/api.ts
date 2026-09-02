import { api, publicApi, setAccessToken } from '../shared/api/client'
import type {
  ApproveQuoteRequest, CreateInquiryRequest, LoginResponse, PublicQuoteResponse, RejectQuoteRequest,
} from '../shared/api/types'

/**
 * E가 맡은 화면들의 API 호출 (12-frontend-plan.md §8 — 호출은 api 모듈 안에서만).
 *
 * 도메인 화면은 각자 `domains/{도메인}/api.ts`를 쓴다. 여기 있는 것은 **소유자 없는 화면**
 * (로그인 · 고객 열람 · 초대 수락 · 비밀번호 설정) 몫이다.
 */

// ── 인증
export async function login(email: string, password: string, rememberMe: boolean) {
  const { data } = await api.post<LoginResponse>('/auth/login', { email, password, rememberMe })
  setAccessToken(data.accessToken) // access는 메모리에만 (§6.3-7)
  return data
}

export async function fetchMe() {
  const { data } = await api.get<LoginResponse>('/me')
  return data
}

export async function logout() {
  await api.post('/auth/logout')
}

// ── 고객 열람 (public — 토큰이 곧 인증)
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

// ── 초대 · 비밀번호
export interface InvitationInfo {
  companyName: string
  email: string
  role: 'COMPANY_ADMIN' | 'SALES_REP'
  expiresAt: string
}

export async function fetchInvitation(token: string) {
  const { data } = await publicApi.get<InvitationInfo>(`/invitations/${token}`)
  return data
}

export async function acceptInvitation(token: string, name: string, password: string) {
  await publicApi.post(`/invitations/${token}/accept`, { name, password })
}

export async function resetPassword(token: string | null, password: string) {
  await publicApi.post('/auth/password-reset', { token, password })
}
