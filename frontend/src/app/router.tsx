import { createBrowserRouter } from 'react-router'
import { Placeholder } from '../shared/ui/Placeholder'
import { AuthGuard } from './AuthGuard'
import { AppLayout } from './layouts/AppLayout'
import { CustomerLayout } from './layouts/CustomerLayout'
import { UiGallery } from './UiGallery'
import { LoginPage } from './pages/LoginPage'
import { QuoteViewPage } from './pages/quote-view/QuoteViewPage'
import { PasswordSetupPage } from './pages/PasswordSetupPage'
import { InviteAcceptPage } from './pages/InviteAcceptPage'

/**
 * 라우팅 3분리 (12-frontend-plan.md §6.2 · 10-screen-design.md §1)
 *
 * | 경로              | 대상          | 레이아웃                                 |
 * |-------------------|--------------|------------------------------------------|
 * | /                 | 구성원 앱     | 상단 내비 + 인증 가드                     |
 * | /admin            | 플랫폼 관리자 | 별도 인증 가드 · 단순 레이아웃            |
 * | /q/:token 등      | 고객·초대     | 레이아웃 없음 — 앱 UI를 상속하지 않는다    |
 *
 * **도메인 화면은 각 담당이 붙인다** — 아래 Placeholder 자리에
 * `domains/{도메인}/pages/*`를 import해 교체하면 된다 (12-frontend-plan.md §3).
 */
export const router = createBrowserRouter([
  // ── 구성원 앱
  {
    path: '/',
    element: (
      <AuthGuard>
        <AppLayout />
      </AuthGuard>
    ),
    children: [
      { index: true, element: <Placeholder title="대시보드" note="D 담당 (DB-01~08)" /> },
      { path: 'customers', element: <Placeholder title="고객사 목록" note="B 담당 (CU)" /> },
      { path: 'deals', element: <Placeholder title="딜 보드" note="C 담당 (DL)" /> },
      { path: 'quotes', element: <Placeholder title="견적 목록" note="C 담당 (QT)" /> },
      { path: 'orders', element: <Placeholder title="주문 목록" note="C 담당 (OD)" /> },
      { path: 'products', element: <Placeholder title="상품 카탈로그" note="B 담당 (PR)" /> },
      { path: 'members', element: <Placeholder title="구성원 관리" note="A 담당 (MB)" /> },
      { path: 'audit-logs', element: <Placeholder title="감사 로그" note="B 담당 (AC-11)" /> },
      // 공통 컴포넌트 갤러리 — 개발 참고용
      { path: '_ui', element: <UiGallery /> },
    ],
  },

  // ── 로그인 (레이아웃 없음)
  { path: '/login', element: <LoginPage /> },

  // ── 비로그인 · 계정 (자체 레이아웃 — center-page)
  { path: '/invite/:token', element: <InviteAcceptPage /> },
  { path: '/password-reset', element: <PasswordSetupPage /> },

  // ── 플랫폼 관리자
  { path: '/admin', element: <Placeholder title="관리자 로그인" note="A 담당 (AU-08)" /> },

  // ── 비로그인 · 고객 (앱 레이아웃 상속 금지)
  {
    element: <CustomerLayout />,
    children: [
      { path: '/q/:token', element: <QuoteViewPage /> },


    ],
  },
])
