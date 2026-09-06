import { api } from '../../shared/api/client'
import type {
  ChangeRoleRequest, CreateInvitationRequest, DeactivateMemberRequest, InvitationResponse, MemberOptionResponse,
  MemberResponse, PageParams, PageResponse,
} from '../../shared/api/types'
import type { InvitationStatus } from '../../shared/ui/status'

/** 구성원·초대 API — 07-api-spec.md §A (MB). 호출은 이 파일 안에서만 한다 (12 §8). */

/** GET /api/v1/members — 기업 관리자 (MB-07). 07에 필터 파라미터 없음 → 페이지만 */
export async function fetchMembers(params: PageParams = {}) {
  const { data } = await api.get<PageResponse<MemberResponse>>('/members', { params })
  return data
}

/** GET /api/v1/members/options — 전 구성원 · 활성 구성원 이름·id만 (DL-04 배정용) */
export async function fetchMemberOptions() {
  const { data } = await api.get<MemberOptionResponse[]>('/members/options')
  return data
}

/** PATCH /api/v1/members/{id}/role — 마지막 관리자 강등은 422 LAST_ADMIN_PROTECTED (MB-11) */
export async function changeMemberRole(id: string, body: ChangeRoleRequest) {
  const { data } = await api.patch<MemberResponse>(`/members/${id}/role`, body)
  return data
}

/** POST /api/v1/members/{id}/deactivate — 담당 Deal 있으면 transferToMemberId 필수 (MB-14) */
export async function deactivateMember(id: string, body: DeactivateMemberRequest) {
  const { data } = await api.post<MemberResponse>(`/members/${id}/deactivate`, body)
  return data
}

export async function reactivateMember(id: string) {
  const { data } = await api.post<MemberResponse>(`/members/${id}/reactivate`)
  return data
}

/** POST /api/v1/invitations — 초대 발송 (MB-01·02, NT-01) */
export async function createInvitation(body: CreateInvitationRequest) {
  const { data } = await api.post<InvitationResponse>('/invitations', body)
  return data
}

/** GET /api/v1/invitations?status= */
export async function fetchInvitations(params: PageParams & { status?: InvitationStatus } = {}) {
  const { data } = await api.get<PageResponse<InvitationResponse>>('/invitations', { params })
  return data
}

/** POST /api/v1/invitations/{id}/resend — 기존 초대 EXPIRED(RESENT) + 새 행 (MB-06, Q-31) */
export async function resendInvitation(id: string) {
  const { data } = await api.post<InvitationResponse>(`/invitations/${id}/resend`)
  return data
}

/** POST /api/v1/invitations/{id}/cancel (MB-05) */
export async function cancelInvitation(id: string) {
  const { data } = await api.post<InvitationResponse>(`/invitations/${id}/cancel`)
  return data
}
