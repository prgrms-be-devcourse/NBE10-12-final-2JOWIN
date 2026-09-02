import { useQuery } from '@tanstack/react-query'
import { Flex, Spinner } from '@radix-ui/themes'
import { Navigate } from 'react-router'
import type { ReactNode } from 'react'
import { SessionContext, type Session } from './session'
import { fetchMe } from './api'

/**
 * 인증 가드 — 세션이 없으면 로그인으로 보낸다.
 *
 * **세션을 화면에 심어두지 않고 `GET /api/v1/me`로 받아온다.** 목이 켜져 있으면 MSW가,
 * 실 API로 바꾸면 서버가 답하므로 **이 코드는 전환할 때 고치지 않는다** (12-frontend-plan.md §5.1).
 *
 * TODO(E · A 연동): A의 refresh가 붙으면 부팅 시 `restoreSession()`을 먼저 부른다 —
 * access는 메모리에만 있어 새로고침하면 사라지고, 쿠키가 살아 있으면 재발급으로 복구된다(§6.3-7).
 * 지금은 목이 항상 응답하므로 /me 하나로 충분하다.
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
