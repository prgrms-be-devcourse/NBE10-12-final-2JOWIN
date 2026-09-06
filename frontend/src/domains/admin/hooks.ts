import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { PageParams, RejectApplicationRequest, SuspendCompanyRequest } from '../../shared/api/types'
import type { ApplicationStatus } from '../../shared/ui/status'
import {
  approveApplication, fetchApplication, fetchApplications, fetchCompanies, reactivateCompany, rejectApplication, suspendCompany,
} from './api'

/** 관리자 Query 훅 — queryKey 규약 `['admin', 리소스, 파라미터]` (12 §6.4) */

export const adminKeys = {
  applications: ['admin', 'applications'] as const,
  applicationList: (params: PageParams & { status?: ApplicationStatus }) => ['admin', 'applications', 'list', params] as const,
  application: (id: string) => ['admin', 'applications', 'detail', id] as const,
  companies: ['admin', 'companies'] as const,
  companyList: (params: PageParams) => ['admin', 'companies', 'list', params] as const,
}

export function useApplicationList(params: PageParams & { status?: ApplicationStatus }) {
  return useQuery({
    queryKey: adminKeys.applicationList(params),
    queryFn: () => fetchApplications(params),
    placeholderData: (previous) => previous,
  })
}

export function useApplication(id: string) {
  return useQuery({ queryKey: adminKeys.application(id), queryFn: () => fetchApplication(id), retry: false })
}

/** 승인·반려 — 신청 목록·상세와 회사 목록(승인 시 회사가 생긴다)을 함께 무효화한다 */
export function useApplicationDecision(id: string) {
  const queryClient = useQueryClient()
  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: adminKeys.applications })
    queryClient.invalidateQueries({ queryKey: adminKeys.companies })
  }
  const approve = useMutation({ mutationFn: () => approveApplication(id), onSuccess: refresh })
  const reject = useMutation({ mutationFn: (body: RejectApplicationRequest) => rejectApplication(id, body), onSuccess: refresh })
  return { approve, reject }
}

export function useCompanyList(params: PageParams) {
  return useQuery({
    queryKey: adminKeys.companyList(params),
    queryFn: () => fetchCompanies(params),
    placeholderData: (previous) => previous,
  })
}

export function useCompanyMutations() {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: adminKeys.companies })
  const suspend = useMutation({
    mutationFn: ({ id, body }: { id: string; body: SuspendCompanyRequest }) => suspendCompany(id, body),
    onSuccess: refresh,
  })
  const reactivate = useMutation({ mutationFn: (id: string) => reactivateCompany(id), onSuccess: refresh })
  return { suspend, reactivate }
}
