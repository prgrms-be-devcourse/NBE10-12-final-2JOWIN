import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Box, Button, Card, Dialog, Flex, Grid, Skeleton, Table, Text, TextField } from '@radix-ui/themes'
import { CalendarIcon } from '@radix-ui/react-icons'
import { BackLink, ErrorCallout, Field, Money, NotFound, PageHeader } from '../../../shared/ui'
import { ApiError, codeOf } from '../../../shared/api/client'
import { date, dateTime } from '../../../shared/lib/format'
import type { OrderDetailResponse, OrderScheduleRequest } from '../../../shared/api/types'
import { useOrderDetail, useScheduleOrder } from '../hooks'

/**
 * 주문 상세 (OD-09·10 · 10 §3.1 "주문 → 상세") — 스냅샷 품목 + 금액 3분리 + 착수일·납기.
 * 품목은 FK 없는 값 복사(OD-04)라 카탈로그 단가가 바뀌어도 움직이지 않는다 — PB-04의 증명 지점.
 * 주문은 기록의 종착점이다 (Q-09, GP-04) — 여기서 할 수 있는 일은 날짜 기록뿐이다.
 */
export function OrderDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { data: order, isPending, error, refetch } = useOrderDetail(id)
  const schedule = useScheduleOrder(id)
  const [editing, setEditing] = useState(false)

  if (isPending) return <DetailSkeleton />
  if (error || !order) {
    return (
      <>
        <BackLink to="/orders" label="주문" />
        <NotFound code={codeOf(error)} backLabel="주문 목록으로" onBack={() => navigate('/orders')} onRetry={() => refetch()} />
      </>
    )
  }

  return (
    <Box className="enter-fade">
      <BackLink to="/orders" label="주문" />
      <PageHeader
        title={order.orderNo}
        description={
          <>
            {order.customerName} · <Link to={`/deals/${order.dealId}`} style={{ color: 'inherit' }}>{order.dealTitle}</Link>
            {' · '}견적 <Link to={`/quotes/${order.quoteId}`} style={{ color: 'inherit' }}>{order.quoteNo}</Link>
            {' · '}{dateTime(order.createdAt)} 전환
          </>
        }
        actions={
          <Button variant="soft" onClick={() => setEditing(true)}>
            <CalendarIcon /> 착수일 · 납기 기록
          </Button>
        }
      />

      <Grid columns={{ initial: '1', sm: '3' }} gap="3" mb="5">
        <Card size="2">
          <Text as="div" size="1" color="gray" mb="1">합계</Text>
          <Money value={order.totalAmount} unit size="4" weight="bold" />
        </Card>
        <Card size="2">
          <Text as="div" size="1" color="gray" mb="1">착수일</Text>
          <Text size="3" weight={order.startDate ? 'medium' : 'regular'} color={order.startDate ? undefined : 'gray'}>
            {order.startDate ? date(`${order.startDate}T00:00:00Z`) : '미정'}
          </Text>
        </Card>
        <Card size="2">
          <Text as="div" size="1" color="gray" mb="1">납기</Text>
          <Text size="3" weight={order.deliveryDate ? 'medium' : 'regular'} color={order.deliveryDate ? undefined : 'gray'}>
            {order.deliveryDate ? date(`${order.deliveryDate}T00:00:00Z`) : '미정'}
          </Text>
        </Card>
      </Grid>

      <Card size="3">
        <Text as="div" size="1" color="gray" mb="2" style={{ paddingInline: 12 }}>전환 시점 스냅샷 — 이후 카탈로그가 바뀌어도 이 값은 움직이지 않습니다 (OD-04)</Text>
        <Table.Root variant="ghost" size="2">
          <Table.Header>
            <Table.Row>
              <Table.ColumnHeaderCell>품목</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell width="80px">단위</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell width="80px" align="right">수량</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell width="140px" align="right">단가</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell width="140px" align="right">금액</Table.ColumnHeaderCell>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {order.items.map((it, i) => (
              <Table.Row key={`${it.name}-${i}`}>
                <Table.RowHeaderCell><Text size="2" weight="medium">{it.name}</Text></Table.RowHeaderCell>
                <Table.Cell><Text color="gray">{it.unit}</Text></Table.Cell>
                <Table.Cell align="right">{it.quantity}</Table.Cell>
                <Table.Cell align="right"><Money value={it.unitPrice} /></Table.Cell>
                <Table.Cell align="right"><Money value={it.amount} /></Table.Cell>
              </Table.Row>
            ))}
          </Table.Body>
        </Table.Root>
        <Flex direction="column" align="end" gap="1" mt="4" px="3">
          <Flex align="baseline" gap="4"><Text size="2" color="gray">공급가액</Text><Money value={order.supplyAmount} size="3" /></Flex>
          <Flex align="baseline" gap="4"><Text size="2" color="gray">부가세</Text><Money value={order.vatAmount} size="3" /></Flex>
          <Flex align="baseline" gap="4" mt="1"><Text size="2" color="gray">합계</Text><Money value={order.totalAmount} unit size="5" weight="bold" /></Flex>
        </Flex>
      </Card>

      <ScheduleDialog
        open={editing}
        onOpenChange={(o) => { setEditing(o); if (!o) schedule.reset() }}
        order={order}
        loading={schedule.isPending}
        error={schedule.error}
        onSubmit={(body) => schedule.mutate(body, { onSuccess: () => setEditing(false) })}
      />
    </Box>
  )
}

