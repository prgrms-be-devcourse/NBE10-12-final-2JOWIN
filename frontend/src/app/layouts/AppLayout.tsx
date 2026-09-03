import { useEffect, useState, type ComponentType } from 'react'
import { Avatar, Badge, Box, Button, Container, DropdownMenu, Flex, Text, Tooltip } from '@radix-ui/themes'
import {
  ActivityLogIcon,
  BackpackIcon,
  BellIcon,
  ChevronDownIcon,
  ColumnsIcon,
  CubeIcon,
  DashboardIcon,
  DoubleArrowLeftIcon,
  DoubleArrowRightIcon,
  FileTextIcon,
  IdCardIcon,
  PersonIcon,
} from '@radix-ui/react-icons'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { clearSession } from '../../shared/api/client'
import { logout } from '../../domains/auth/api'
import { BRAND, Logo } from '../../shared/brand'
import { isAdmin, useSession } from '../session'

/**
 * 구성원 앱 레이아웃 — 좌측 사이드바 + 상단 바 (10-screen-design.md §3.1 · 12-frontend-plan.md §6.2).
 *
 * - 사이드바는 내비게이션만, 알림·프로필은 상단 바
 * - 사이드바·상단 바는 sticky. 바깥 컨테이너는 min-height여야 한다 (height 고정 시 sticky가 풀린다)
 * - 접힘 상태는 localStorage에 저장하고, WIDE_ROUTES는 기본 접힘
 * - 관리자 전용 메뉴는 숨김 (§3.2)
 */

interface Menu {
  to: string
  label: string
  icon: ComponentType<{ width?: string | number; height?: string | number }>
  end?: boolean
}

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

const SIDEBAR_WIDTH = { expanded: 240, collapsed: 64 } as const
const TOPBAR_HEIGHT = 56
const CELL = 40 // 아이콘 셀 한 변 — 로고 마크·아이콘·버튼이 전부 이 정사각형 안에 가운데 정렬된다
const PAD = (SIDEBAR_WIDTH.collapsed - CELL) / 2 // 12 — 접힌 폭에서 셀이 정중앙에 오는 여백
/** 기본 접힘 라우트 — 가로 폭이 필요한 화면 */
const WIDE_ROUTES = ['/deals']
const STORAGE_KEY = '2jo.sidebar.collapsed'

export function AppLayout() {
  const session = useSession()
  const menus = isAdmin(session) ? [...MENUS, ...ADMIN_MENUS] : MENUS
  const [collapsed, setCollapsed] = useSidebarCollapsed()

  return (
    <Flex style={{ minHeight: '100%' }}>
      <Box
        asChild
        flexShrink="0"
        style={{
          width: collapsed ? SIDEBAR_WIDTH.collapsed : SIDEBAR_WIDTH.expanded,
          position: 'sticky',
          top: 0,
          height: '100vh',
          boxShadow: 'inset -1px 0 var(--gray-a5)', // 테두리 대신 — 안쪽 폭(40px 셀 + 여백)을 먹지 않는다
          // 사이드바 면 톤 (10 §2.2)
          background: 'linear-gradient(var(--accent-a2), var(--accent-a2)), var(--color-background)',
          transition: 'width var(--motion-base) var(--ease-out)',
          overflow: 'hidden',
          zIndex: 10,
        }}
      >
        <aside>
          <Flex direction="column" height="100%" gap="4" style={{ padding: PAD }}>
            <Flex align="center" justify="between" height={`${CELL}px`} style={{ minWidth: SIDEBAR_WIDTH.expanded - PAD * 2 }}>
              {collapsed ? (
                <ExpandControl onExpand={() => setCollapsed(false)} />
              ) : (
                <>
                  <Link to="/" aria-label="홈으로" style={{ display: 'flex', alignItems: 'center', height: CELL, paddingLeft: (CELL - 18) / 2 }}>
                    <Logo height={22} />
                  </Link>
                  <Tooltip content="사이드바 접기" side="right">
                    <button type="button" className="icon-cell" aria-label="사이드바 접기" onClick={() => setCollapsed(true)}>
                      <DoubleArrowLeftIcon width="18" height="18" />
                    </button>
                  </Tooltip>
                </>
              )}
            </Flex>

            <Flex asChild direction="column" gap="1">
              <nav aria-label="주요 메뉴">
                {menus.map((menu) => (
                  <SidebarLink key={menu.to} menu={menu} collapsed={collapsed} />
                ))}
              </nav>
            </Flex>
          </Flex>
        </aside>
      </Box>

      {/* 본문 면 톤 gray-2 (10 §2.2) */}
      <Flex direction="column" flexGrow="1" minWidth="0" style={{ background: 'var(--gray-2)' }}>
        <Box
          asChild
          style={{
            position: 'sticky',
            top: 0,
            zIndex: 9,
            height: TOPBAR_HEIGHT,
            borderBottom: '1px solid var(--gray-a5)',
            background: 'var(--color-background)',
          }}
        >
          <header>
            <Flex align="center" justify="end" gap="2" height="100%" px="4">
              <NotificationBell />
              <ProfileMenu name={session.name} role={session.role} company={session.companyName} />
            </Flex>
          </header>
        </Box>

        <Box asChild flexGrow="1">
          <main>
            <Container size="4" px="6" py="6">
              <Outlet />
            </Container>
          </main>
        </Box>
      </Flex>
    </Flex>
  )
}

