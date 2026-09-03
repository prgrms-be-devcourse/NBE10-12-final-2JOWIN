import { Box, Flex, Skeleton, Text } from '@radix-ui/themes'
import { AutoBadge } from '../../../shared/ui'
import { dateTime } from '../../../shared/lib/format'
import type { ActivityResponse } from '../../../shared/api/types'

interface Props {
  activities: ActivityResponse[] | undefined
  loading: boolean
}

/** 고객사 단위 이력 (AC-10). 자동 기록은 `자동` 배지 (10 §6.1) */
export function ActivityTimeline({ activities, loading }: Props) {
  if (loading) {
    return (
      <Flex direction="column" gap="3">
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} height="40px" />
        ))}
      </Flex>
    )
  }
  if (!activities || activities.length === 0) {
    return (
      <Box py="5">
        <Text as="p" size="2" color="gray" align="center">
          기록된 활동이 없습니다.
        </Text>
      </Box>
    )
  }

  return (
    <Box style={{ position: 'relative', paddingLeft: 20 }}>
      <Box
        aria-hidden
        style={{ position: 'absolute', left: 5, top: 8, bottom: 8, width: 2, background: 'var(--gray-a4)', borderRadius: 1 }}
      />
      <Flex direction="column" gap="4">
        {activities.map((activity) => (
          <Box key={activity.id} style={{ position: 'relative' }}>
            <Box
              aria-hidden
              style={{
                position: 'absolute', left: -19, top: 6, width: 10, height: 10, borderRadius: '50%',
                background: activity.type === 'MANUAL' ? 'var(--blue-9)' : 'var(--gray-7)',
                boxShadow: '0 0 0 2px var(--color-panel-solid)',
              }}
            />
            <Flex align="center" gap="2" wrap="wrap">
              <Text size="2">{activity.content}</Text>
              {activity.type === 'AUTO' && <AutoBadge />}
            </Flex>
            <Text as="div" size="1" color="gray" mt="1">
              {activity.authorMemberName}
              {!activity.authorActive && ' (퇴사)'} · {dateTime(activity.occurredAt)}
            </Text>
          </Box>
        ))}
      </Flex>
    </Box>
  )
}
