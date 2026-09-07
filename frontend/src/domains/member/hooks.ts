import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CreateInvitationRequest, DeactivateMemberRequest, PageParams } from '../../shared/api/types'
import type { InvitationStatus, Role } from '../../shared/ui/status'
import {
  cancelInvitation, changeMemberRole, createInvitation, deactivateMember, fetchInvitations, fetchMemberOptions, fetchMembers,
  reactivateMember, resendInvitation,
} from './api'

/** 구성원·초대 Query 훅 — queryKey 규약 `[도메인, 리소스, 파라미터]` (12 §6.4) */

export type InvitationListParams = PageParams & { status?: InvitationStatus }

export const memberKeys = {
  all: ['member'] as const,
  list: (params: PageParams) => ['member', 'list', params] as const,
  options: ['member', 'options'] as const,
  invitations: (params: InvitationListParams) => ['member', 'invitations', params] as const,
}

export function useMemberList(params: PageParams) {
  return useQuery({ queryKey: memberKeys.list(params), queryFn: () => fetchMembers(params), placeholderData: (previous) => previous })
}

/** 활성 구성원 이름·id — 이관 대상·담당자 배정 선택지 (DL-04) */
export function useMemberOptions(enabled = true) {
  return useQuery({ queryKey: memberKeys.options, queryFn: fetchMemberOptions, enabled, staleTime: 60_000 })
}

export function useInvitationList(params: InvitationListParams) {
  return useQuery({ queryKey: memberKeys.invitations(params), queryFn: () => fetchInvitations(params), placeholderData: (previous) => previous })
}

/** 구성원 상태·역할 변경 — 목록과 선택지(활성만)를 함께 무효화한다 */
export function useMemberMutations() {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: memberKeys.all })

  const changeRole = useMutation({
    mutationFn: ({ id, role }: { id: string; role: Role }) => changeMemberRole(id, { role }),
    onSuccess: refresh,
  })
  const deactivate = useMutation({
    mutationFn: ({ id, body }: { id: string; body: DeactivateMemberRequest }) => deactivateMember(id, body),
    onSuccess: refresh,
  })
  const reactivate = useMutation({ mutationFn: (id: string) => reactivateMember(id), onSuccess: refresh })

  return { changeRole, deactivate, reactivate }
}

export function useInvitationMutations() {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: ['member', 'invitations'] })

  const create = useMutation({ mutationFn: (body: CreateInvitationRequest) => createInvitation(body), onSuccess: refresh })
  const resend = useMutation({ mutationFn: (id: string) => resendInvitation(id), onSuccess: refresh })
  const cancel = useMutation({ mutationFn: (id: string) => cancelInvitation(id), onSuccess: refresh })

  return { create, resend, cancel }
}
