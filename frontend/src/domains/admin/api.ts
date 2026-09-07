import { adminApi, setAdminAccessToken } from '../../shared/api/client'
import type {
  ApplicationResponse, CompanyResponse, LoginRequest, LoginResponse, PageParams, PageResponse,
  RejectApplicationRequest, SuspendCompanyRequest,
} from '../../shared/api/types'
import type { ApplicationStatus } from '../../shared/ui/status'

/**
 * 플랫폼 관리자 API — 07-api-spec.md §A 온보딩 (`/admin/api/v1`) · 08 §A.
 * 구성원 세션(`api`)과 완전히 분리된 `adminApi`만 쓴다 (AU-08 · Q-28 — 쿠키 `2jo_admin_rt`).
 * `/admin/api`에는 고객사·Deal·견적 리소스가 존재하지 않는다 (ON-11).
 */

/** POST /admin/api/v1/auth/login — 응답은 구성원과 같은 LoginResponse(companyName은 null) */
export async function adminLogin(body: LoginRequest) {
  const { data } = await adminApi.post<LoginResponse>('/auth/login', body)
  setAdminAccessToken(data.accessToken)
  return data
}

/** POST /admin/api/v1/auth/logout — refresh 행 폐기(LOGOUT) + 쿠키 삭제 */
export async function adminLogout() {
  await adminApi.post('/auth/logout')
}

/** GET /admin/api/v1/applications?status= — 신청 목록 (ON-03). 목록 공통 규칙(Q-39)대로 PageResponse로 가정 */
export async function fetchApplications(params: PageParams & { status?: ApplicationStatus } = {}) {
  const { data } = await adminApi.get<PageResponse<ApplicationResponse>>('/applications', { params })
  return data
}

/** GET /admin/api/v1/applications/{id} */
export async function fetchApplication(id: string) {
  const { data } = await adminApi.get<ApplicationResponse>(`/applications/${id}`)
  return data
}

/** POST /admin/api/v1/applications/{id}/approve — 회사 생성 + 기업 관리자 계정 + 설정 링크 메일 (ON-04·06·07, NT-13) */
export async function approveApplication(id: string) {
  const { data } = await adminApi.post<ApplicationResponse>(`/applications/${id}/approve`)
  return data
}

/** POST /admin/api/v1/applications/{id}/reject — 사유 필수 (ON-05·14) */
export async function rejectApplication(id: string, body: RejectApplicationRequest) {
  const { data } = await adminApi.post<ApplicationResponse>(`/applications/${id}/reject`, body)
  return data
}

/** GET /admin/api/v1/companies — 회사 목록 · 이용 현황 (ON-12, Q-41: memberCount만) */
export async function fetchCompanies(params: PageParams = {}) {
  const { data } = await adminApi.get<PageResponse<CompanyResponse>>('/companies', { params })
  return data
}

/** POST /admin/api/v1/companies/{id}/suspend — 정지 (ON-08·09, Q-27) */
export async function suspendCompany(id: string, body: SuspendCompanyRequest) {
  const { data } = await adminApi.post<CompanyResponse>(`/companies/${id}/suspend`, body)
  return data
}

/** POST /admin/api/v1/companies/{id}/reactivate — 정지 해제 (ON-10) */
export async function reactivateCompany(id: string) {
  const { data } = await adminApi.post<CompanyResponse>(`/companies/${id}/reactivate`)
  return data
}
