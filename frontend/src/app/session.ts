import { createContext, useContext } from 'react'
import type { MeResponse } from '../shared/api/types'
import type { Role } from '../shared/ui/status'

/**
 * 로그인 세션 — 화면이 역할을 읽는 단일 지점. 값은 AuthGuard가 `GET /api/v1/me`(MeResponse)로 채운다.
 *
 * 권한 없는 기능은 비활성화가 아니라 숨긴다 (10-screen-design.md §3.2).
 * 눌러서 403을 만나게 하지 않는다 — 서버는 그래도 검사한다(FORBIDDEN).
 *
 * 역할은 `boundary/Role`의 2종뿐이다. 플랫폼 관리자는 이 세션이 아니라 `admin/session`이다 (09 v1.6.3 각주).
 */

export type { Role }

/** MeResponse 그대로 — 화면이 필요한 것만 골라 쓴다 */
export type Session = MeResponse

/** 세션 쿼리 키 — PATCH /me 뒤 헤더·프로필 표시를 갱신할 때 이 키를 무효화한다 (07 v1.6.10) */
export const ME_QUERY_KEY = ['auth', 'me'] as const

export const SessionContext = createContext<Session | null>(null)

export function useSession(): Session {
  const session = useContext(SessionContext)
  if (!session) throw new Error('SessionContext 밖에서 useSession을 불렀다 — 인증 가드 안에서만 쓴다')
  return session
}

/** 기업 관리자 전용 UI인지 — 메뉴·버튼 노출 판정 (§3.2 · 09 매트릭스 ⭕/✕ 열) */
export const isAdmin = (session: Pick<Session, 'role'>): boolean => session.role === 'COMPANY_ADMIN'

/**
 * 회사 전체 범위(COMPANY_ALL)인지 — 영업 담당자는 본인 담당(OWNED_ONLY)만 본다 (boundary/AccessScope).
 * 담당자 필터·담당자별 실적처럼 "회사 전체가 전제인 UI"의 노출 판정에 쓴다.
 */
export const hasCompanyWideScope = isAdmin
