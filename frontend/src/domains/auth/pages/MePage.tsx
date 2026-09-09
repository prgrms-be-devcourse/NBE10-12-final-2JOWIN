import { useSearchParams } from 'react-router'
import { Box, Card, Tabs } from '@radix-ui/themes'
import { PageHeader, RoleBadge } from '../../../shared/ui'
import { useSession } from '../../../app/session'
import { ProfileForm } from '../components/ProfileForm'
import { PasswordForm } from '../components/PasswordForm'
import { NotificationSettingsForm } from '../components/NotificationSettingsForm'

/**
 * 내 정보 — 프로필 · 비밀번호 · 알림 수신 설정 (10 §3.1 "프로필" · AU-04·07 · NT-07).
 * 탭은 URL 쿼리 `?tab=` — 상단 바 프로필 메뉴의 "알림 수신 설정"이 바로 그 탭으로 온다.
 * 플랫폼 관리자에게는 이 화면이 없다 (09 본인 계정 행 — v1 엔드포인트 없음).
 */
const TABS = ['profile', 'password', 'notifications'] as const
type Tab = (typeof TABS)[number]

export function MePage() {
  const session = useSession()
  const [params, setParams] = useSearchParams()
  const raw = params.get('tab')
  const tab: Tab = (TABS as readonly string[]).includes(raw ?? '') ? (raw as Tab) : 'profile'

  return (
    <>
      <PageHeader title={session.name} badge={<RoleBadge role={session.role} size="2" />} description={`${session.companyName} · ${session.email}`} />

      <Card size="3" className="enter-fade">
        <Tabs.Root value={tab} onValueChange={(value) => setParams({ tab: value }, { replace: true })}>
          <Tabs.List>
            <Tabs.Trigger value="profile">프로필</Tabs.Trigger>
            <Tabs.Trigger value="password">비밀번호</Tabs.Trigger>
            <Tabs.Trigger value="notifications">알림 수신 설정</Tabs.Trigger>
          </Tabs.List>
          <Box pt="5">
            <Tabs.Content value="profile">
              {/* key는 계정 단위만 — 이름·연락처까지 넣으면 저장 성공 직후 리마운트되어 "저장되었습니다"가 뜨기도 전에 사라진다 */}
              <ProfileForm key={session.memberId} />
            </Tabs.Content>
            <Tabs.Content value="password">
              <PasswordForm />
            </Tabs.Content>
            <Tabs.Content value="notifications">
              <NotificationSettingsForm />
            </Tabs.Content>
          </Box>
        </Tabs.Root>
      </Card>
    </>
  )
}
