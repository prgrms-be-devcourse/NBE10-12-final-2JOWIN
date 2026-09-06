import { useMemo } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { Badge, Button, Card, Flex, Table, Text, TextField } from '@radix-ui/themes'
import { Cross2Icon, CubeIcon } from '@radix-ui/react-icons'
import { EmptyState, ErrorCallout, Money, PageHeader, Pagination, TableSkeleton } from '../../../shared/ui'
import { codeOf } from '../../../shared/api/client'
import { dateShort } from '../../../shared/lib/format'
import type { OrderResponse } from '../../../shared/api/types'
import { useOrderList } from '../hooks'

/**
 * 주문 목록 (OD-08 · 07 §C `GET /orders?from=&to=`) — 담당 스코프는 서버가 가른다 (SC-04).
 * 주문에는 상태가 없다 (Q-09) — 상태 열·취소 버튼이 없는 것이 의도다. 착수일·납기는 날짜 필드다 (OD-10).
 */
const PAGE_SIZE = 20

export function OrderListPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const page = Math.max(0, Number(params.get('page') ?? 0))

  const query = useMemo(() => ({ from: from || undefined, to: to || undefined, page, size: PAGE_SIZE }), [from, to, page])
  const { data, isPending, isFetching, error, refetch } = useOrderList(query)

  const update = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value) merged.set(key, value)
      else merged.delete(key)
    }
    if (!('page' in next)) merged.delete('page')
    setParams(merged, { replace: true })
  }
  const filtered = from !== '' || to !== ''

  return (
    <>
      <PageHeader
        title="주문"
        badge={data && <Badge color="blue" variant="soft" size="2">{data.totalElements}건</Badge>}
        description="승인된 견적을 전환한 기록입니다. 전환 시점의 품목·금액이 그대로 남고, 딜은 성사로 집계됩니다."
      />

      <Flex gap="3" mb="4" align="center" wrap="wrap">
        <Text size="2" color="gray">생성일</Text>
        <TextField.Root type="date" value={from} max={to || undefined} onChange={(e) => update({ from: e.target.value })} aria-label="시작일" />
        <Text size="2" color="gray">~</Text>
        <TextField.Root type="date" value={to} min={from || undefined} onChange={(e) => update({ to: e.target.value })} aria-label="종료일" />
        {filtered && (
          <Button variant="ghost" color="gray" onClick={() => update({ from: '', to: '' })}>
            <Cross2Icon /> 필터 해제
          </Button>
        )}
      </Flex>

      {error && <ErrorCallout code={codeOf(error)} onRetry={() => refetch()} />}

      {isPending ? (
        <TableSkeleton columns={[1, 2, 2, 1, 1, 1, 1]} />
      ) : data && data.content.length === 0 ? (
        filtered ? (
          <EmptyState icon={<CubeIcon width="28" height="28" />} title="기간에 해당하는 주문이 없습니다" action={{ label: '필터 해제', onClick: () => update({ from: '', to: '' }) }} />
        ) : (
          <EmptyState
            icon={<CubeIcon width="28" height="28" />}
            title="아직 주문이 없습니다"
            description="고객이 견적을 승인하면 견적 상세에서 「주문 전환」으로 주문을 만듭니다."
            action={{ label: '견적 목록으로', onClick: () => navigate('/quotes?status=APPROVED') }}
          />
        )
      ) : (
        data && (
          <Card className="enter-fade" style={{ opacity: isFetching ? 0.7 : 1, transition: 'opacity var(--motion-fast)' }}>
            <Table.Root variant="ghost" size="2">
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeaderCell>주문번호</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>고객사 · 딜</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="140px" align="right">합계</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="100px">착수일</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="100px">납기</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="100px">생성일</Table.ColumnHeaderCell>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {data.content.map((order) => (
                  <OrderRow key={order.id} order={order} onOpen={() => navigate(`/orders/${order.id}`)} />
                ))}
              </Table.Body>
            </Table.Root>
            <Pagination data={data} unit="건" onPageChange={(next) => update({ page: String(next) })} />
          </Card>
        )
      )}
    </>
  )
}

function OrderRow({ order, onOpen }: { order: OrderResponse; onOpen: () => void }) {
  return (
    <Table.Row
      className="row-hover"
      tabIndex={0}
      role="link"
      aria-label={`${order.orderNo} 상세`}
      style={{ cursor: 'pointer' }}
      onClick={onOpen}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onOpen()
        }
      }}
    >
      <Table.RowHeaderCell>
        <Text size="2" weight="medium" style={{ fontVariantNumeric: 'tabular-nums' }}>{order.orderNo}</Text>
        <Text as="div" size="1" color="gray">{order.quoteNo}</Text>
      </Table.RowHeaderCell>
      <Table.Cell>
        <Text as="div" size="2" weight="medium">{order.customerName}</Text>
        <Text as="div" size="1" color="gray" truncate style={{ maxWidth: 320 }}>{order.dealTitle}</Text>
      </Table.Cell>
      <Table.Cell align="right"><Money value={order.totalAmount} unit /></Table.Cell>
      <Table.Cell><DateCell value={order.startDate} /></Table.Cell>
      <Table.Cell><DateCell value={order.deliveryDate} /></Table.Cell>
      <Table.Cell><Text size="2" color="gray">{dateShort(order.createdAt)}</Text></Table.Cell>
    </Table.Row>
  )
}

function DateCell({ value }: { value: string | null }) {
  return <Text size="2" color={value ? undefined : 'gray'}>{value ? dateShort(`${value}T00:00:00Z`) : '미정'}</Text>
}
