import { useMemo } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { Badge, Box, Button, Card, Flex, SegmentedControl, Text } from '@radix-ui/themes'
import { BellIcon, CheckIcon } from '@radix-ui/react-icons'
import { EmptyState, ErrorCallout, NotificationTypeBadge, PageHeader, Pagination, TableSkeleton } from '../../../shared/ui'
import { codeOf } from '../../../shared/api/client'
import { dateTime } from '../../../shared/lib/format'
import type { NotificationResponse } from '../../../shared/api/types'
import { relativeTime } from '../lib/relativeTime'
import { useMarkAllRead, useMarkRead, useNotificationList } from '../hooks'

/**
 * 알림 전체 목록 (NT-08 · 10 §6.4) — 본인 수신분만.
 * 필터(전체/미읽음)·페이지는 URL 쿼리. 항목 클릭 = 읽음 처리 + 해당 견적으로 이동.
 * 문의(INQUIRY_RECEIVED)의 내용은 v1에 조회 API가 없어 알림 메시지가 전부다 (Q-42).
 */
const PAGE_SIZE = 20

export function NotificationListPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const unreadOnly = params.get('filter') === 'unread'
  const page = Math.max(0, Number(params.get('page') ?? 0))

  const query = useMemo(() => ({ unreadOnly: unreadOnly || undefined, page, size: PAGE_SIZE }), [unreadOnly, page])
  const { data, isPending, isFetching, error, refetch } = useNotificationList(query)
  const markRead = useMarkRead()
  const markAll = useMarkAllRead()

  const update = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value) merged.set(key, value)
      else merged.delete(key)
    }
    if (!('page' in next)) merged.delete('page')
    setParams(merged, { replace: true })
  }

  const open = (notification: NotificationResponse) => {
    if (!notification.readAt) markRead.mutate(notification.id)
    if (notification.refType === 'QUOTE') navigate(`/quotes/${notification.refId}`)
  }

  return (
    <>
      <PageHeader
        title="알림"
        badge={data && unreadOnly && <Badge color="red" variant="soft" size="2">{data.totalElements}건</Badge>}
        description="견적 열람·승인·반려와 고객 문의를 알려드립니다. 메일 수신 여부는 내 정보에서 바꿀 수 있습니다."
        actions={
          <Button variant="soft" color="gray" onClick={() => markAll.mutate()} loading={markAll.isPending}>
            <CheckIcon /> 모두 읽음
          </Button>
        }
      />

      <Flex mb="4">
        <SegmentedControl.Root value={unreadOnly ? 'unread' : 'all'} onValueChange={(value) => update({ filter: value === 'unread' ? 'unread' : '' })}>
          <SegmentedControl.Item value="all">전체</SegmentedControl.Item>
          <SegmentedControl.Item value="unread">미읽음</SegmentedControl.Item>
        </SegmentedControl.Root>
      </Flex>

      {error && <ErrorCallout code={codeOf(error)} onRetry={() => refetch()} />}

      {isPending ? (
        <TableSkeleton columns={[1, 4, 1]} />
      ) : data && data.content.length === 0 ? (
        <EmptyState
          icon={<BellIcon width="28" height="28" />}
          title={unreadOnly ? '미읽음 알림이 없습니다' : '아직 알림이 없습니다'}
          description="고객이 견적을 열람하거나 응답하면 여기에 쌓입니다."
          action={unreadOnly ? { label: '전체 보기', onClick: () => update({ filter: '' }) } : undefined}
        />
      ) : (
        data && (
          <Card className="enter-fade" style={{ opacity: isFetching ? 0.7 : 1, transition: 'opacity var(--motion-fast)' }}>
            <Flex direction="column">
              {data.content.map((notification, index) => (
                <Box
                  key={notification.id}
                  asChild
                  className="row-hover"
                  style={{ borderTop: index === 0 ? undefined : '1px solid var(--gray-a4)', cursor: 'pointer', background: notification.readAt ? undefined : 'var(--accent-a2)' }}
                >
                  <button type="button" onClick={() => open(notification)} style={{ all: 'unset', display: 'block', width: '100%', boxSizing: 'border-box', padding: '12px' }}>
                    <Flex align="center" gap="3" wrap="wrap">
                      <NotificationTypeBadge type={notification.type} />
                      <Text size="2" weight={notification.readAt ? 'regular' : 'medium'} style={{ flex: 1, minWidth: 200 }}>
                        {notification.message}
                      </Text>
                      <Text size="1" color="gray" title={dateTime(notification.createdAt)}>
                        {relativeTime(notification.createdAt)}
                      </Text>
                    </Flex>
                  </button>
                </Box>
              ))}
            </Flex>
            <Pagination data={data} unit="건" onPageChange={(next) => update({ page: String(next) })} />
          </Card>
        )
      )}
    </>
  )
}
