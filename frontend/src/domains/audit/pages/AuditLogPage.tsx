import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { Badge, Button, Card, Code, Flex, Select, Table, Text, TextField } from '@radix-ui/themes'
import { ActivityLogIcon, Cross2Icon } from '@radix-ui/react-icons'
import {
  EmptyState, ErrorCallout, PageHeader, Pagination, TableSkeleton, AUDIT_ACTOR_TYPE_LABEL,
} from '../../../shared/ui'
import { codeOf } from '../../../shared/api/client'
import { dateTime } from '../../../shared/lib/format'
import type { AuditLogResponse } from '../../../shared/api/types'
import { PAGE_SIZE, SELECT_CONTENT } from '../../customer/constants'
import { useAuditLogList } from '../hooks'
import { AuditLogDetailDialog } from '../components/AuditLogDetailDialog'

/**
 * 감사 로그 (AC-11 · 07 §B `GET /audit-logs?entityType=&from=&to=`) — 기업 관리자 전용 (09 매트릭스).
 *
 * - 목록은 요약(payload 제외), 상세는 행 클릭 시 별도 조회 (07 v1.5 감사 로그 상세)
 * - entityType은 자유 문자열 — 선택지는 B의 이벤트 포맷이 확정될 때까지 화면 로컬 상수로 둔다
 * - 필터·페이지는 URL 쿼리
 */

const ALL = '__all__'

/** 목 데이터·시드에 실제로 있는 대상 유형. 정본은 B의 이벤트 페이로드 포맷(AC-07) */
const ENTITY_TYPES = ['DEAL', 'QUOTE', 'ORDER', 'CUSTOMER', 'PRODUCT', 'MEMBER'] as const
const ENTITY_LABEL: Record<(typeof ENTITY_TYPES)[number], string> = {
  DEAL: '딜', QUOTE: '견적', ORDER: '주문', CUSTOMER: '고객사', PRODUCT: '상품', MEMBER: '구성원',
}
const entityLabel = (type: string) => (ENTITY_LABEL as Record<string, string>)[type] ?? type

export function AuditLogPage() {
  const [params, setParams] = useSearchParams()
  const entityType = params.get('entityType') ?? ''
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const page = Math.max(0, Number(params.get('page') ?? 0))
  const [selected, setSelected] = useState<string | null>(null)

  const query = useMemo(
    () => ({ entityType: entityType || undefined, from: from || undefined, to: to || undefined, page, size: PAGE_SIZE }),
    [entityType, from, to, page],
  )
  const { data, isPending, isFetching, error, refetch } = useAuditLogList(query)

  const update = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value) merged.set(key, value)
      else merged.delete(key)
    }
    if (!('page' in next)) merged.delete('page')
    setParams(merged, { replace: true })
  }

  const filtered = entityType !== '' || from !== '' || to !== ''
  const clear = () => update({ entityType: '', from: '', to: '' })

  return (
    <>
      <PageHeader
        title="감사 로그"
        badge={data && <Badge color="blue" variant="soft" size="2">{data.totalElements}건</Badge>}
        description="누가 무엇을 언제 바꿨는지 남깁니다. 행을 누르면 변경 전·후 값을 볼 수 있습니다."
      />

      <Flex gap="3" mb="4" align="center" wrap="wrap">
        <Select.Root value={entityType || ALL} onValueChange={(value) => update({ entityType: value === ALL ? '' : value })}>
          <Select.Trigger placeholder="대상" style={{ minWidth: 130 }} />
          <Select.Content {...SELECT_CONTENT}>
            <Select.Item value={ALL}>전체 대상</Select.Item>
            {ENTITY_TYPES.map((t) => (
              <Select.Item key={t} value={t}>
                {ENTITY_LABEL[t]}
              </Select.Item>
            ))}
          </Select.Content>
        </Select.Root>
        <Flex align="center" gap="2">
          <TextField.Root type="date" value={from} max={to || undefined} onChange={(e) => update({ from: e.target.value })} aria-label="시작일" />
          <Text size="2" color="gray">~</Text>
          <TextField.Root type="date" value={to} min={from || undefined} onChange={(e) => update({ to: e.target.value })} aria-label="종료일" />
        </Flex>
        {filtered && (
          <Button variant="ghost" color="gray" onClick={clear}>
            <Cross2Icon /> 필터 해제
          </Button>
        )}
      </Flex>

      {error && <ErrorCallout code={codeOf(error)} onRetry={() => refetch()} />}

      {isPending ? (
        <TableSkeleton columns={[2, 2, 2, 2]} />
      ) : data && data.content.length === 0 ? (
        filtered ? (
          <EmptyState title="조건에 맞는 기록이 없습니다" description="대상이나 기간을 바꿔 보세요." action={{ label: '필터 해제', onClick: clear }} />
        ) : (
          <EmptyState icon={<ActivityLogIcon width="28" height="28" />} title="아직 기록이 없습니다" description="딜·견적·상품이 바뀌면 여기에 자동으로 쌓입니다." />
        )
      ) : (
        data && (
          <Card className="enter-fade" style={{ opacity: isFetching ? 0.7 : 1, transition: 'opacity var(--motion-fast)' }}>
            <Table.Root variant="ghost" size="2">
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeaderCell width="160px">시각</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>대상</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="180px">이벤트</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="180px">행위자</Table.ColumnHeaderCell>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {data.content.map((log) => (
                  <AuditRow key={log.id} log={log} onOpen={() => setSelected(log.id)} />
                ))}
              </Table.Body>
            </Table.Root>
            <Pagination data={data} unit="건" onPageChange={(next) => update({ page: String(next) })} />
          </Card>
        )
      )}

      <AuditLogDetailDialog id={selected} onOpenChange={(open) => !open && setSelected(null)} />
    </>
  )
}

function AuditRow({ log, onOpen }: { log: AuditLogResponse; onOpen: () => void }) {
  return (
    <Table.Row
      className="row-hover"
      tabIndex={0}
      role="button"
      aria-label={`${entityLabel(log.entityType)} ${log.eventType} 상세`}
      style={{ cursor: 'pointer' }}
      onClick={onOpen}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault()
          onOpen()
        }
      }}
    >
      <Table.Cell>
        <Text size="2" color="gray">{dateTime(log.occurredAt)}</Text>
      </Table.Cell>
      <Table.RowHeaderCell>
        <Flex align="center" gap="2">
          <Badge color="gray" variant="soft">{entityLabel(log.entityType)}</Badge>
          <Code size="1" variant="ghost" color="gray">{log.entityId.slice(0, 8)}</Code>
        </Flex>
      </Table.RowHeaderCell>
      <Table.Cell>
        <Badge color="blue" variant="soft">{log.eventType}</Badge>
      </Table.Cell>
      <Table.Cell>
        <Text size="2">
          {AUDIT_ACTOR_TYPE_LABEL[log.actorType]}
          {log.actorName && (
            <Text size="2" color="gray"> · {log.actorName}</Text>
          )}
        </Text>
      </Table.Cell>
    </Table.Row>
  )
}
