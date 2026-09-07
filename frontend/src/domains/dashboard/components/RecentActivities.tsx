import { Card, Flex, Heading, Text } from '@radix-ui/themes'
import { Link } from 'react-router'
import { dateTime } from '../../../shared/lib/format'
import type { DashboardRecentActivity } from '../../../shared/api/types'
import { relativeTime } from '../../notification/lib/relativeTime'

/** 최근 활동 (DB-04 · 10 §5.1) — 요약 + 딜 링크 + 상대 시각 */
export function RecentActivities({ activities }: { activities: DashboardRecentActivity[] }) {
  return (
    <Card size="3">
      <Heading size="3" mb="3">
        최근 활동
      </Heading>
      {activities.length === 0 ? (
        <Text as="p" size="2" color="gray" align="center" my="5">
          아직 기록된 활동이 없습니다.
        </Text>
      ) : (
        <Flex direction="column">
          {activities.map((activity, index) => (
            <Flex key={`${activity.dealId}-${activity.occurredAt}-${index}`} align="center" gap="3" py="2" wrap="wrap" style={{ borderTop: index === 0 ? undefined : '1px solid var(--gray-a4)' }}>
              <Text asChild size="2" weight="medium" style={{ flexShrink: 0 }}>
                <Link to={`/deals/${activity.dealId}`} style={{ color: 'inherit', textDecoration: 'none' }}>
                  {activity.dealTitle}
                </Link>
              </Text>
              <Text size="2" color="gray" style={{ flex: 1, minWidth: 160 }}>
                {activity.summary}
              </Text>
              <Text size="1" color="gray" title={dateTime(activity.occurredAt)}>
                {relativeTime(activity.occurredAt)}
              </Text>
            </Flex>
          ))}
        </Flex>
      )}
    </Card>
  )
}
