import { Box, Card, Flex, Heading, IconButton, Text, Tooltip } from '@radix-ui/themes'
import { CheckIcon } from '@radix-ui/react-icons'
import { Link } from 'react-router'
import { daysUntil } from '../../../shared/lib/format'
import type { DashboardFollowUp } from '../../../shared/api/types'

interface Props {
  followUps: DashboardFollowUp[]
  onComplete: (taskId: string) => void
  completing?: boolean
}

/**
 * 후속 필요 (DB-05 · 10 §5.1) — 담당 Deal의 미완료 할 일 (Q-29).
 * 마감이 지난 항목은 red 점 + red 텍스트로 최상단. 완료는 PATCH /tasks/{id} {done:true} (AC-09).
 * "내 할 일" 별도 목록은 없다 — 여기가 유일한 노출이다 (04 GAP-04 의도 확정).
 */
export function FollowUps({ followUps, onComplete, completing }: Props) {
  const sorted = [...followUps].sort((a, b) => a.dueDate.localeCompare(b.dueDate))
  return (
    <Card size="3" style={{ height: '100%' }}>
      <Heading size="3" mb="3">
        후속 필요{' '}
        <Text size="2" color="gray" weight="regular">
          {followUps.length}
        </Text>
      </Heading>
      {sorted.length === 0 ? (
        <Text as="p" size="2" color="gray" align="center" my="5">
          예정된 할 일이 없습니다. 딜 상세에서 할 일을 등록해 보세요.
        </Text>
      ) : (
        <Flex direction="column">
          {sorted.map((task, index) => {
            const days = daysUntil(`${task.dueDate}T00:00:00Z`)
            const overdue = days < 0
            const dueText = days > 0 ? `D-${days}` : days === 0 ? '오늘 마감' : `${-days}일 지남`
            return (
              <Flex key={task.taskId} align="center" gap="3" py="2" style={{ borderTop: index === 0 ? undefined : '1px solid var(--gray-a4)' }}>
                <Box aria-hidden style={{ width: 8, height: 8, borderRadius: '50%', flexShrink: 0, background: overdue ? 'var(--red-9)' : 'var(--blue-9)' }} />
                <Box flexGrow="1" minWidth="0">
                  <Text as="div" size="2" weight="medium" truncate>
                    {task.content}
                  </Text>
                  <Text as="div" size="1" color="gray" truncate>
                    <Link to={`/deals/${task.dealId}`} style={{ color: 'inherit' }}>
                      {task.dealTitle}
                    </Link>
                    {' · '}
                    <Text color={overdue ? 'red' : undefined} weight={overdue ? 'medium' : undefined}>
                      {dueText}
                    </Text>
                  </Text>
                </Box>
                <Tooltip content="완료 처리">
                  <IconButton size="1" variant="soft" color="green" aria-label="완료 처리" disabled={completing} onClick={() => onComplete(task.taskId)}>
                    <CheckIcon />
                  </IconButton>
                </Tooltip>
              </Flex>
            )
          })}
        </Flex>
      )}
    </Card>
  )
}
