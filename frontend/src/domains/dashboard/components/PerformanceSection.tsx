import { Box, Card, Flex, Grid, Heading, Skeleton, Table, Text, TextField } from '@radix-ui/themes'
import { ErrorCallout, Money } from '../../../shared/ui'
import { DEAL_STAGE_LABEL } from '../../../shared/ui/status'
import { moneyShort } from '../../../shared/lib/format'
import { BarChart } from './BarChart'
import { codeOf } from '../../../shared/api/client'
import { useDashboardPerformance } from '../hooks'
import { PENDING_LABEL, SALES_STATS_PENDING } from '../pending'

interface Props {
  from: string
  to: string
  onRangeChange: (next: { from: string; to: string }) => void
}

/**
 * 담당자별 실적 · 단계별 전환율 — 기업 관리자 전용 (DB-06~08 · 10 §5.1 "관리자" 행).
 * 영업 담당자에게는 이 섹션 자체를 렌더하지 않는다 (§3.2 — 숨긴다, 비활성화하지 않는다).
 * 기간은 from/to 날짜 입력 (DB-08).
 */
export function PerformanceSection({ from, to, onRangeChange }: Props) {
  // 자리표시자인 동안은 호출하지 않는다 — 빈 목록을 받으러 갈 이유가 없다 (#216)
  const { data, isPending, error, refetch } = useDashboardPerformance(from, to, !SALES_STATS_PENDING)
  return (
    <Card size="3">
      <Flex align="center" justify="between" gap="3" wrap="wrap" mb="3">
        <Heading size="3">담당자별 실적</Heading>
        <Flex align="center" gap="2">
          <TextField.Root type="date" size="1" value={from} max={to} aria-label="시작일" onChange={(e) => e.target.value && onRangeChange({ from: e.target.value, to })} />
          <Text size="1" color="gray">
            ~
          </Text>
          <TextField.Root type="date" size="1" value={to} min={from} aria-label="종료일" onChange={(e) => e.target.value && onRangeChange({ from, to: e.target.value })} />
        </Flex>
      </Flex>

      {error && <ErrorCallout code={codeOf(error)} onRetry={() => refetch()} />}

      {SALES_STATS_PENDING ? (
        // 서버가 빈 목록을 주는 동안 "없습니다"로 그리면 실적이 0이라는 거짓말이 된다 (#216)
        <Text as="p" size="2" color="gray" my="4">
          {PENDING_LABEL} — 담당자별 실적과 전환율은 아직 집계되지 않습니다. 준비되면 이 자리에 표시됩니다.
        </Text>
      ) : isPending ? (
        <Flex direction="column" gap="2">
          <Skeleton height="24px" />
          <Skeleton height="24px" />
          <Skeleton height="24px" />
        </Flex>
      ) : (
        data && (
          <Grid columns={{ initial: '1', md: '3fr 2fr' }} gap="4">
            <Box>
              {/* 성사 금액 크기 비교 — 표는 아래에 그대로 둔다 (표 보기는 접근성 요건) */}
              <Text as="div" size="2" weight="medium" mb="2">
                담당자별 성사 금액
              </Text>
              {data.members.length === 0 ? (
                <Text as="p" size="2" color="gray" my="3">
                  구성원이 없습니다.
                </Text>
              ) : data.members.every((m) => m.wonAmount === 0) ? (
                // 전부 0이면 막대는 정보가 없다 — 기간을 넓히라고 말하고 표만 남긴다
                <Text as="p" size="2" color="gray" my="3">
                  이 기간에 성사된 주문이 없습니다. 기간을 넓혀 보세요.
                </Text>
              ) : (
                <Box mb="4">
                  <BarChart
                    color="var(--green-9)"
                    labelWidth={132}
                    rows={[...data.members]
                      .sort((a, b) => b.wonAmount - a.wonAmount)
                      .map((m) => ({
                        key: m.memberId,
                        label: m.name,
                        sublabel: `성사 ${m.wonCount}건 · 진행 ${m.activeDealCount}건`,
                        value: m.wonAmount,
                        valueLabel: moneyShort(m.wonAmount),
                        tooltip: (
                          <Text size="1">
                            {m.name} · 성사 {m.wonCount}건 <Money value={m.wonAmount} unit size="1" /> · 진행 중 {m.activeDealCount}건
                          </Text>
                        ),
                      }))}
                  />
                </Box>
              )}
            <Table.Root variant="ghost" size="1">
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeaderCell>담당자</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell align="right">성사</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell align="right">성사 금액</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell align="right">진행 중</Table.ColumnHeaderCell>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {data.members.map((member) => (
                  <Table.Row key={member.memberId}>
                    <Table.RowHeaderCell>{member.name}</Table.RowHeaderCell>
                    <Table.Cell align="right">{member.wonCount}건</Table.Cell>
                    <Table.Cell align="right">
                      <Money value={member.wonAmount} unit color={member.wonAmount > 0 ? 'red' : 'gray'} />
                    </Table.Cell>
                    <Table.Cell align="right">{member.activeDealCount}건</Table.Cell>
                  </Table.Row>
                ))}
              </Table.Body>
            </Table.Root>
            </Box>

            <Box>
              <Text as="div" size="2" weight="medium" mb="2">
                단계별 전환율
              </Text>
              <Flex direction="column" gap="2">
                {data.conversions.map((conversion) => {
                  const pct = Math.round(conversion.rate * 100)
                  return (
                    <Box key={`${conversion.fromStage}-${conversion.toStage}`}>
                      <Flex justify="between" mb="1">
                        <Text size="1" color="gray">
                          {DEAL_STAGE_LABEL[conversion.fromStage]} → {DEAL_STAGE_LABEL[conversion.toStage]}
                        </Text>
                        <Text size="1" weight="medium" style={{ fontVariantNumeric: 'tabular-nums' }}>
                          {pct}%
                        </Text>
                      </Flex>
                      <Box style={{ height: 6, borderRadius: 3, background: 'var(--gray-a4)', overflow: 'hidden' }}>
                        <Box style={{ width: `${Math.min(100, pct)}%`, height: '100%', background: conversion.toStage === 'WON' ? 'var(--green-9)' : 'var(--accent-9)' }} />
                      </Box>
                    </Box>
                  )
                })}
              </Flex>
            </Box>
          </Grid>
        )
      )}
    </Card>
  )
}
