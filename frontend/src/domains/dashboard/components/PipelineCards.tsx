import { Card, Flex, Grid, Text } from '@radix-ui/themes'
import { Link } from 'react-router'
import { Money } from '../../../shared/ui'
import { DEAL_STAGE_LABEL, OPEN_DEAL_STAGES } from '../../../shared/ui/status'
import { PENDING_LABEL, SALES_STATS_PENDING } from '../pending'
import type { DashboardSummaryResponse } from '../../../shared/api/types'

interface Props {
  pipeline: DashboardSummaryResponse['pipeline']
  monthWonAmount: number
  monthWonCount: number
  monthLabel: string
}

/**
 * 파이프라인 4칸 + 이달 성사 (DB-01·02 · 10 §5.1).
 * 진행 단계만 — 성사는 "이달 성사"로, 실패는 제외 (DL-18 정합). 견적 칸만 blue로 강조.
 * 성사 금액은 "벌어들인 돈"이라 red (§2.3 예외 — 집계 숫자에만).
 */
export function PipelineCards({ pipeline, monthWonAmount, monthWonCount, monthLabel }: Props) {
  const byStage = new Map(pipeline.map((p) => [p.stage, p]))
  return (
    <Grid columns={{ initial: '2', sm: '4', md: '6' }} gap="3">
      {OPEN_DEAL_STAGES.map((stage) => {
        const row = byStage.get(stage)
        const highlight = stage === 'QUOTE'
        return (
          <Card key={stage} asChild size="2" style={highlight ? { background: 'var(--accent-a2)', boxShadow: 'inset 0 0 0 1px var(--accent-a5)' } : undefined}>
            <Link to={`/deals?stage=${stage}`} style={{ textDecoration: 'none', color: 'inherit' }}>
              <Flex direction="column" gap="1">
                <Text size="1" color={highlight ? 'blue' : 'gray'} weight="medium">
                  {DEAL_STAGE_LABEL[stage]}
                </Text>
                <Text size="6" weight="bold" style={{ fontVariantNumeric: 'tabular-nums' }}>
                  {row?.count ?? 0}
                </Text>
                <Money value={row?.expectedAmountSum ?? 0} short size="1" color="gray" />
              </Flex>
            </Link>
          </Card>
        )
      })}
      <Card size="2" style={{ gridColumn: 'span 2', background: 'var(--green-a2)', boxShadow: 'inset 0 0 0 1px var(--green-a5)' }}>
        <Flex direction="column" gap="1">
          <Text size="1" color="green" weight="medium">
            이달 성사
          </Text>
          {SALES_STATS_PENDING ? (
            <Text size="4" weight="bold" color="gray">
              {PENDING_LABEL}
            </Text>
          ) : (
            <Money value={monthWonAmount} short size="6" weight="bold" color="red" />
          )}
          <Text size="1" color="gray">
            {SALES_STATS_PENDING ? monthLabel : `${monthWonCount}건 · ${monthLabel}`}
          </Text>
        </Flex>
      </Card>
    </Grid>
  )
}
