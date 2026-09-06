import { Navigate } from 'react-router'
import type { ReactNode } from 'react'
import { isAdmin, useSession } from './session'

/**
 * 기업 관리자 전용 화면 가드 (09 매트릭스 — 구성원 관리 · 감사 로그 · 담당자별 실적).
 *
 * 메뉴는 이미 숨겨져 있으므로(10 §3.2) 여기 오는 것은 직접 입력한 주소다.
 * 403을 보여주지 않고 홈으로 보낸다 — "권한 없는 기능은 비활성화가 아니라 숨긴다".
 */
export function RequireAdmin({ children }: { children: ReactNode }) {
  const session = useSession()
  if (!isAdmin(session)) return <Navigate to="/" replace />
  return children
}
