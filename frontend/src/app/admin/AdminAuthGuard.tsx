import { useQuery } from '@tanstack/react-query'
import { Flex, Spinner } from '@radix-ui/themes'
import { Navigate } from 'react-router'
import type { ReactNode } from 'react'
import { getAdminAccessToken, refreshAdminSession } from '../../shared/api/client'
import { AdminSessionContext, loadAdminProfile, type AdminSession } from '../../domains/admin/session'

/**
 * 플랫폼 관리자 인증 가드 (AU-08).
 *
 * 관리자에게는 `/me`가 없다(09 "본인 계정" 행 — 로그인·재발급·로그아웃만 존재). 그래서 세션 확인은
 *  1. 메모리에 access가 있으면 통과 (로그인 직후)
 *  2. 없으면(새로고침) `POST /admin/api/v1/auth/refresh`를 한 번 — 쿠키 `2jo_admin_rt`가 곧 자격 증명
 *  3. 실패하면 `/admin/login`으로
 * 표시용 이름은 로그인 때 저장해 둔 sessionStorage 프로필에서 되살린다 (토큰은 저장하지 않는다).
 */
export function AdminAuthGuard({ children }: { children: ReactNode }) {
  const { data, isPending, isError } = useQuery({
    queryKey: ['admin', 'session'],
    queryFn: async (): Promise<AdminSession> => {
      if (!getAdminAccessToken()) await refreshAdminSession()
      return loadAdminProfile() ?? { memberId: '', name: '플랫폼 관리자' }
    },
    retry: false,
    staleTime: Infinity,
  })

  if (isPending) {
    return (
      <Flex align="center" justify="center" height="100%">
        <Spinner size="3" />
      </Flex>
    )
  }

  if (isError || !data) return <Navigate to="/admin/login" replace />

  return <AdminSessionContext value={data}>{children}</AdminSessionContext>
}
