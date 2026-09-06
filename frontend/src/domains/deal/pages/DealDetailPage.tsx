import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useMutation } from '@tanstack/react-query'
import { Badge, Box, Button, Card, DropdownMenu, Flex, Grid, IconButton, Skeleton, Table, Tabs, Text } from '@radix-ui/themes'
import { DotsHorizontalIcon, FileTextIcon, Pencil1Icon, PersonIcon, PlusIcon, ResetIcon, TrashIcon } from '@radix-ui/react-icons'
import {
  BackLink, ConfirmDialog, DealStageBadge, ErrorCallout, Money, NotFound, PageHeader, QuoteStatusBadge, RemainingBadge, isOpenStage,
} from '../../../shared/ui'
import { ApiError, codeOf } from '../../../shared/api/client'
import { date, dateShort } from '../../../shared/lib/format'
import type { DealDetailResponse } from '../../../shared/api/types'
import { hasCompanyWideScope, useSession } from '../../../app/session'
import { createQuote } from '../../quote/api'
import { DealTimeline } from '../../activity/components/DealTimeline'
import { useDealDetail, useDealMutations } from '../hooks'
import { StageStepper } from '../components/StageStepper'
import { DealFormDialog } from '../components/DealFormDialog'
import { LoseDealDialog } from '../components/LoseDealDialog'
import { ChangeAssigneeDialog } from '../components/ChangeAssigneeDialog'

/**
 * 딜 상세 — 작업의 허브 (DL-15·18, 10 §5.3).
 *
 * - 상단은 요약(고객사·담당·마감·금액), 이력은 타임라인 탭 하나로 통합 (GAP-10)
 * - 견적·주문은 DealDetailResponse의 요약 목록만 — 활동 이력 전체는 /deals/{id}/activities (v1.6.3)
 * - 단계 이동은 허용 전이만 버튼으로 (전이표 §5) · 모든 변경에 version, STALE_VERSION은 새로고침 유도
 * - 견적 작성·발송·복제는 진행 중(리드~협상) 딜에서만 (Q-25) — 종결 딜에서는 버튼을 숨긴다
 * - 담당자 변경은 기업 관리자만 (DL-05) · 404는 존재 여부를 구별하지 않는다 (SC-09)
 */
