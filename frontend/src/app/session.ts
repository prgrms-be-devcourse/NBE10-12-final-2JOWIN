import { createContext, useContext } from 'react'

/**
 * 로그인 세션 — 화면이 역할을 읽는 단일 지점. 값은 AuthGuard가 /me 응답으로 채운다.
 *
 * 권한 없는 기능은 비활성화가 아니라 숨긴다 (10-screen-design.md §3.2).
 * 눌러서 403을 만나게 하지 않는다 — 서버는 그래도 검사한다(FORBIDDEN).
 */

export type Role = 'COMPANY_ADMIN' | 'SALES_REP'

export interface Session {
  memberId: string
  name: string
  role: Role
  companyName: string
}

export const SessionContext = createContext<Session | null>(null)

export function useSession(): Session {
  const session = useContext(SessionContext)
  if (!session) throw new Error('SessionContext 밖에서 useSession을 불렀다 — 인증 가드 안에서만 쓴다')
  return session
}

/** 기업 관리자 전용 UI인지 — 메뉴·버튼 노출 판정 (§3.2) */
export const isAdmin = (session: Session): boolean => session.role === 'COMPANY_ADMIN'
