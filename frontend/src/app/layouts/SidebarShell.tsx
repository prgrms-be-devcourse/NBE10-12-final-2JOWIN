import { useEffect, useState, type ReactNode } from 'react'
import { Box, Container, Flex, Tooltip } from '@radix-ui/themes'
import { DoubleArrowLeftIcon, DoubleArrowRightIcon } from '@radix-ui/react-icons'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { BRAND, Logo } from '../../shared/brand'
import { CELL, SIDEBAR_WIDTH, type Menu, type SidebarTone } from './shell'

/**
 * 좌측 사이드바 + 상단 바 골격 (10-screen-design.md §3.1).
 *
 * 구성원 앱(AppLayout)과 플랫폼 관리자(AdminLayout)가 같은 골격을 쓴다 — 접기·40px 셀 정렬·
 * 활성 표시가 두 앱에서 다르게 움직이면 손이 헷갈린다. 두 앱이 다른 것은 메뉴·톤·상단 바 오른쪽뿐이라
 * 그 셋만 props로 받는다.
 *
 * - 사이드바는 내비게이션만, 알림·프로필 같은 것은 상단 바 오른쪽(`topbarRight`)
 * - 사이드바·상단 바는 sticky. 바깥 컨테이너는 min-height여야 한다 (height 고정 시 sticky가 풀린다)
 * - 접힘 상태는 localStorage(`storageKey`)에 저장하고, `wideRoutes`는 기본 접힘
 */

interface Props {
  menus: Menu[]
  /** 접힘 상태 저장 키 — 앱마다 따로 기억한다 */
  storageKey: string
  /** 기본 접힘 라우트 — 가로 폭이 필요한 화면 */
  wideRoutes?: string[]
  /** 로고 클릭 시 이동 */
  homeTo: string
  /** 로고 옆 앱 이름표 (관리자 "플랫폼 관리자") — 링크가 아니다 */
  brandBadge?: ReactNode
  /** 상단 바 오른쪽 — 알림·프로필 / 관리자 이름·로그아웃 */
  topbarRight: ReactNode
  /** 본문 컨테이너 폭 — 구성원 4 · 관리자 3 (화면이 4종뿐이라 좁게) */
  containerSize?: '3' | '4'
  tone?: SidebarTone
}

const TOPBAR_HEIGHT = 56
const PAD = (SIDEBAR_WIDTH.collapsed - CELL) / 2 // 12 — 접힌 폭에서 셀이 정중앙에 오는 여백

const TONE: Record<SidebarTone, { background: string; stripe?: string }> = {
  member: { background: 'linear-gradient(var(--accent-a2), var(--accent-a2)), var(--color-background)' },
  admin: { background: 'linear-gradient(var(--accent-a4), var(--accent-a4)), var(--color-background)', stripe: 'var(--accent-9)' },
}

export function SidebarShell({
  menus, storageKey, wideRoutes = [], homeTo, brandBadge, topbarRight, containerSize = '4', tone = 'member',
}: Props) {
  const [collapsed, setCollapsed] = useSidebarCollapsed(storageKey, wideRoutes)
  const { background, stripe } = TONE[tone]

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
          background,
          // 관리자는 위쪽에 accent 띠 — 접힌 상태에서도 앱이 구분된다
          borderTop: stripe ? `3px solid ${stripe}` : undefined,
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
                  <Flex align="center" gap="2" height={`${CELL}px`}>
                    <Link to={homeTo} aria-label="홈으로" style={{ display: 'flex', alignItems: 'center', height: CELL, paddingLeft: (CELL - 18) / 2 }}>
                      <Logo height={22} />
                    </Link>
                    {/* 앱 이름표 — 링크 밖에 둔다. 누르는 것이 아니라 읽는 것이다 */}
                    {brandBadge && <span style={{ display: 'inline-flex', userSelect: 'none' }}>{brandBadge}</span>}
                  </Flex>
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
              {topbarRight}
            </Flex>
          </header>
        </Box>

        <Box asChild flexGrow="1">
          <main>
            <Container size={containerSize} px="6" py="6">
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
function useSidebarCollapsed(storageKey: string, wideRoutes: string[]): [boolean, (next: boolean) => void] {
  const { pathname } = useLocation()
  const [stored, setStored] = useState<boolean | null>(() => {
    try {
      const raw = localStorage.getItem(storageKey)
      return raw === null ? null : raw === 'true'
    } catch {
      return null
    }
  })

  useEffect(() => {
    if (stored === null) return
    try {
      localStorage.setItem(storageKey, String(stored))
    } catch {
      // localStorage 사용 불가 환경 — 세션 내에서만 유지
    }
  }, [stored, storageKey])

  const byRoute = wideRoutes.some((route) => pathname.startsWith(route))
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