export function DealDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const session = useSession()
  const { data: deal, isPending, error, refetch } = useDealDetail(id)
  const m = useDealMutations(id)
  const startQuote = useMutation({ mutationFn: () => createQuote({ dealId: id }) })

  const [dialog, setDialog] = useState<'edit' | 'lose' | 'assignee' | 'delete' | null>(null)
  const close = () => {
    setDialog(null)
    m.update.reset()
    m.lose.reset()
    m.changeAssignee.reset()
    m.remove.reset()
  }

  if (isPending) return <DetailSkeleton />
  if (error || !deal) {
    return (
      <>
        <BackLink to="/deals" label="딜" />
        <NotFound code={codeOf(error)} backLabel="딜 보드로" onBack={() => navigate('/deals')} onRetry={() => refetch()} />
      </>
    )
  }

  const open = isOpenStage(deal.stage)
  const won = deal.stage === 'WON'
  const busy = m.advance.isPending || m.revert.isPending || m.reopen.isPending
  const stageError = m.advance.error ?? m.revert.error ?? m.reopen.error ?? startQuote.error ?? m.remove.error
  const version = { version: deal.version }

  return (
    <Box className="enter-fade">
      <BackLink to="/deals" label="딜" />
      <PageHeader
        title={deal.title}
        badge={<DealStageBadge stage={deal.stage} current size="2" />}
        description={<Summary deal={deal} />}
        actions={
          <>
            {open && (
              <Button onClick={() => startQuote.mutate(undefined, { onSuccess: (created) => navigate(`/quotes/${created.id}`) })} loading={startQuote.isPending}>
                <PlusIcon /> 견적 작성
              </Button>
            )}
            <DropdownMenu.Root>
              <DropdownMenu.Trigger>
                <IconButton variant="soft" color="gray" aria-label="더 보기">
                  <DotsHorizontalIcon />
                </IconButton>
              </DropdownMenu.Trigger>
              <DropdownMenu.Content align="end">
                <DropdownMenu.Item onSelect={() => setDialog('edit')}>
                  <Pencil1Icon /> 수정
                </DropdownMenu.Item>
                {hasCompanyWideScope(session) && (
                  <DropdownMenu.Item onSelect={() => setDialog('assignee')}>
                    <PersonIcon /> 담당자 변경
                  </DropdownMenu.Item>
                )}
                {deal.stage === 'LOST' && (
                  <DropdownMenu.Item onSelect={() => m.reopen.mutate(version)}>
                    <ResetIcon /> 재개
                  </DropdownMenu.Item>
                )}
                <DropdownMenu.Separator />
                <DropdownMenu.Item color="red" onSelect={() => setDialog('delete')}>
                  <TrashIcon /> 삭제
                </DropdownMenu.Item>
              </DropdownMenu.Content>
            </DropdownMenu.Root>
          </>
        }
      />

      {stageError && <ErrorCallout code={codeOf(stageError)} onRetry={() => refetch()} />}

      {/* 단계 */}
      <Card size="2" mb="4">
        <StageStepper
          stage={deal.stage}
          lostReason={deal.lostReason}
          busy={busy}
          onAdvance={() => m.advance.mutate(version)}
          onRevert={() => m.revert.mutate(version)}
          onLose={() => setDialog('lose')}
          onReopen={() => m.reopen.mutate(version)}
        />
      </Card>

      {/* 타임라인 · 견적 · 주문 */}
      <Card size="3">
        <Tabs.Root defaultValue="timeline">
          <Tabs.List>
            <Tabs.Trigger value="timeline">타임라인</Tabs.Trigger>
            <Tabs.Trigger value="quotes">
              견적
              <Badge color="gray" variant="soft" size="1" ml="1">
                {deal.quotes.length}
              </Badge>
            </Tabs.Trigger>
            <Tabs.Trigger value="orders">
              주문
              <Badge color="gray" variant="soft" size="1" ml="1">
                {deal.orders.length}
              </Badge>
            </Tabs.Trigger>
          </Tabs.List>
          <Box pt="4">
            <Tabs.Content value="timeline">
              <DealTimeline dealId={deal.id} />
            </Tabs.Content>
            <Tabs.Content value="quotes">
              <QuoteSummaryTable deal={deal} canCreate={open} creating={startQuote.isPending} onCreate={() => startQuote.mutate(undefined, { onSuccess: (created) => navigate(`/quotes/${created.id}`) })} />
            </Tabs.Content>
            <Tabs.Content value="orders">
              <OrderSummaryTable deal={deal} won={won} />
            </Tabs.Content>
          </Box>
        </Tabs.Root>
      </Card>

      {/* ── 다이얼로그 */}
      <DealFormDialog
        mode="edit"
        open={dialog === 'edit'}
        onOpenChange={(next) => !next && close()}
        deal={deal}
        loading={m.update.isPending}
        error={m.update.error}
        onSubmit={(body) => m.update.mutate(body, { onSuccess: close })}
      />
      <LoseDealDialog
        open={dialog === 'lose'}
        onOpenChange={(next) => !next && close()}
        title={deal.title}
        loading={m.lose.isPending}
        error={m.lose.error}
        onRetry={() => refetch()}
        onConfirm={(reason) => m.lose.mutate({ reason, ...version }, { onSuccess: close })}
      />
      <ChangeAssigneeDialog
        open={dialog === 'assignee'}
        onOpenChange={(next) => !next && close()}
        currentAssigneeId={deal.assigneeMemberId}
        currentAssigneeName={deal.assigneeMemberName}
        loading={m.changeAssignee.isPending}
        error={m.changeAssignee.error}
        onRetry={() => refetch()}
        onConfirm={(assigneeMemberId) => m.changeAssignee.mutate({ assigneeMemberId, ...version }, { onSuccess: close })}
      />
      <ConfirmDialog
        open={dialog === 'delete'}
        onOpenChange={(next) => !next && close()}
        title={`${deal.title}을(를) 삭제하시겠습니까?`}
        description="견적이 연결된 딜은 삭제할 수 없습니다. 삭제한 딜은 보드와 고객사 이력에서 사라집니다."
        confirmLabel="삭제"
        confirmColor="red"
        loading={m.remove.isPending}
        onConfirm={() => m.remove.mutate(undefined, { onSuccess: () => navigate('/deals', { replace: true }) })}
      >
        {m.remove.error instanceof ApiError && <ErrorCallout code={m.remove.error.code} />}
      </ConfirmDialog>
    </Box>
  )
}

/** 제목 밑 한 줄 — 고객사 · 담당 · 마감 · 금액 (성사 후 주문 합계, DL-18) */
function Summary({ deal }: { deal: DealDetailResponse }) {
  const won = deal.stage === 'WON'
  return (
    <Flex align="center" gap="2" wrap="wrap">
      <Link to={`/customers/${deal.customerId}`} style={{ color: 'inherit' }}>
        {deal.customerName}
      </Link>
      <Text color="gray">·</Text>
      <Text>담당 {deal.assigneeMemberName}</Text>
      {deal.dueDate && (
        <>
          <Text color="gray">·</Text>
          <Text>마감 {date(`${deal.dueDate}T00:00:00Z`)}</Text>
          {isOpenStage(deal.stage) && <RemainingBadge until={`${deal.dueDate}T00:00:00Z`} />}
        </>
      )}
      <Text color="gray">·</Text>
      {won && deal.wonAmount !== null ? (
        <Text>
          성사 <Money value={deal.wonAmount} unit weight="bold" color="green" />
        </Text>
      ) : deal.expectedAmount === null ? (
        <Text color="gray">예상 금액 미정</Text>
      ) : (
        <Text>
          예상 <Money value={deal.expectedAmount} unit weight="medium" />
        </Text>
      )}
    </Flex>
  )
}

