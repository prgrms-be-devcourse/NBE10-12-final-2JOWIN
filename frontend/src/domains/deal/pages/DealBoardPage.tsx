import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { Badge, Box, Button, Card, Flex, Grid, Select, Skeleton, Text } from '@radix-ui/themes'
import { ChevronDownIcon, ChevronUpIcon, Cross2Icon, PlusIcon } from '@radix-ui/react-icons'
import { EmptyState, ErrorCallout, Money, PageHeader, DEAL_STAGE_LABEL, type DealStage } from '../../../shared/ui'
import { codeOf } from '../../../shared/api/client'
import type { DealResponse } from '../../../shared/api/types'
import { hasCompanyWideScope, useSession } from '../../../app/session'
import { useCustomerList } from '../../customer/hooks'
import { SELECT_CONTENT } from '../../customer/constants'
import { useCreateDeal, useDealBoard, useMemberOptions } from '../hooks'
import { DealCard } from '../components/DealCard'
import { DealFormDialog } from '../components/DealFormDialog'

/**
 * 딜 보드 (DL-06·13·14, 10 §5.2) — 단계별 5칸(리드·상담·견적·협상·성사) + 접힌 실패 영역.
 *
 * - 단계별로 `GET /deals?stage=`를 나눠 호출한다 (C 결정 — DealController javadoc)
 * - 각 칸: 건수 + 금액 합계. 성사 칸만 주문 합계(green), 나머지는 예상 금액 (DL-18). 견적 칸은 blue 배경
 * - 필터(고객사·담당자)는 URL 쿼리. 담당자 필터는 회사 전체 범위일 때만 — 영업은 선택지가 자기 하나 (10 §3.2)
 * - 드래그 이동 미지원(10 §9-3). 단계 이동은 딜 상세에서만
 */

const COLUMNS: DealStage[] = ['LEAD', 'CONSULT', 'QUOTE', 'NEGOTIATION', 'WON']
const ALL = '__all__'

