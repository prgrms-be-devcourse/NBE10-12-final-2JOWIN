import { Box, Card, Flex, Heading, Text } from '@radix-ui/themes'
import { Link } from 'react-router'
import { RemainingBadge, ViewedBadge } from '../../../shared/ui'
import type { DashboardWaitingQuote } from '../../../shared/api/types'
import { SplitBar } from './BarChart'

/**
 * 고객 응답 대기 (DB-03 · 10 §5.1).
 * 열람 배지가 이 카드의 핵심 정보다 — "보냈는데 안 봤다"와 "봤는데 답이 없다"는 취할 행동이 다르다 (AP-06, GAP-08).
 */
export function WaitingQuotes({ quotes }: { quotes: DashboardWaitingQuote[] }) {
  return (
    <Card size="3" style={{ height: '100%' }}>
      <Heading size="3" mb="3">
        고객 응답 대기{' '}
        <Text size="2" color="gray" weight="regular">
          {quotes.length}
        </Text>
      </Heading>
      {/* 열람/미열람 비율 — 색은 ViewedBadge와 같은 뜻(amber=열람 · gray=미열람), 범례에 건수 (§2.6) */}
      {quotes.length > 0 && (
        <Box mb="3">
          <SplitBar
            ariaLabel="응답 대기 견적의 열람 여부"
            parts={[
              { key: 'viewed', label: '열람', value: quotes.filter((q) => q.firstViewedAt).length, color: 'var(--amber-11)' },
              { key: 'unviewed', label: '미열람', value: quotes.filter((q) => !q.firstViewedAt).length, color: 'var(--gray-8)' },
            ]}
          />
        </Box>
      )}
      {quotes.length === 0 ? (
        <Text as="p" size="2" color="gray" align="center" my="5">
          응답을 기다리는 견적이 없습니다.
        </Text>
      ) : (
        <Flex direction="column">
          {quotes.map((quote, index) => (
            <Box asChild key={quote.quoteId} className="row-hover" py="2" style={{ borderTop: index === 0 ? undefined : '1px solid var(--gray-a4)' }}>
              <Link to={`/quotes/${quote.quoteId}`} style={{ textDecoration: 'none', color: 'inherit', display: 'block' }}>
                <Flex align="center" justify="between" gap="3" wrap="wrap">
                  <Box minWidth="0">
                    <Text as="div" size="2" weight="medium" truncate>
                      {quote.customerName}
                    </Text>
                    <Text as="div" size="1" color="gray" style={{ fontVariantNumeric: 'tabular-nums' }}>
                      {quote.quoteNo}
                    </Text>
                  </Box>
                  <Flex gap="2" align="center" wrap="wrap" justify="end">
                    <RemainingBadge until={`${quote.validUntil}T00:00:00Z`} />
                    <ViewedBadge firstViewedAt={quote.firstViewedAt} sentAt={quote.sentAt} />
                  </Flex>
                </Flex>
              </Link>
            </Box>
          ))}
        </Flex>
      )}
    </Card>
  )
}
