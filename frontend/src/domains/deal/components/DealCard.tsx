import { Card, Flex, Text } from '@radix-ui/themes'
import { useNavigate } from 'react-router'
import { Money } from '../../../shared/ui'
import { daysUntil, remainingText } from '../../../shared/lib/format'
import type { DealResponse } from '../../../shared/api/types'

/**
 * 딜 보드 카드 (10 §5.2) — 고객사 · 제목 · 금액 · 담당자 · 마감 D-N.
 * 금액은 성사 후 주문 합계, 그 전에는 예상 금액 (DL-18). 마감 임박(3일 이내)·초과는 red.
 * 드래그 이동은 미지원(10 §9-3) — 단계 이동은 상세에서만.
 */
export function DealCard({ deal }: { deal: DealResponse }) {
  const navigate = useNavigate()
  const won = deal.stage === 'WON'
  const amount = won ? deal.wonAmount : deal.expectedAmount
  const remaining = deal.dueDate && !won && deal.stage !== 'LOST' ? daysUntil(`${deal.dueDate}T00:00:00Z`) : null
  const urgent = remaining !== null && remaining <= 3

  const open = () => navigate(`/deals/${deal.id}`)

  return (
    <Card
      size="1"
      className="lift"
      role="link"
      tabIndex={0}
      aria-label={`${deal.title} 상세`}
      style={{ cursor: 'pointer' }}
      onClick={open}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          open()
        }
      }}
    >
      <Flex direction="column" gap="1">
        <Text size="1" color="gray" truncate>
          {deal.customerName}
        </Text>
        <Text size="2" weight="medium" style={{ lineHeight: 1.35 }}>
          {deal.title}
        </Text>
        <Flex align="baseline" gap="1" mt="1">
          {amount === null ? (
            <Text size="2" color="gray">
              금액 미정
            </Text>
          ) : (
            <>
              <Money value={amount} short size="2" weight="bold" color={won ? 'green' : undefined} />
              <Text size="1" color="gray">
                {won ? '주문 합계' : '예상'}
              </Text>
            </>
          )}
        </Flex>
        <Flex align="center" justify="between" gap="2" mt="1">
          <Text size="1" color="gray" truncate>
            {deal.assigneeMemberName}
          </Text>
          {remaining !== null && (
            <Text size="1" color={urgent ? 'red' : 'gray'} weight={urgent ? 'medium' : 'regular'} style={{ flexShrink: 0 }}>
              {remaining > 0 ? `D-${remaining}` : remaining === 0 ? 'D-Day' : remainingText(`${deal.dueDate}T00:00:00Z`)}
            </Text>
          )}
        </Flex>
      </Flex>
    </Card>
  )
}