export function DealBoardPage() {
  const session = useSession()
  const navigate = useNavigate()
  const companyWide = hasCompanyWideScope(session)
  const [params, setParams] = useSearchParams()
  const customerId = params.get('customerId') ?? ''
  const assigneeId = params.get('assigneeId') ?? ''

  const filters = useMemo(() => ({ customerId: customerId || undefined, assigneeId: companyWide && assigneeId ? assigneeId : undefined }), [customerId, assigneeId, companyWide])
  const board = useDealBoard(filters)
  const customers = useCustomerList({ size: 100 })
  const members = useMemberOptions(companyWide)
  const createMutation = useCreateDeal()
  const [creating, setCreating] = useState(false)
  const [showLost, setShowLost] = useState(false)

  const update = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value) merged.set(key, value)
      else merged.delete(key)
    }
    setParams(merged, { replace: true })
  }
  const filtered = customerId !== '' || assigneeId !== ''

  const lost = board.byStage.LOST.data?.content ?? []
  const total = COLUMNS.reduce((sum, stage) => sum + (board.byStage[stage].data?.totalElements ?? 0), 0)
  const empty = !board.isPending && total === 0 && lost.length === 0

  return (
    <>
      <PageHeader
        title="딜"
        badge={!board.isPending && <Badge color="blue" variant="soft" size="2">{total}건</Badge>}
        description={companyWide ? '회사 전체 딜입니다. 단계 이동·견적 작성은 딜 상세에서 합니다.' : '내가 담당하는 딜입니다. 단계 이동·견적 작성은 딜 상세에서 합니다.'}
        actions={
          <Button onClick={() => setCreating(true)}>
            <PlusIcon /> 새 딜
          </Button>
        }
      />

      <Flex gap="3" mb="4" align="center" wrap="wrap">
        <Select.Root value={customerId || ALL} onValueChange={(value) => update({ customerId: value === ALL ? '' : value })}>
          <Select.Trigger placeholder="고객사" style={{ minWidth: 160 }} />
          <Select.Content {...SELECT_CONTENT}>
            <Select.Item value={ALL}>전체 고객사</Select.Item>
            {customers.data?.content.map((c) => (
              <Select.Item key={c.id} value={c.id}>
                {c.name}
              </Select.Item>
            ))}
          </Select.Content>
        </Select.Root>
        {companyWide && (
          <Select.Root value={assigneeId || ALL} onValueChange={(value) => update({ assigneeId: value === ALL ? '' : value })}>
            <Select.Trigger placeholder="담당자" style={{ minWidth: 140 }} />
            <Select.Content {...SELECT_CONTENT}>
              <Select.Item value={ALL}>전체 담당자</Select.Item>
              {members.data?.map((m) => (
                <Select.Item key={m.id} value={m.id}>
                  {m.name}
                </Select.Item>
              ))}
            </Select.Content>
          </Select.Root>
        )}
        {filtered && (
          <Button variant="ghost" color="gray" onClick={() => update({ customerId: '', assigneeId: '' })}>
            <Cross2Icon /> 필터 해제
          </Button>
        )}
      </Flex>

      {board.error && <ErrorCallout code={codeOf(board.error)} onRetry={board.refetch} />}

      {empty ? (
        filtered ? (
          <EmptyState title="조건에 맞는 딜이 없습니다" description="고객사나 담당자 필터를 바꿔 보세요." action={{ label: '필터 해제', onClick: () => update({ customerId: '', assigneeId: '' }) }} />
        ) : (
          <EmptyState
            icon={<PlusIcon width="28" height="28" />}
            title="아직 딜이 없습니다"
            description="고객사에서 시작된 거래를 딜로 만들면 상담·견적·주문이 이 딜에 쌓입니다."
            action={{ label: '첫 딜 만들기', onClick: () => setCreating(true) }}
          />
        )
      ) : (
        <>
          <Box style={{ overflowX: 'auto', opacity: board.isFetching && !board.isPending ? 0.7 : 1, transition: 'opacity var(--motion-fast)' }} className="enter-fade">
            <Grid columns="5" gap="3" style={{ minWidth: 960 }}>
              {COLUMNS.map((stage) => (
                <StageColumn key={stage} stage={stage} deals={board.byStage[stage].data?.content} loading={board.byStage[stage].isPending} />
              ))}
            </Grid>
          </Box>

          {/* 실패(LOST)는 보드에서 빼고 접힌 영역에 (10 §5.2) */}
          <Flex justify="end" mt="4">
            <Button variant="ghost" color="gray" size="2" onClick={() => setShowLost((v) => !v)} disabled={lost.length === 0}>
              실패한 딜 보기 ({board.byStage.LOST.data?.totalElements ?? 0}) {showLost ? <ChevronUpIcon /> : <ChevronDownIcon />}
            </Button>
          </Flex>
          {showLost && lost.length > 0 && (
            <Grid columns={{ initial: '1', sm: '2', md: '3', lg: '5' }} gap="3" mt="2" className="enter-fade">
              {lost.map((deal) => (
                <Box key={deal.id} style={{ opacity: 0.75 }}>
                  <DealCard deal={deal} />
                </Box>
              ))}
            </Grid>
          )}
        </>
      )}

      <DealFormDialog
        mode="create"
        open={creating}
        onOpenChange={(open) => {
          setCreating(open)
          if (!open) createMutation.reset()
        }}
        defaultCustomerId={customerId || undefined}
        loading={createMutation.isPending}
        error={createMutation.error}
        onSubmit={(body) =>
          createMutation.mutate(body, {
            onSuccess: (created) => {
              setCreating(false)
              navigate(`/deals/${created.id}`) // 만든 직후 할 일은 상담 기록·견적이다 — 상세로 보낸다
            },
          })
        }
      />
    </>
  )
}

/** 단계 칸 — 헤더(라벨·건수·금액 합계) + 카드. 견적 칸 blue, 성사 칸 green (10 §5.2) */
function StageColumn({ stage, deals, loading }: { stage: DealStage; deals: DealResponse[] | undefined; loading: boolean }) {
  const won = stage === 'WON'
  const sum = (deals ?? []).reduce((acc, d) => acc + ((won ? d.wonAmount : d.expectedAmount) ?? 0), 0)
  const tone = won ? 'var(--green-a2)' : stage === 'QUOTE' ? 'var(--accent-a2)' : 'var(--gray-a2)'
  const headerColor = won ? 'green' : stage === 'QUOTE' ? 'blue' : 'gray'

  return (
    <Flex direction="column" gap="2" p="2" style={{ background: tone, borderRadius: 'var(--radius-3)', minHeight: 320 }}>
      <Flex align="baseline" justify="between" px="1" pt="1">
        <Flex align="baseline" gap="2">
          <Text size="2" weight="bold" color={headerColor}>
            {DEAL_STAGE_LABEL[stage]}
          </Text>
          <Text size="1" color="gray">
            {loading ? '' : `${deals?.length ?? 0}`}
          </Text>
        </Flex>
        {!loading && sum > 0 && <Money value={sum} short size="1" color={won ? 'green' : 'gray'} weight="medium" />}
      </Flex>
      {loading ? (
        <>
          <Skeleton height="88px" />
          <Skeleton height="88px" />
        </>
      ) : deals && deals.length > 0 ? (
        <Flex direction="column" gap="2" className="stagger">
          {deals.map((deal) => (
            <DealCard key={deal.id} deal={deal} />
          ))}
        </Flex>
      ) : (
        <Card size="1" variant="ghost">
          <Text size="1" color="gray" align="center" as="div">
            {won ? '이달 성사 없음' : '없음'}
          </Text>
        </Card>
      )}
    </Flex>
  )
}
