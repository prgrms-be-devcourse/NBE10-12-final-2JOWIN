import { api, publicApi, setAccessToken } from '../../shared/api/client'
import type { LoginResponse } from '../../shared/api/types'

/** 인증 API — 07-api-spec.md §A. 호출은 이 파일 안에서만 한다 (12 §8). */

export async function login(email: string, password: string, rememberMe: boolean) {
  const { data } = await api.post<LoginResponse>('/auth/login', { email, password, rememberMe })
  setAccessToken(data.accessToken)
  return data
}

export async function fetchMe() {
  const { data } = await api.get<LoginResponse>('/me')
  return data
}

export async function logout() {
  await api.post('/auth/logout')
}

// ── 초대 · 비밀번호 (public — 토큰이 곧 인증)

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
