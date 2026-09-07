import { Avatar, Box, Button, DropdownMenu, Text } from '@radix-ui/themes'
import {
  ActivityLogIcon, BackpackIcon, ChevronDownIcon, ColumnsIcon, CubeIcon, DashboardIcon, FileTextIcon, IdCardIcon, PersonIcon,
} from '@radix-ui/react-icons'
import { useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { clearSession } from '../../shared/api/client'
import { logout } from '../../domains/auth/api'
import { ROLE_LABEL, type Role } from '../../shared/ui/status'
import { NotificationBell } from '../../domains/notification/components/NotificationBell'
import { isAdmin, useSession } from '../session'
import { SidebarShell } from './SidebarShell'
import { CELL, type Menu } from './shell'

/**
 * 구성원 앱 레이아웃 — 좌측 사이드바 + 상단 바 (10-screen-design.md §3.1 · 12-frontend-plan.md §6.2).
 *
 * 골격은 SidebarShell. 여기서 정하는 것은 메뉴(역할별)·상단 바 오른쪽(알림·프로필)뿐이다.
 * 관리자 전용 메뉴는 숨긴다 (§3.2) — 눌러서 403을 만나게 하지 않는다.
 */

const MENUS: Menu[] = [
  { to: '/', label: '대시보드', icon: DashboardIcon, end: true },
  { to: '/customers', label: '고객사', icon: IdCardIcon },
  { to: '/deals', label: '딜', icon: ColumnsIcon },
  { to: '/quotes', label: '견적', icon: FileTextIcon },
  { to: '/orders', label: '주문', icon: CubeIcon },
  { to: '/products', label: '상품', icon: BackpackIcon },
]

const ADMIN_MENUS: Menu[] = [
  { to: '/members', label: '구성원', icon: PersonIcon },
  { to: '/audit-logs', label: '감사 로그', icon: ActivityLogIcon },
]

/** 기본 접힘 라우트 — 딜 보드처럼 가로가 곧 정보량인 화면 (10 §3.1) */
const WIDE_ROUTES = ['/deals']

export function AppLayout() {
  const session = useSession()
  const menus = isAdmin(session) ? [...MENUS, ...ADMIN_MENUS] : MENUS

  return (
    <SidebarShell
      menus={menus}
      storageKey="2jo.sidebar.collapsed"
      wideRoutes={WIDE_ROUTES}
      homeTo="/"
      tone="member"
      containerSize="4"
      topbarRight={
        <>
          <NotificationBell />
          <ProfileMenu name={session.name} role={session.role} company={session.companyName} />
        </>
      }
    />
  )
}

function ProfileMenu({ name, role, company }: { name: string; role: Role; company: string }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  // 서버가 refresh 쿠키를 지우고, 프론트는 메모리 access와 캐시를 비운다 (12 §6.3-8)
  const handleLogout = async () => {
    try {
      await logout()
    } finally {
      clearSession()
      queryClient.clear()
      navigate('/login', { replace: true })
    }
  }

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger>
        <Button
          variant="ghost"
          color="gray"
          highContrast
          style={{ margin: 0, height: CELL, padding: '0 10px 0 6px', borderRadius: 'var(--radius-3)' }}
        >
          <Avatar size="1" fallback={name.slice(0, 1)} radius="full" />
          <Text size="2" weight="medium">
            {name}
          </Text>
          <Text size="2" color="gray">
            {company}
          </Text>
          <ChevronDownIcon />
        </Button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Content align="end" style={{ minWidth: 200 }}>
        <Box px="2" py="1">
          <Text as="div" size="2" weight="medium">
            {company}
          </Text>
          <Text as="div" size="1" color="gray">
            {ROLE_LABEL[role]}
          </Text>
        </Box>
        <DropdownMenu.Separator />
        <DropdownMenu.Item onSelect={() => navigate('/me')}>내 정보</DropdownMenu.Item>
        <DropdownMenu.Item onSelect={() => navigate('/me?tab=notifications')}>알림 수신 설정</DropdownMenu.Item>
        <DropdownMenu.Separator />
        <DropdownMenu.Item color="red" onSelect={handleLogout}>
          로그아웃
        </DropdownMenu.Item>
      </DropdownMenu.Content>
    </DropdownMenu.Root>
  )
}
