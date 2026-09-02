import { Flex, Heading, Text } from '@radix-ui/themes'
import type { ReactNode } from 'react'

interface Props {
  title: string
  /** 제목 옆 배지 — 상태를 제목 높이에서 읽게 한다 */
  badge?: ReactNode
  /** 제목 아래 한 줄 설명 */
  description?: ReactNode
  /** 오른쪽 액션 버튼 자리 */
  actions?: ReactNode
}

/** 화면 제목 · 배지 · 액션의 고정 자리 — 화면마다 다른 높이로 시작하지 않게 한다 */
export function PageHeader({ title, badge, description, actions }: Props) {
  return (
    <Flex justify="between" align="start" gap="4" mb="5">
      <Flex direction="column" gap="1">
        <Flex align="center" gap="2">
          <Heading size="7" style={{ letterSpacing: '-0.01em' }}>
            {title}
          </Heading>
          {badge}
        </Flex>
        {description && (
          <Text size="2" color="gray">
            {description}
          </Text>
        )}
      </Flex>
      {actions && (
        <Flex gap="2" align="center" style={{ flexShrink: 0 }}>
          {actions}
        </Flex>
      )}
    </Flex>
  )
}
