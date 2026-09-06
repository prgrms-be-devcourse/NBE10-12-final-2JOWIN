import { api, publicApi, setAccessToken } from '../../shared/api/client'
import type {
  AcceptInvitationRequest, ApplicationResponse, ChangePasswordRequest, CreateApplicationRequest,
  ExecutePasswordResetRequest, InvitationInfoResponse, LoginRequest, LoginResponse, MeResponse,
  NotificationSettingResponse, RequestPasswordResetRequest, UpdateMeRequest, UpdateNotificationSettingsRequest,
} from '../../shared/api/types'

/** 인증·계정 API — 07-api-spec.md §A (AU · NT-07 · ON-01). 호출은 이 파일 안에서만 한다 (12 §8). */

export async function login(body: LoginRequest) {
  const { data } = await api.post<LoginResponse>('/auth/login', body)
  setAccessToken(data.accessToken)
  return data
}

/** GET /api/v1/me — MeResponse (member/dto). LoginResponse와 형태가 다르다 */
export async function fetchMe() {
  const { data } = await api.get<MeResponse>('/me')
  return data
}

/** PATCH /api/v1/me — 200 MeResponse (07 v1.6.10) */
export async function updateMe(body: UpdateMeRequest) {
  const { data } = await api.patch<MeResponse>('/me', body)
  return data
}

/** POST /api/v1/me/password — 204 · 성공 시 전 세션 폐기 → 본인도 재로그인 (AU-04) */
export async function changePassword(body: ChangePasswordRequest) {
  await api.post('/me/password', body)
}

export async function logout() {
  await api.post('/auth/logout')
}

/** GET /api/v1/me/notification-settings — 메일 채널만 (NT-07, Q-23) */
export async function fetchNotificationSettings() {
  const { data } = await api.get<NotificationSettingResponse>('/me/notification-settings')
  return data
}

/** PUT /api/v1/me/notification-settings — 전체 교체 */
export async function updateNotificationSettings(body: UpdateNotificationSettingsRequest) {
  const { data } = await api.put<NotificationSettingResponse>('/me/notification-settings', body)
  return data
}

// ── 초대 · 비밀번호 · 사용 신청 (public — 토큰이 곧 인증 / 비로그인)

/** GET /public/api/v1/invitations/{token} — InvitationInfoResponse (08 §A: companyName·email·role) */
export async function fetchInvitation(token: string) {
  const { data } = await publicApi.get<InvitationInfoResponse>(`/invitations/${token}`)
  return data
}

export async function acceptInvitation(token: string, body: AcceptInvitationRequest) {
  await publicApi.post(`/invitations/${token}/accept`, body)
}

/** POST /public/api/v1/auth/password-reset-request — 미가입 이메일도 202 (SC-09 인증 확장) */
export async function requestPasswordReset(body: RequestPasswordResetRequest) {
  await publicApi.post('/auth/password-reset-request', body)
}

/** POST /public/api/v1/auth/password-reset — ExecutePasswordResetRequest(token · newPassword) · 204 */
export async function resetPassword(body: ExecutePasswordResetRequest) {
  await publicApi.post('/auth/password-reset', body)
}

/** POST /public/api/v1/applications — 사용 신청 (ON-01·02) */
export async function createApplication(body: CreateApplicationRequest) {
  const { data } = await publicApi.post<ApplicationResponse>('/applications', body)
  return data
}
