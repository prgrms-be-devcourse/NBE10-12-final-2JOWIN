import { useMemo } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { Badge, Card, Flex, Select, Table, Text } from '@radix-ui/themes'
import { ApplicationStatusBadge, EmptyState, ErrorCallout, PageHeader, Pagination, TableSkeleton } from '../../../shared/ui'
import { APPLICATION_STATUSES, APPLICATION_STATUS_LABEL, type ApplicationStatus } from '../../../shared/ui/status'
import { codeOf } from '../../../shared/api/client'
import { dateShort } from '../../../shared/lib/format'
import type { ApplicationResponse } from '../../../shared/api/types'
import { useApplicationList } from '../hooks'

/**
 * 가입 신청 목록 (ON-03 · 07 §A `GET /admin/api/v1/applications?status=`).
 *
 * 기본 필터는 검토 대기(PENDING) — 관리자가 이 화면에 오는 이유는 심사다.
 * 열은 ApplicationResponse(08 §A)에 있는 것만: 회사명·사업자번호·이메일·상태·신청일·처리일.
 * 상태·페이지는 URL 쿼리 — 새로고침·뒤로가기에 그대로 동작한다 (10 §5.9).
 */

const ALL = '__all__'
const PAGE_SIZE = 20

export function AdminApplicationListPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const rawStatus = params.get('status')
  // 파라미터가 없으면 PENDING, 'all'이면 전체
  const status: ApplicationStatus | undefined =
    rawStatus === ALL ? undefined : (APPLICATION_STATUSES as readonly string[]).includes(rawStatus ?? '') ? (rawStatus as ApplicationStatus) : 'PENDING'
  const page = Math.max(0, Number(params.get('page') ?? 0))

  const query = useMemo(() => ({ status, page, size: PAGE_SIZE }), [status, page])
  const { data, isPending, isFetching, error, refetch } = useApplicationList(query)

  const update = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value) merged.set(key, value)
      else merged.delete(key)
    }
    if (!('page' in next)) merged.delete('page')
    setParams(merged, { replace: true })
  }

  return (
    <>
      <PageHeader
        title="가입 신청"
        badge={data && <Badge variant="soft" size="2">{data.totalElements}건</Badge>}
        description="승인하면 회사가 생성되고 신청자에게 비밀번호 설정 링크가 발송됩니다. 반려는 사유가 필요합니다."
      />

      <Flex gap="3" mb="4" align="center">
        <Select.Root value={status ?? ALL} onValueChange={(value) => update({ status: value })}>
          <Select.Trigger style={{ minWidth: 140 }} />
          <Select.Content position="popper">
            {APPLICATION_STATUSES.map((s) => (
              <Select.Item key={s} value={s}>
                {APPLICATION_STATUS_LABEL[s]}
              </Select.Item>
            ))}
            <Select.Item value={ALL}>전체 상태</Select.Item>
          </Select.Content>
        </Select.Root>
      </Flex>

      {error && <ErrorCallout code={codeOf(error)} onRetry={() => refetch()} />}

      {isPending ? (
        <TableSkeleton columns={[2, 1.2, 2, 1, 1, 1]} />
      ) : data && data.content.length === 0 ? (
        <EmptyState
          title={status === 'PENDING' ? '검토할 신청이 없습니다' : '조건에 맞는 신청이 없습니다'}
          description={status === 'PENDING' ? '새 신청이 들어오면 여기에 표시됩니다.' : '상태 필터를 바꿔 보세요.'}
          action={status === 'PENDING' ? undefined : { label: '검토 대기 보기', onClick: () => update({ status: 'PENDING' }) }}
        />
      ) : (
        data && (
          <Card className="enter-fade" style={{ opacity: isFetching ? 0.7 : 1, transition: 'opacity var(--motion-fast)' }}>
            <Table.Root variant="ghost" size="2">
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeaderCell>회사명</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="130px">사업자번호</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>이메일</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="110px">상태</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="90px">신청일</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="90px">처리일</Table.ColumnHeaderCell>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {data.content.map((application) => (
                  <ApplicationRow key={application.id} application={application} onOpen={() => navigate(`/admin/applications/${application.id}`)} />
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

function ApplicationRow({ application, onOpen }: { application: ApplicationResponse; onOpen: () => void }) {
  return (
    <Table.Row
      className="row-hover"
      tabIndex={0}
      role="link"
      aria-label={`${application.companyName} 신청 상세`}
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
        <Text size="2" weight="medium">
          {application.companyName}
        </Text>
      </Table.RowHeaderCell>
      <Table.Cell>
        <Text size="2" style={{ fontVariantNumeric: 'tabular-nums' }}>
          {application.businessNo}
        </Text>
      </Table.Cell>
      <Table.Cell>
        <Text size="2" color="gray">
          {application.email}
        </Text>
      </Table.Cell>
      <Table.Cell>
        <ApplicationStatusBadge status={application.status} />
      </Table.Cell>
      <Table.Cell>
        <Text size="2" color="gray">
          {dateShort(application.createdAt)}
        </Text>
      </Table.Cell>
      <Table.Cell>
        <Text size="2" color="gray">
          {application.decidedAt ? dateShort(application.decidedAt) : '—'}
        </Text>
      </Table.Cell>
    </Table.Row>
  )
}
