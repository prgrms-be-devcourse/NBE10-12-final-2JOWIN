import { Button, Card, Flex, Heading, Text } from '@radix-ui/themes'
import type { ReactNode } from 'react'

interface Props {
  /** 무엇이 없는지 */
  title: string
  /** 왜 비어 있고 무엇을 하면 되는지 */
  description?: ReactNode
  /** 다음 행동 — 라벨과 onClick */
  action?: { label: string; onClick: () => void }
  icon?: ReactNode
}

/**
 * 빈 화면 — **"없습니다"가 아니라 다음 행동을 제안한다** (10-screen-design.md §6.2, GAP-01).
 *
 * 가입 직후 관리자가 보는 화면은 전부 비어 있다. 그때 무엇을 해야 하는지 화면이 말하지 않으면
 * 제품을 쓰기 시작할 수가 없다.
 */
export function EmptyState({ title, description, action, icon }: Props) {
  return (
    <Card size="3">
      <Flex direction="column" align="center" gap="3" py="6" px="4">
        {icon && <Text color="gray">{icon}</Text>}
        <Heading size="4" align="center">
          {title}
        </Heading>
        {description && (
          <Text size="2" color="gray" align="center" style={{ maxWidth: 380 }}>
            {description}
          </Text>
        )}
        {action && (
          <Button mt="2" onClick={action.onClick}>
            {action.label}
          </Button>
        )}
      </Flex>
    </Card>
  )
}
