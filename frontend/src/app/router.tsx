import { createBrowserRouter } from 'react-router'
import { Placeholder } from '../shared/ui/Placeholder'
import { AuthGuard } from './AuthGuard'
import { AppLayout } from './layouts/AppLayout'
import { CustomerLayout } from './layouts/CustomerLayout'
import { LoginPage } from '../domains/auth/pages/LoginPage'
import { QuoteViewPage } from '../domains/quote/pages/QuoteViewPage'
import { PasswordSetupPage } from '../domains/auth/pages/PasswordSetupPage'
import { InviteAcceptPage } from '../domains/auth/pages/InviteAcceptPage'
import { CustomerListPage } from '../domains/customer/pages/CustomerListPage'
import { CustomerDetailPage } from '../domains/customer/pages/CustomerDetailPage'

/**
 * 라우팅 3분리 (12-frontend-plan.md §6.2 · 10-screen-design.md §1)
 *
 * | 경로              | 대상          | 레이아웃                                 |
 * |-------------------|--------------|------------------------------------------|
 * | /                 | 구성원 앱     | 사이드바 + 인증 가드                      |
 * | /admin            | 플랫폼 관리자 | 별도 인증 가드 · 단순 레이아웃            |
 * | /q/:token 등      | 고객·초대     | 레이아웃 없음 — 앱 UI를 상속하지 않는다    |
 *
 * 도메인 화면은 각 담당이 Placeholder를 `domains/{도메인}/pages/*`로 교체한다 (12 §3).
 */
// 공통 컴포넌트 갤러리 — 개발 환경에서만 등록한다. 픽스처를 import하므로 프로덕션 번들에서 제외한다
// (import.meta.env.DEV가 false로 치환되면 동적 import까지 함께 제거된다)
const devRoutes = import.meta.env.DEV
  ? [{ path: '_ui', lazy: async () => ({ Component: (await import('./UiGallery')).UiGallery }) }]
  : []

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
      // 고객사 — 구성원 앱 화면의 예제 (10 §5.9)
      { path: 'customers', element: <CustomerListPage /> },
      { path: 'customers/:id', element: <CustomerDetailPage /> },
      { path: 'deals', element: <Placeholder title="딜 보드" note="C 담당 (DL)" /> },
      { path: 'quotes', element: <Placeholder title="견적 목록" note="C 담당 (QT)" /> },
      { path: 'orders', element: <Placeholder title="주문 목록" note="C 담당 (OD)" /> },
      { path: 'products', element: <Placeholder title="상품 카탈로그" note="B 담당 (PR)" /> },
      { path: 'members', element: <Placeholder title="구성원 관리" note="A 담당 (MB)" /> },
      { path: 'audit-logs', element: <Placeholder title="감사 로그" note="B 담당 (AC-11)" /> },
      ...devRoutes,
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
