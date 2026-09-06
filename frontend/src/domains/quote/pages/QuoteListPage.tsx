import { useMemo } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { Badge, Button, Card, Flex, Select, Table, Text } from '@radix-ui/themes'
import { Cross2Icon, FileTextIcon } from '@radix-ui/react-icons'
import { EmptyState, ErrorCallout, Money, PageHeader, Pagination, QuoteStatusBadge, RemainingBadge, TableSkeleton, ViewedBadge } from '../../../shared/ui'
import { QUOTE_STATUSES, QUOTE_STATUS_LABEL, type QuoteStatus } from '../../../shared/ui/status'
import { codeOf } from '../../../shared/api/client'
import { dateShort } from '../../../shared/lib/format'
import type { QuoteResponse } from '../../../shared/api/types'
import { useQuoteList } from '../hooks'

/**
 * 견적 목록 (QT-20 · 07 §C `GET /quotes?status=&dealId=`) — 담당 스코프는 서버가 가른다 (SC-04).
 *
 * - 열은 QuoteResponse에 있는 것만: 견적번호·상태·합계·유효기간·발송/열람·딜(dealId만 있어 링크로)
 * - 검색·필터·페이지는 URL 쿼리. `?dealId=`는 딜 상세에서 넘어온다
 * - 견적은 딜에서만 시작한다 (QT-01) — 이 화면에 "새 견적" 버튼이 없는 이유
 */
const ALL = '__all__'
const PAGE_SIZE = 20
const OPEN: QuoteStatus[] = ['DRAFT', 'SENT', 'VIEWED']

export function QuoteListPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const status = params.get('status') ?? ''
  const dealId = params.get('dealId') ?? ''
  const page = Math.max(0, Number(params.get('page') ?? 0))

  const query = useMemo(() => ({ status: (status || undefined) as QuoteStatus | undefined, dealId: dealId || undefined, page, size: PAGE_SIZE }), [status, dealId, page])
  const { data, isPending, isFetching, error, refetch } = useQuoteList(query)

  const update = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value) merged.set(key, value)
      else merged.delete(key)
    }
    if (!('page' in next)) merged.delete('page')
    setParams(merged, { replace: true })
  }
  const filtered = status !== '' || dealId !== ''

  return (
    <>
      <PageHeader
        title="견적"
        badge={data && <Badge color="blue" variant="soft" size="2">{data.totalElements}건</Badge>}
        description="견적은 딜 상세에서 작성을 시작합니다. 발송된 견적은 수정할 수 없고, 다시 제안하려면 복제합니다."
      />

      <Flex gap="3" mb="4" align="center" wrap="wrap">
        <Select.Root value={status || ALL} onValueChange={(value) => update({ status: value === ALL ? '' : value })}>
          <Select.Trigger placeholder="상태" style={{ minWidth: 130 }} />
          <Select.Content position="popper">
            <Select.Item value={ALL}>전체 상태</Select.Item>
            {QUOTE_STATUSES.map((s) => (
              <Select.Item key={s} value={s}>{QUOTE_STATUS_LABEL[s]}</Select.Item>
            ))}
          </Select.Content>
        </Select.Root>
        {dealId && (
          <Badge color="blue" variant="soft" size="2">
            <Link to={`/deals/${dealId}`} style={{ color: 'inherit', textDecoration: 'none' }}>특정 딜의 견적만</Link>
          </Badge>
        )}
        {filtered && (
          <Button variant="ghost" color="gray" onClick={() => update({ status: '', dealId: '' })}>
            <Cross2Icon /> 필터 해제
          </Button>
        )}
      </Flex>

      {error && <ErrorCallout code={codeOf(error)} onRetry={() => refetch()} />}

      {isPending ? (
        <TableSkeleton columns={[2, 1, 1, 1, 1, 1]} />
      ) : data && data.content.length === 0 ? (
        filtered ? (
          <EmptyState icon={<FileTextIcon width="28" height="28" />} title="조건에 맞는 견적이 없습니다" action={{ label: '필터 해제', onClick: () => update({ status: '', dealId: '' }) }} />
        ) : (
          <EmptyState
            icon={<FileTextIcon width="28" height="28" />}
            title="아직 견적이 없습니다"
            description="딜 상세의 「견적 작성」에서 첫 견적을 시작하세요. 카탈로그에서 고르면 단가가 그 순간 복사됩니다."
            action={{ label: '딜 보드로', onClick: () => navigate('/deals') }}
          />
        )
      ) : (
        data && (
          <Card className="enter-fade" style={{ opacity: isFetching ? 0.7 : 1, transition: 'opacity var(--motion-fast)' }}>
            <Table.Root variant="ghost" size="2">
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeaderCell>견적번호</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="110px">상태</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="140px" align="right">합계</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="170px">유효기간</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="150px">발송 · 열람</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="90px">딜</Table.ColumnHeaderCell>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {data.content.map((quote) => (
                  <QuoteRow key={quote.id} quote={quote} onOpen={() => navigate(`/quotes/${quote.id}`)} />
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

function QuoteRow({ quote, onOpen }: { quote: QuoteResponse; onOpen: () => void }) {
  const open = OPEN.includes(quote.status)
  return (
    <Table.Row
      className="row-hover"
      tabIndex={0}
      role="link"
      aria-label={`${quote.quoteNo} 상세`}
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
        <Text size="2" weight="medium" style={{ fontVariantNumeric: 'tabular-nums' }}>{quote.quoteNo}</Text>
      </Table.RowHeaderCell>
      <Table.Cell><QuoteStatusBadge status={quote.status} /></Table.Cell>
      <Table.Cell align="right"><Money value={quote.totalAmount} unit /></Table.Cell>
      <Table.Cell>
        {/* 날짜와 남은 기간은 한 줄 — 줄이 갈리면 행 높이가 들쭉날쭉해진다 */}
        <Flex align="center" gap="2" wrap="nowrap" style={{ whiteSpace: 'nowrap' }}>
          <Text size="2" color="gray">{dateShort(`${quote.validUntil}T00:00:00Z`)}</Text>
          {open && <RemainingBadge until={`${quote.validUntil}T00:00:00Z`} />}
        </Flex>
      </Table.Cell>
      <Table.Cell>
        {quote.status === 'SENT' || quote.status === 'VIEWED' ? (
          <ViewedBadge firstViewedAt={quote.firstViewedAt} sentAt={quote.sentAt} />
        ) : (
          <Text size="2" color="gray">{quote.sentAt ? `${dateShort(quote.sentAt)} 발송` : '—'}</Text>
        )}
      </Table.Cell>
      <Table.Cell>
        <Text asChild size="2" color="blue">
          <Link to={`/deals/${quote.dealId}`} onClick={(e) => e.stopPropagation()} style={{ textDecoration: 'none' }}>
            딜 보기
          </Link>
        </Text>
      </Table.Cell>
    </Table.Row>
  )
}
