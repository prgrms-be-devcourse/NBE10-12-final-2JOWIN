import type { ReactNode } from 'react'
import { Avatar, Badge, Box, Card, Flex, Heading, Separator, Table, Text } from '@radix-ui/themes'
import { ClockIcon, EnvelopeClosedIcon, MobileIcon } from '@radix-ui/react-icons'
import { Money } from '../../../shared/ui'
import { dateShort, daysUntil } from '../../../shared/lib/format'
import type { PublicQuoteResponse } from '../../../shared/api/types'

interface Props {
  quote: PublicQuoteResponse
  /** 남은 기간 배지 — 응답 가능한 문서에만 (고객 화면). 미리보기에서도 그대로 보여준다 */
  showRemaining?: boolean
  /** 문서 아래 자리 — 고객 화면은 응답 버튼 3개, 미리보기는 "편집으로 돌아가기" */
  children?: ReactNode
}

/**
 * 견적서 문서 — 고객 열람 페이지(10 §5.6)와 구성원 미리보기(QT-12)가 **같은 컴포넌트**를 쓴다.
 * "고객 화면과 동일한 구성"을 코드로 보장하는 지점이다. 응답은 PublicQuoteResponse 하나로 통일 (08 §C 주석).
 *
 *  - 발신 회사를 최상단에 크게 — 누가 보냈는지 0.5초 안에 (GAP-05)
 *  - 담당자는 Deal의 현재 담당자 (AP-18)
 *  - 금액 3분리 — 공급가액·부가세·합계 (QT-25)
 */
export function QuoteDocument({ quote, showRemaining = true, children }: Props) {
  const vatExcluded = quote.vatMode === 'EXCLUDED'
  const remaining = daysUntil(`${quote.validUntil}T00:00:00Z`)

  return (
    <Card size="4" className="enter-slow">
      {/* 발신 회사 — 고객이 가장 먼저 봐야 하는 것 (GAP-05) */}
      <Flex direction="column" align="center" gap="2" py="4">
        <Avatar size="4" radius="full" color="blue" fallback={quote.companyName.slice(0, 1)} />
        <Heading size="5">{quote.companyName}</Heading>
        <Text size="2" color="gray">
          사업자등록번호 {quote.companyBusinessNo}
        </Text>
      </Flex>

      <Separator size="4" my="4" />

      <Flex justify="between" align="start" wrap="wrap" gap="3" mb="4">
        <Box>
          <Heading size="6">견적서</Heading>
          <Text as="div" size="2" color="gray" mt="1">
            유효기간 {dateShort(`${quote.validUntil}T00:00:00Z`)}
          </Text>
        </Box>
        <Flex direction="column" align="end" gap="2">
          <Text size="3" weight="medium" style={{ fontVariantNumeric: 'tabular-nums' }}>
            {quote.quoteNo}
          </Text>
          {showRemaining && (
            <Badge color={remaining < 0 ? 'red' : 'amber'} variant="soft" radius="full">
              <ClockIcon />
              {remaining > 0 ? `${remaining}일 남음` : remaining === 0 ? '오늘 마감' : `${-remaining}일 지남`}
            </Badge>
          )}
        </Flex>
      </Flex>

      <Box style={{ overflowX: 'auto' }}>
        <Table.Root variant="surface" size="2">
          <Table.Header>
            <Table.Row>
              <Table.ColumnHeaderCell>품목</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>단위</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell align="right">수량</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell align="right">단가</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell align="right">금액</Table.ColumnHeaderCell>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {quote.items.map((item, index) => (
              <Table.Row key={`${item.name}-${index}`}>
                <Table.RowHeaderCell>{item.name}</Table.RowHeaderCell>
                <Table.Cell>
                  <Text color="gray">{item.unit}</Text>
                </Table.Cell>
                <Table.Cell align="right">{item.quantity}</Table.Cell>
                <Table.Cell align="right">
                  <Money value={item.unitPrice} />
                </Table.Cell>
                <Table.Cell align="right">
                  <Money value={item.amount} />
                </Table.Cell>
              </Table.Row>
            ))}
          </Table.Body>
        </Table.Root>
      </Box>

      {/* 금액 3분리 — 합계만 Heading 크기로 (QT-25) */}
      <Flex direction="column" align="end" gap="1" mt="4">
        <AmountRow label="공급가액" value={quote.supplyAmount} />
        <AmountRow label={`부가세${vatExcluded ? ' (별도)' : ' (포함)'}`} value={quote.vatAmount} />
        <Flex align="baseline" gap="4" mt="1">
          <Text size="2" color="gray">
            합계
          </Text>
          <Money value={quote.totalAmount} unit size="6" weight="bold" />
        </Flex>
      </Flex>

      {quote.terms && (
        <Text as="p" size="2" color="gray" mt="4" style={{ whiteSpace: 'pre-wrap' }}>
          {quote.terms}
        </Text>
      )}

      {/* 담당자 — Deal의 현재 담당자 (AP-18) */}
      <Card variant="surface" mt="5" style={{ background: 'var(--blue-2)' }}>
        <Text as="div" size="1" color="gray" mb="2">
          문의하실 곳
        </Text>
        <Flex align="center" gap="3" wrap="wrap">
          <Avatar size="2" radius="full" color="blue" fallback={quote.assignee.name.slice(0, 1) || '?'} />
          <Text size="2" weight="medium">
            {quote.assignee.name}
          </Text>
          <Flex align="center" gap="1">
            <MobileIcon color="var(--gray-9)" />
            <Text size="2" color="gray">
              {quote.assignee.phone}
            </Text>
          </Flex>
          <Flex align="center" gap="1">
            <EnvelopeClosedIcon color="var(--gray-9)" />
            <Text size="2" color="gray">
              {quote.assignee.email}
            </Text>
          </Flex>
        </Flex>
      </Card>

      {children}
    </Card>
  )
}

function AmountRow({ label, value }: { label: string; value: number }) {
  return (
    <Flex align="baseline" gap="4">
      <Text size="2" color="gray">
        {label}
      </Text>
      <Money value={value} size="3" />
    </Flex>
  )
}
