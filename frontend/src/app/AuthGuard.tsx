import { useQuery } from '@tanstack/react-query'
import { Flex, Spinner } from '@radix-ui/themes'
import { Navigate } from 'react-router'
import type { ReactNode } from 'react'
import { SessionContext, type Session } from './session'
import { fetchMe } from '../domains/auth/api'

/**
 * 인증 가드 — 세션이 없으면 로그인으로 보낸다.
 *
 * 세션은 `GET /api/v1/me`로 받아온다. 새로고침 직후에는 access가 없어 /me가 401을 받지만,
 * 클라이언트 인터셉터가 refresh 쿠키로 재발급 후 재시도하므로 별도 복구 단계가 없다 (12 §6.3-7).
 * refresh까지 실패하면 여기서 로그인으로 보낸다 (AU-12).
 */
export function AuthGuard({ children }: { children: ReactNode }) {
  const { data, isPending, isError } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: fetchMe,
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

  // 세션이 없으면 로그인 화면 — REFRESH_TOKEN_NOT_ACTIVE의 화면 쪽 대응 (AU-12)
  if (isError || !data) return <Navigate to="/login" replace />

  const session: Session = {
    memberId: data.memberId,
    name: data.name,
    role: data.role,
    companyName: data.companyName,
  }

  return <SessionContext value={session}>{children}</SessionContext>
}
