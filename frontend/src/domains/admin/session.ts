import { createContext, useContext } from 'react'

/**
 * 플랫폼 관리자 세션 — 구성원 세션(`app/session`)과 별개다 (AU-08 · 09 v1.6.3 각주).
 *
 * 관리자에게는 `/me`가 없다(09 "본인 계정" 행 — 세션 관리 엔드포인트만 존재). 그래서 표시용 정보는
 * 로그인 응답(LoginResponse.name·memberId)을 여기에 들고, 새로고침 뒤에는 refresh가 성공했을 때
 * sessionStorage에서 되살린다. **토큰은 절대 저장하지 않는다** — 메모리(client.ts)에만 있다.
 */

export interface AdminSession {
  memberId: string
  name: string
}

const STORAGE_KEY = '2jo.admin.profile'

export const AdminSessionContext = createContext<AdminSession | null>(null)

export function useAdminSession(): AdminSession {
  const session = useContext(AdminSessionContext)
  if (!session) throw new Error('AdminSessionContext 밖에서 useAdminSession을 불렀다 — AdminAuthGuard 안에서만 쓴다')
  return session
}

/** 이름·id만 — 비밀이 아닌 표시용 정보 */
export function saveAdminProfile(profile: AdminSession) {
  try {
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(profile))
  } catch {
    // 저장 불가 환경 — 새로고침 시 이름만 비어 보인다
  }
}

export function loadAdminProfile(): AdminSession | null {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as AdminSession) : null
  } catch {
    return null
  }
}

export function clearAdminProfile() {
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // 무시
  }
}
