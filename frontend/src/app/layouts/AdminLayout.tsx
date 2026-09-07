import { Avatar, Badge, Button, Text, Theme } from '@radix-ui/themes'
import { ExitIcon, FileTextIcon, HomeIcon } from '@radix-ui/react-icons'
import { useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { clearAdminSession } from '../../shared/api/client'
import { adminLogout } from '../../domains/admin/api'
import { clearAdminProfile, useAdminSession } from '../../domains/admin/session'
import { SidebarShell } from './SidebarShell'
import { CELL, type Menu } from './shell'

/**
 * 플랫폼 관리자 레이아웃 — 구성원 앱과 같은 사이드바 골격, 다른 색 (10-screen-design.md §1 "세 앱은 서로 다른 제품처럼").
 *
 * 골격을 공유하는 이유: 접기·활성 표시·40px 셀이 두 앱에서 같아야 손이 헷갈리지 않는다.
 * 색을 바꾸는 이유: 한 브라우저에서 구성원 세션과 관리자 세션이 공존한다(AU-08 · Q-28) —
 * 지금 어느 쪽에 들어와 있는지 면 색만으로 알아야 한다. accent를 indigo로 바꾸면 사이드바·활성 메뉴·
 * 주요 버튼·배지가 한꺼번에 바뀐다. indigo는 브랜드 blue와 같은 계열이라 어울리면서도 한 단계 짙어 구분되고,
 * 구성원 앱의 의미 색(blue·amber·green·red, 10 §2.3)과는 겹치지 않는다.
 *
 * 메뉴는 둘뿐이다 — 관리자 화면은 4종이고(04 §7), 영업 데이터는 애초에 엔드포인트가 없다 (ON-11).
 */

const MENUS: Menu[] = [
  { to: '/admin/applications', label: '가입 신청', icon: FileTextIcon },
  { to: '/admin/companies', label: '회사', icon: HomeIcon },
]

export function AdminLayout() {
  const session = useAdminSession()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  // 서버가 쿠키(2jo_admin_rt)를 지우고, 프론트는 메모리 access·프로필·캐시를 비운다
  const handleLogout = async () => {
    try {
      await adminLogout()
    } finally {
      clearAdminSession()
      clearAdminProfile()
      queryClient.clear()
      navigate('/admin/login', { replace: true })
    }
  }

  return (
    <Theme accentColor="indigo" asChild>
      <div style={{ minHeight: '100%' }}>
        <SidebarShell
          menus={MENUS}
          storageKey="2jo.admin.sidebar.collapsed"
          homeTo="/admin"
          tone="admin"
          containerSize="3"
          brandBadge={
            <Badge variant="solid" radius="full" size="1">
              플랫폼 관리자
            </Badge>
          }
          topbarRight={
            <>
              <Badge variant="soft" radius="full">
                운영자
              </Badge>
              <Button
                variant="ghost"
                color="gray"
                highContrast
                style={{ margin: 0, height: CELL, padding: '0 10px 0 6px', borderRadius: 'var(--radius-3)', cursor: 'default' }}
                asChild
              >
                <span>
                  <Avatar size="1" fallback={session.name.slice(0, 1)} radius="full" />
                  <Text size="2" weight="medium">
                    {session.name}
                  </Text>
                </span>
              </Button>
              <Button variant="soft" color="gray" onClick={handleLogout}>
                <ExitIcon /> 로그아웃
              </Button>
            </>
          }
        />
      </div>
    </Theme>
  )
}
