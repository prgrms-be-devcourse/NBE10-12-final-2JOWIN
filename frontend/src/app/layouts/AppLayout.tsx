import { Avatar, Badge, Box, Button, Container, DropdownMenu, Flex, Separator, Text } from '@radix-ui/themes'
import { BellIcon, ChevronDownIcon } from '@radix-ui/react-icons'
import { NavLink, Outlet } from 'react-router'
import { isAdmin, useSession } from '../session'

/**
 * 구성원 앱 레이아웃 — 상단 내비 (10-screen-design.md §1 · §3.1).
 *
 * 매일 쓰는 업무 도구이므로 밀도를 높게 잡는다(높이 52px, 본문 폭 1200px).
 * **관리자 전용 메뉴는 숨긴다** — 비활성화가 아니라 숨김이다 (§3.2).
 */

interface Menu { to: string; label: string; end?: boolean }

const MENUS: Menu[] = [
  { to: '/', label: '대시보드', end: true },
  { to: '/customers', label: '고객사' },
  { to: '/deals', label: '딜' },
  { to: '/quotes', label: '견적' },
  { to: '/orders', label: '주문' },
  { to: '/products', label: '상품' },
]

const ADMIN_MENUS: Menu[] = [
  { to: '/members', label: '구성원' },
  { to: '/audit-logs', label: '감사 로그' },
]

export function AppLayout() {
  const session = useSession()
  const menus = isAdmin(session) ? [...MENUS, ...ADMIN_MENUS] : MENUS

  return (
    <Flex direction="column" height="100%">
      <Box
        asChild
        style={{
          borderBottom: '1px solid var(--gray-a5)',
          background: 'var(--color-background)',
          position: 'sticky',
          top: 0,
          zIndex: 10,
        }}
      >
        <header>
          <Container size="4" px="4">
            <Flex align="center" gap="5" height="52px">
              <Text size="3" weight="bold" style={{ letterSpacing: '-0.02em' }}>
                2JO
              </Text>

              <Flex asChild align="center" gap="1">
                <nav>
                  {menus.map((menu) => (
                    <NavLink key={menu.to} to={menu.to} end={menu.end} className="nav-link" style={navLinkStyle}>
                      {menu.label}
                    </NavLink>
                  ))}
                </nav>
              </Flex>

              <Flex align="center" gap="3" ml="auto">
                <NotificationBell />
                <Separator orientation="vertical" size="1" />
                <ProfileMenu name={session.name} role={session.role} company={session.companyName} />
              </Flex>
            </Flex>
          </Container>
        </header>
      </Box>

      <Box flexGrow="1" style={{ background: 'var(--gray-1)' }}>
        <Container size="4" px="4" py="6">
          <Outlet />
        </Container>
      </Box>
    </Flex>
  )
}

/** 미읽음은 red 원형 배지 (10-screen-design.md §6.4) */
function NotificationBell() {
  // TODO(D 연동): GET /api/v1/notifications?unreadOnly=true 의 건수를 쓴다 (NT-08).
  // 목 핸들러가 없는 동안 임의 숫자를 넣지 않는다 — 목과 화면은 같은 데이터를 봐야 한다
  const unread = 0
  return (
    <Button variant="ghost" color="gray" highContrast aria-label={`알림 ${unread}건`}>
      <Box position="relative">
        <BellIcon width="18" height="18" />
        {unread > 0 && (
          <Badge
            color="red"
            radius="full"
            size="1"
            style={{ position: 'absolute', top: -5, right: -8, minWidth: 15, justifyContent: 'center', fontSize: 9, lineHeight: '15px', padding: 0 }}
          >
            {unread > 9 ? '9+' : unread}
          </Badge>
        )}
      </Box>
    </Button>
  )
}

function ProfileMenu({ name, role, company }: { name: string; role: string; company: string }) {
  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger>
        <Button variant="ghost" color="gray" highContrast>
          <Avatar size="1" fallback={name.slice(0, 1)} radius="full" color="indigo" />
          <Text size="2">{name}</Text>
          <ChevronDownIcon />
        </Button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Content align="end" style={{ minWidth: 180 }}>
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
        <DropdownMenu.Item color="red">로그아웃</DropdownMenu.Item>
      </DropdownMenu.Content>
    </DropdownMenu.Root>
  )
}

// 비활성의 background를 인라인으로 두지 않는다 — 인라인이 .nav-link:hover를 이겨 호버가 죽는다
const navLinkStyle = ({ isActive }: { isActive: boolean }) => ({
  padding: '6px 10px',
  borderRadius: 'var(--radius-2)',
  fontSize: 'var(--font-size-2)',
  fontWeight: isActive ? 500 : 400,
  color: isActive ? 'var(--indigo-11)' : 'var(--gray-11)',
  ...(isActive ? { background: 'var(--indigo-3)' } : {}),
  textDecoration: 'none',
})