/** 접힌 상태의 로고 자리. hover 시 「사이드바 열기」 버튼으로 전환된다 */
function ExpandControl({ onExpand }: { onExpand: () => void }) {
  const [hover, setHover] = useState(false)
  return (
    <Tooltip content="사이드바 열기" side="right" open={hover}>
      <button
        type="button"
        className="icon-cell"
        aria-label="사이드바 열기"
        onClick={onExpand}
        onMouseEnter={() => setHover(true)}
        onMouseLeave={() => setHover(false)}
        onFocus={() => setHover(true)}
        onBlur={() => setHover(false)}
      >
        {hover ? <DoubleArrowRightIcon width="18" height="18" /> : <img src={BRAND.mark} alt={BRAND.name} width={22} height={22} draggable={false} />}
      </button>
    </Tooltip>
  )
}

/** 접힘 상태 — 저장된 값이 없으면 라우트 기본값 */
function useSidebarCollapsed(): [boolean, (next: boolean) => void] {
  const { pathname } = useLocation()
  const [stored, setStored] = useState<boolean | null>(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      return raw === null ? null : raw === 'true'
    } catch {
      return null
    }
  })

  useEffect(() => {
    if (stored === null) return
    try {
      localStorage.setItem(STORAGE_KEY, String(stored))
    } catch {
      // localStorage 사용 불가 환경 — 세션 내에서만 유지
    }
  }, [stored])

  const byRoute = WIDE_ROUTES.some((route) => pathname.startsWith(route))
  return [stored ?? byRoute, setStored]
}

function SidebarLink({ menu, collapsed }: { menu: Menu; collapsed: boolean }) {
  const Icon = menu.icon
  // 인라인 style 함수는 Tooltip(asChild)의 props 병합에서 유실되므로 CSS 클래스로 처리한다
  const link = (
    <NavLink to={menu.to} end={menu.end} className="sidebar-link" aria-label={menu.label}>
      <span className="sidebar-link__icon">
        <Icon width="18" height="18" />
      </span>
      <span className="sidebar-link__label" style={{ opacity: collapsed ? 0 : 1 }}>
        {menu.label}
      </span>
    </NavLink>
  )
  return collapsed ? (
    <Tooltip content={menu.label} side="right">
      {link}
    </Tooltip>
  ) : (
    link
  )
}

/** 미읽음은 red 원형 배지 (10-screen-design.md §6.4) */
function NotificationBell() {
  // TODO(D): GET /api/v1/notifications?unreadOnly=true 건수 연동 (NT-08)
  const unread = 0
  return (
    <Tooltip content="알림">
      <button type="button" className="icon-cell" aria-label={`알림 ${unread}건`}>
        <Box position="relative" style={{ display: 'inline-flex' }}>
          <BellIcon width="18" height="18" />
          {unread > 0 && (
            <Badge
              color="red"
              radius="full"
              size="1"
              style={{ position: 'absolute', top: -6, right: -8, minWidth: 15, justifyContent: 'center', fontSize: 9, lineHeight: '15px', padding: 0 }}
            >
              {unread > 9 ? '9+' : unread}
            </Badge>
          )}
        </Box>
      </button>
    </Tooltip>
  )
}

function ProfileMenu({ name, role, company }: { name: string; role: string; company: string }) {
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
            {role === 'COMPANY_ADMIN' ? '기업 관리자' : '영업 담당자'}
          </Text>
        </Box>
        <DropdownMenu.Separator />
        <DropdownMenu.Item>내 정보</DropdownMenu.Item>
        <DropdownMenu.Item>알림 수신 설정</DropdownMenu.Item>
        <DropdownMenu.Separator />
        <DropdownMenu.Item color="red" onSelect={handleLogout}>
          로그아웃
        </DropdownMenu.Item>
      </DropdownMenu.Content>
    </DropdownMenu.Root>
  )
}

