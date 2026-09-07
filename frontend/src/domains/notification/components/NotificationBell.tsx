import { Badge, Box, Button, DropdownMenu, Flex, Text, Tooltip } from '@radix-ui/themes'
import { BellIcon } from '@radix-ui/react-icons'
import { useNavigate } from 'react-router'
import type { NotificationResponse } from '../../../shared/api/types'
import { NotificationTypeBadge } from '../../../shared/ui'
import { relativeTime } from '../lib/relativeTime'
import { useMarkAllRead, useMarkRead, useUnreadNotifications } from '../hooks'

/**
 * 상단 바 알림 벨 (10 §6.4 · NT-08).
 *
 * - 미읽음 수는 red 원형 배지, 9를 넘으면 9+
 * - 드롭다운에 최근 미읽음 5건 — 클릭하면 읽음 처리하고 `refType·refId`로 이동 (QUOTE → /quotes/{id})
 * - 폴링 30초 · 탭 비활성 시 중단은 훅(useUnreadNotifications)이 맡는다
 * - 버튼은 상단 바의 다른 아이콘과 같은 40px 셀(.icon-cell)
 */
export function NotificationBell() {
  const navigate = useNavigate()
  const { data } = useUnreadNotifications()
  const markRead = useMarkRead()
  const markAll = useMarkAllRead()
  const unread = data?.totalElements ?? 0
  const recent = data?.content ?? []

  const open = (notification: NotificationResponse) => {
    markRead.mutate(notification.id)
    if (notification.refType === 'QUOTE') navigate(`/quotes/${notification.refId}`)
  }

  return (
    <DropdownMenu.Root>
      <Tooltip content="알림">
        <DropdownMenu.Trigger>
          <button type="button" className="icon-cell" aria-label={`알림 ${unread}건`}>
            <Box position="relative" style={{ display: 'inline-flex' }}>
              <BellIcon width="18" height="18" />
              {unread > 0 && (
                <Badge
                  color="red"
                  variant="solid"
                  radius="full"
                  size="1"
                  style={{ position: 'absolute', top: -6, right: -8, minWidth: 15, justifyContent: 'center', fontSize: 9, lineHeight: '15px', padding: '0 3px' }}
                >
                  {unread > 9 ? '9+' : unread}
                </Badge>
              )}
            </Box>
          </button>
        </DropdownMenu.Trigger>
      </Tooltip>
      <DropdownMenu.Content align="end" style={{ minWidth: 340, maxWidth: 400 }}>
        <Flex align="center" justify="between" px="2" py="1">
          <Text size="2" weight="medium">
            알림{unread > 0 && ` · 미읽음 ${unread}건`}
          </Text>
          {unread > 0 && (
            <Button size="1" variant="ghost" color="gray" onClick={() => markAll.mutate()} loading={markAll.isPending}>
              모두 읽음
            </Button>
          )}
        </Flex>
        <DropdownMenu.Separator />
        {recent.length === 0 ? (
          <Box px="2" py="3">
            <Text size="2" color="gray">
              새 알림이 없습니다.
            </Text>
          </Box>
        ) : (
          recent.map((notification) => (
            <DropdownMenu.Item key={notification.id} onSelect={() => open(notification)} style={{ height: 'auto', padding: '8px' }}>
              <Flex direction="column" gap="1" width="100%">
                <Flex align="center" gap="2">
                  <NotificationTypeBadge type={notification.type} />
                  <Text size="1" color="gray" ml="auto">
                    {relativeTime(notification.createdAt)}
                  </Text>
                </Flex>
                <Text size="2" weight={notification.readAt ? 'regular' : 'medium'} style={{ whiteSpace: 'normal' }}>
                  {notification.message}
                </Text>
              </Flex>
            </DropdownMenu.Item>
          ))
        )}
        <DropdownMenu.Separator />
        <DropdownMenu.Item onSelect={() => navigate('/notifications')}>전체 보기</DropdownMenu.Item>
      </DropdownMenu.Content>
    </DropdownMenu.Root>
  )
}
