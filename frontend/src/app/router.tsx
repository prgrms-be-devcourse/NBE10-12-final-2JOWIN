import { createBrowserRouter, Navigate } from 'react-router'
import { AuthGuard } from './AuthGuard'
import { RequireAdmin } from './RequireAdmin'
import { AppLayout } from './layouts/AppLayout'
import { CustomerLayout } from './layouts/CustomerLayout'
import { AdminLayout } from './layouts/AdminLayout'
import { AdminAuthGuard } from './admin/AdminAuthGuard'
import { LoginPage } from '../domains/auth/pages/LoginPage'
import { PasswordSetupPage } from '../domains/auth/pages/PasswordSetupPage'
import { PasswordResetRequestPage } from '../domains/auth/pages/PasswordResetRequestPage'
import { InviteAcceptPage } from '../domains/auth/pages/InviteAcceptPage'
import { ApplyPage } from '../domains/auth/pages/ApplyPage'
import { MePage } from '../domains/auth/pages/MePage'
import { DashboardPage } from '../domains/dashboard/pages/DashboardPage'
import { CustomerListPage } from '../domains/customer/pages/CustomerListPage'
import { CustomerDetailPage } from '../domains/customer/pages/CustomerDetailPage'
import { DealBoardPage } from '../domains/deal/pages/DealBoardPage'
import { DealDetailPage } from '../domains/deal/pages/DealDetailPage'
import { QuoteListPage } from '../domains/quote/pages/QuoteListPage'
import { QuoteDetailPage } from '../domains/quote/pages/QuoteDetailPage'
import { QuotePreviewPage } from '../domains/quote/pages/QuotePreviewPage'
import { QuoteViewPage } from '../domains/quote/pages/QuoteViewPage'
import { OrderListPage } from '../domains/order/pages/OrderListPage'
import { OrderDetailPage } from '../domains/order/pages/OrderDetailPage'
import { ProductListPage } from '../domains/product/pages/ProductListPage'
import { MemberListPage } from '../domains/member/pages/MemberListPage'
import { AuditLogPage } from '../domains/audit/pages/AuditLogPage'
import { NotificationListPage } from '../domains/notification/pages/NotificationListPage'
import { AdminLoginPage } from '../domains/admin/pages/AdminLoginPage'
import { AdminApplicationListPage } from '../domains/admin/pages/AdminApplicationListPage'
import { AdminApplicationDetailPage } from '../domains/admin/pages/AdminApplicationDetailPage'
import { AdminCompanyListPage } from '../domains/admin/pages/AdminCompanyListPage'

/**
 * 라우팅 3분리 (12-frontend-plan.md §6.2 · 10-screen-design.md §1)
 *
 * | 경로              | 대상          | 레이아웃                                 |
 * |-------------------|--------------|------------------------------------------|
 * | /                 | 구성원 앱     | 사이드바 + 인증 가드 (/api/v1)            |
 * | /admin            | 플랫폼 관리자 | 별도 인증 가드 · 단순 레이아웃 (/admin/api/v1) |
 * | /q/:token 등      | 고객·초대     | 레이아웃 없음 — 앱 UI를 상속하지 않는다    |
 *
 * 화면 목록의 근거: 04-user-scenarios.md §7 · 12-frontend-plan.md §3 · 07-api-spec.md 엔드포인트.
 * 기업 관리자 전용 화면(구성원·감사 로그)은 메뉴를 숨기고(10 §3.2), 직접 진입도 RequireAdmin이 막는다.
 */
// 공통 컴포넌트 갤러리 — 개발 환경에서만 등록한다. 픽스처를 import하므로 프로덕션 번들에서 제외한다
// (import.meta.env.DEV가 false로 치환되면 동적 import까지 함께 제거된다)
const devRoutes = import.meta.env.DEV
  ? [{ path: '_ui', lazy: async () => ({ Component: (await import('./UiGallery')).UiGallery }) }]
  : []

export const router = createBrowserRouter([
  // ── 구성원 앱 (/api/v1)
  {
    path: '/',
    element: (
      <AuthGuard>
        <AppLayout />
      </AuthGuard>
    ),
    children: [
      { index: true, element: <DashboardPage /> },
      // 고객사 — 회사 공유 자원 (SC-03)
      { path: 'customers', element: <CustomerListPage /> },
      { path: 'customers/:id', element: <CustomerDetailPage /> },
      // 딜 — 작업의 허브 (10 §3.1)
      { path: 'deals', element: <DealBoardPage /> },
      { path: 'deals/:id', element: <DealDetailPage /> },
      // 견적 — 목록 · 상세/편집기 · 미리보기 (QT-12는 고객 화면과 동일 구성)
      { path: 'quotes', element: <QuoteListPage /> },
      { path: 'quotes/:id', element: <QuoteDetailPage /> },
      { path: 'quotes/:id/preview', element: <QuotePreviewPage /> },
      // 주문 — 상태 없음, 기록의 종착점 (Q-09)
      { path: 'orders', element: <OrderListPage /> },
      { path: 'orders/:id', element: <OrderDetailPage /> },
      // 상품 — 조회는 전원, 편집은 기업 관리자 (PR-09·10)
      { path: 'products', element: <ProductListPage /> },
      // 기업 관리자 전용 (09 매트릭스)
      { path: 'members', element: <RequireAdmin><MemberListPage /></RequireAdmin> },
      { path: 'audit-logs', element: <RequireAdmin><AuditLogPage /></RequireAdmin> },
      // 알림 전체 목록 (NT-08) · 내 정보 (AU-04·07, NT-07)
      { path: 'notifications', element: <NotificationListPage /> },
      { path: 'me', element: <MePage /> },
      ...devRoutes,
    ],
  },

  // ── 로그인 · 비로그인 계정 (자체 레이아웃 — center-page)
  { path: '/login', element: <LoginPage /> },
  { path: '/apply', element: <ApplyPage /> },
  { path: '/invite/:token', element: <InviteAcceptPage /> },
  { path: '/password-reset', element: <PasswordSetupPage /> },
  { path: '/password-reset/request', element: <PasswordResetRequestPage /> },

  // ── 플랫폼 관리자 (/admin/api/v1) — 별도 세션 (AU-08) · 영업 데이터 없음 (ON-11)
  { path: '/admin/login', element: <AdminLoginPage /> },
  {
    path: '/admin',
    element: (
      <AdminAuthGuard>
        <AdminLayout />
      </AdminAuthGuard>
    ),
    children: [
      { index: true, element: <Navigate to="/admin/applications" replace /> },
      { path: 'applications', element: <AdminApplicationListPage /> },
      { path: 'applications/:id', element: <AdminApplicationDetailPage /> },
      { path: 'companies', element: <AdminCompanyListPage /> },
    ],
  },

  // ── 비로그인 · 고객 (앱 레이아웃 상속 금지)
  {
    element: <CustomerLayout />,
    children: [
      { path: '/q/:token', element: <QuoteViewPage /> },
    ],
  },
])