/** 착수일·납기 기록 (OD-10) — 되돌릴 수 있는 입력이라 Dialog */
function ScheduleDialog({
  open, onOpenChange, order, loading, error, onSubmit,
}: { open: boolean; onOpenChange: (o: boolean) => void; order: OrderDetailResponse; loading: boolean; error: unknown; onSubmit: (body: OrderScheduleRequest) => void }) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content maxWidth="400px">
        {open && <ScheduleForm order={order} loading={loading} error={error} onSubmit={onSubmit} />}
      </Dialog.Content>
    </Dialog.Root>
  )
}

function ScheduleForm({ order, loading, error, onSubmit }: { order: OrderDetailResponse; loading: boolean; error: unknown; onSubmit: (body: OrderScheduleRequest) => void }) {
  const [startDate, setStartDate] = useState(order.startDate ?? '')
  const [deliveryDate, setDeliveryDate] = useState(order.deliveryDate ?? '')
  const apiError = error instanceof ApiError ? error : null
  const invalid = Boolean(startDate && deliveryDate && deliveryDate < startDate)
  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    onSubmit({ startDate: startDate || null, deliveryDate: deliveryDate || null })
  }
  return (
    <>
      <Dialog.Title>착수일 · 납기 기록</Dialog.Title>
      <Dialog.Description size="2" color="gray">{order.orderNo} · 비워 두면 미정으로 남습니다.</Dialog.Description>
      <form onSubmit={handleSubmit}>
        <Flex direction="column" gap="4" mt="4">
          <Field label="착수일" error={apiError?.reasonOf('startDate')}>
            <TextField.Root type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} disabled={loading} />
          </Field>
          <Field label="납기" error={apiError?.reasonOf('deliveryDate') ?? (invalid ? '납기는 착수일 이후여야 합니다.' : undefined)}>
            <TextField.Root type="date" min={startDate || undefined} value={deliveryDate} onChange={(e) => setDeliveryDate(e.target.value)} disabled={loading} color={invalid ? 'red' : undefined} />
          </Field>
          {apiError && apiError.code !== 'VALIDATION_FAILED' && <ErrorCallout code={apiError.code} />}
          <Flex gap="3" justify="end" mt="2">
            <Dialog.Close>
              <Button type="button" variant="soft" color="gray" disabled={loading}>취소</Button>
            </Dialog.Close>
            <Button type="submit" loading={loading} disabled={invalid}>저장</Button>
          </Flex>
        </Flex>
      </form>
    </>
  )
}

function DetailSkeleton() {
  return (
    <Box>
      <Skeleton height="20px" width="60px" mb="3" />
      <Skeleton height="32px" width="240px" mb="5" />
      <Grid columns={{ initial: '1', sm: '3' }} gap="3" mb="5">
        {[0, 1, 2].map((i) => <Skeleton key={i} height="72px" />)}
      </Grid>
      <Skeleton height="280px" />
    </Box>
  )
}
