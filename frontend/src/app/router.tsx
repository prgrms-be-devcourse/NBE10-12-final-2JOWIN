import { createBrowserRouter } from 'react-router'
import { Placeholder } from '../shared/ui/Placeholder'

/**
 * 라우팅 3분리 (12-frontend-plan.md §6.2 · 10-screen-design.md §1)
 *
 * | 경로                          | 대상          | 레이아웃                                  |
 * |------------------------------|--------------|------------------------------------------|
 * | /                            | 구성원 앱     | 상단 내비 + 인증 가드 (TODO: E 1주차)      |
 * | /admin                       | 플랫폼 관리자 | 별도 인증 가드 · 단순 레이아웃             |
 * | /q/:token 등 비로그인         | 고객·초대     | 레이아웃 없음 — 앱 UI를 상속하지 않는다     |
 */
export const router = createBrowserRouter([
  // ── 구성원 앱
  { path: '/', element: <Placeholder title="대시보드" note="공통 기반 세팅 완료 — 화면은 각 도메인 담당이 붙인다" /> },

  // ── 플랫폼 관리자
  { path: '/admin', element: <Placeholder title="관리자 로그인" /> },

  // ── 비로그인 (레이아웃 없음)
  { path: '/q/:token', element: <Placeholder title="견적 열람" /> },
  { path: '/invite/:token', element: <Placeholder title="초대 수락" /> },
  { path: '/password-reset', element: <Placeholder title="비밀번호 설정" /> },
])