/** 견적 요약 (DL-15) — DealDetailResponse.QuoteSummary. 상세는 견적 화면이 담당 */
function QuoteSummaryTable({ deal, canCreate, creating, onCreate }: { deal: DealDetailResponse; canCreate: boolean; creating: boolean; onCreate: () => void }) {
  if (deal.quotes.length === 0) {
    return (
      <Box py="5">
        <Flex direction="column" align="center" gap="3">
          <Text size="2" color="gray" align="center">
            {canCreate ? '아직 견적이 없습니다. 카탈로그에서 항목을 골라 첫 견적을 만들어 보세요.' : '종결된 딜에는 견적을 작성할 수 없습니다. 추가 거래는 새 딜로 진행합니다 (Q-25).'}
          </Text>
          {canCreate && (
            <Button variant="soft" onClick={onCreate} loading={creating}>
              <PlusIcon /> 견적 작성
            </Button>
          )}
        </Flex>
      </Box>
    )
  }
  return (
    <Table.Root variant="ghost" size="2">
      <Table.Header>
        <Table.Row>
          <Table.ColumnHeaderCell>견적번호</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell width="120px">상태</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell align="right" width="160px">
            합계
          </Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell width="100px" align="center">
            발송일
          </Table.ColumnHeaderCell>
        </Table.Row>
      </Table.Header>
      <Table.Body>
        {deal.quotes.map((q) => (
          <Table.Row key={q.id} className="row-hover">
            <Table.RowHeaderCell>
              <Text asChild size="2" weight="medium">
                <Link to={`/quotes/${q.id}`} style={{ color: 'inherit', textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                  <FileTextIcon /> {q.quoteNo}
                </Link>
              </Text>
            </Table.RowHeaderCell>
            <Table.Cell>
              <QuoteStatusBadge status={q.status} />
            </Table.Cell>
            <Table.Cell align="right">
              <Money value={q.totalAmount} unit />
            </Table.Cell>
            <Table.Cell align="center">
              <Text size="2" color="gray">
                {q.sentAt ? dateShort(q.sentAt) : '—'}
              </Text>
            </Table.Cell>
          </Table.Row>
        ))}
      </Table.Body>
    </Table.Root>
  )
}

/** 주문 요약 — DealDetailResponse.OrderSummary. 성사 금액 = 주문 합계 (DL-18) */
function OrderSummaryTable({ deal, won }: { deal: DealDetailResponse; won: boolean }) {
  if (deal.orders.length === 0) {
    return (
      <Box py="5">
        <Text as="p" size="2" color="gray" align="center">
          {won ? '주문이 없습니다.' : '승인된 견적을 주문으로 전환하면 여기에 쌓이고, 딜은 성사로 바뀝니다 (OD-06).'}
        </Text>
      </Box>
    )
  }
  return (
    <Table.Root variant="ghost" size="2">
      <Table.Header>
        <Table.Row>
          <Table.ColumnHeaderCell>주문번호</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell align="right" width="160px">
            합계
          </Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell width="100px" align="center">
            전환일
          </Table.ColumnHeaderCell>
        </Table.Row>
      </Table.Header>
      <Table.Body>
        {deal.orders.map((o) => (
          <Table.Row key={o.id} className="row-hover">
            <Table.RowHeaderCell>
              <Text asChild size="2" weight="medium">
                <Link to={`/orders/${o.id}`} style={{ color: 'inherit', textDecoration: 'none' }}>
                  {o.orderNo}
                </Link>
              </Text>
            </Table.RowHeaderCell>
            <Table.Cell align="right">
              <Money value={o.totalAmount} unit />
            </Table.Cell>
            <Table.Cell align="center">
              <Text size="2" color="gray">
                {dateShort(o.createdAt)}
              </Text>
            </Table.Cell>
          </Table.Row>
        ))}
      </Table.Body>
    </Table.Root>
  )
}

function DetailSkeleton() {
  return (
    <Box>
      <Skeleton height="20px" width="40px" mb="3" />
      <Skeleton height="32px" width="320px" mb="2" />
      <Skeleton height="18px" width="420px" mb="5" />
      <Skeleton height="64px" mb="4" />
      <Grid columns="1" gap="3">
        <Skeleton height="280px" />
      </Grid>
    </Box>
  )
}
