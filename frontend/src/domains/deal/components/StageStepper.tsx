import { Button, Callout, Flex, Text, Tooltip } from '@radix-ui/themes'
import { ChevronLeftIcon, ChevronRightIcon, CrossCircledIcon, ResetIcon } from '@radix-ui/react-icons'
import { DealStageBadge, DEAL_STAGE_LABEL, NEXT_STAGE, PREVIOUS_STAGE, type DealStage } from '../../../shared/ui'
import { ERROR_MESSAGES } from '../../../shared/api/errors'

interface Props {
  stage: DealStage
  lostReason: string | null
  busy: boolean
  onAdvance: () => void
  onRevert: () => void
  onLose: () => void
  onReopen: () => void
}

/** 진행 단계 5개 — 리드 → 상담 → 견적 → 협상 → 성사. 실패는 스텝이 아니라 별도 상태다 (전이표 §5) */
const STEPS: DealStage[] = ['LEAD', 'CONSULT', 'QUOTE', 'NEGOTIATION', 'WON']

/**
 * 단계 표시 + 이동 버튼 (10 §5.3). 허용 전이만 버튼으로 노출한다 (전이표 §5 — 표에 없는 전이는 전부 불가).
 *
 * - 다음 단계: LEAD·CONSULT·QUOTE에서만. 협상(NEGOTIATION)에서는 비활성 + 안내 — 성사는 주문 전환 자동 (DL-09, DEAL_WON_REQUIRES_ORDER)
 * - 이전 단계: CONSULT·QUOTE·NEGOTIATION에서만. 리드에서는 되돌릴 단계가 없다 (DEAL_NO_PREVIOUS_STAGE)
 * - 실패 처리: 진행 중(리드~협상)에서만 · 재개: 실패(LOST)에서만 · 성사(WON)는 버튼 없음
 */
export function StageStepper({ stage, lostReason, busy, onAdvance, onRevert, onLose, onReopen }: Props) {
  const lost = stage === 'LOST'
  const won = stage === 'WON'
  const canAdvance = NEXT_STAGE[stage] !== undefined
  const canRevert = PREVIOUS_STAGE[stage] !== undefined

  return (
    <Flex direction="column" gap="3">
      <Flex align="center" gap="2" wrap="wrap">
        <Text size="2" color="gray" mr="1">
          단계
        </Text>
        {STEPS.map((step, i) => (
          <Flex key={step} align="center" gap="2">
            {i > 0 && (
              <Text size="1" color="gray" aria-hidden>
                ›
              </Text>
            )}
            <span style={{ opacity: won && step !== 'WON' ? 0.6 : lost ? 0.5 : 1 }}>
              <DealStageBadge stage={step} current={step === stage} />
            </span>
          </Flex>
        ))}
        {lost && (
          <Flex align="center" gap="2" ml="2">
            <Text size="1" color="gray" aria-hidden>
              →
            </Text>
            <DealStageBadge stage="LOST" current />
          </Flex>
        )}

        <Flex gap="2" ml="auto" wrap="wrap">
          {!won && !lost && (
            <>
              <Tooltip content={canRevert ? `${DEAL_STAGE_LABEL[PREVIOUS_STAGE[stage]!]}(으)로` : ERROR_MESSAGES.DEAL_NO_PREVIOUS_STAGE}>
                <Button variant="soft" color="gray" size="2" disabled={!canRevert || busy} onClick={onRevert}>
                  <ChevronLeftIcon /> 이전 단계
                </Button>
              </Tooltip>
              <Tooltip content={canAdvance ? `${DEAL_STAGE_LABEL[NEXT_STAGE[stage]!]}(으)로` : ERROR_MESSAGES.DEAL_WON_REQUIRES_ORDER}>
                <Button variant="soft" size="2" disabled={!canAdvance || busy} onClick={onAdvance}>
                  다음 단계 <ChevronRightIcon />
                </Button>
              </Tooltip>
              <Button variant="soft" color="red" size="2" disabled={busy} onClick={onLose}>
                <CrossCircledIcon /> 실패 처리
              </Button>
            </>
          )}
          {lost && (
            <Button variant="soft" size="2" disabled={busy} onClick={onReopen}>
              <ResetIcon /> 재개
            </Button>
          )}
        </Flex>
      </Flex>

      {lost && (
        <Callout.Root color="gray" size="1">
          <Callout.Icon>
            <CrossCircledIcon />
          </Callout.Icon>
          <Callout.Text>
            실패 처리된 딜입니다{lostReason ? ` — 사유: ${lostReason}` : ''}. 재개하면 실패 직전 단계로 돌아가지만, 만료된 견적·열람 링크는 복원되지 않습니다 (DL-12).
          </Callout.Text>
        </Callout.Root>
      )}
      {stage === 'NEGOTIATION' && (
        <Text size="1" color="gray">
          {ERROR_MESSAGES.DEAL_WON_REQUIRES_ORDER}
        </Text>
      )}
    </Flex>
  )
}
