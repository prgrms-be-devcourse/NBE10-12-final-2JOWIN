import { Card, Flex, Heading, Text } from '@radix-ui/themes'
import { Money } from '../../../shared/ui'
import { moneyShort } from '../../../shared/lib/format'
import { DEAL_STAGE_LABEL, OPEN_DEAL_STAGES } from '../../../shared/ui/status'
import type { DashboardSummaryResponse } from '../../../shared/api/types'
import { BarChart } from './BarChart'

/**
 * 단계별 예상 금액 (DB-01) — 파이프라인 4칸의 숫자를 크기로 보여준다.
 * 값은 `DashboardSummaryResponse.pipeline`(count · expectedAmountSum) 그대로. 단계 순서는 전이표 순서(Q-11).
 * 성사·실패는 파이프라인이 아니다 — 성사는 "이달 성사", 실패는 제외 (v1.6.1).
 */
export function PipelineChart({ pipeline }: { pipeline: DashboardSummaryResponse['pipeline'] }) {
  const byStage = new Map(pipeline.map((p) => [p.stage, p]))
  const total = pipeline.reduce((sum, p) => sum + p.expectedAmountSum, 0)
  const rows = OPEN_DEAL_STAGES.map((stage) => {
    const row = byStage.get(stage)
    const amount = row?.expectedAmountSum ?? 0
    const count = row?.count ?? 0
    return {
      key: stage,
      label: DEAL_STAGE_LABEL[stage],
      sublabel: `${count}건`,
      value: amount,
      valueLabel: moneyShort(amount),
      href: `/deals?stage=${stage}`,
      tooltip: (
        <Text size="1">
          {DEAL_STAGE_LABEL[stage]} · {count}건 · <Money value={amount} unit size="1" />
        </Text>
      ),
    }
  })

  return (
    <Card size="3" style={{ height: '100%' }}>
      <Flex align="baseline" justify="between" mb="3">
        <Heading size="3">단계별 예상 금액</Heading>
        <Text size="1" color="gray">
          진행 중 합계 <Money value={total} short size="1" weight="medium" />
        </Text>
      </Flex>
      {total === 0 ? (
        <Text as="p" size="2" color="gray" align="center" my="5">
          진행 중인 딜의 예상 금액이 없습니다.
        </Text>
      ) : (
        <BarChart rows={rows} labelWidth={56} />
      )}
    </Card>
  )
}
