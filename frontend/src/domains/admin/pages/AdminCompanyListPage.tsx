import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { Badge, Box, Button, Card, Flex, Table, Text, TextArea } from '@radix-ui/themes'
import { CheckIcon, PauseIcon } from '@radix-ui/react-icons'
import { CompanyStatusBadge, ConfirmDialog, EmptyState, ErrorCallout, PageHeader, Pagination, TableSkeleton } from '../../../shared/ui'
import { ApiError, codeOf } from '../../../shared/api/client'
import { dateShort } from '../../../shared/lib/format'
import type { CompanyResponse } from '../../../shared/api/types'
import { useCompanyList, useCompanyMutations } from '../hooks'

/**
 * 회사 목록 · 이용 현황 (ON-12 · 07 §A `GET /admin/api/v1/companies`).
 *
 * 열은 CompanyResponse(08 §A)에 있는 것만 — 이용 현황의 v1 범위는 구성원 수뿐이다 (Q-41).
 * 딜·견적·고객사는 여기서 보이지 않는다: `/admin/api`에 그 리소스가 없다 (ON-11).
 * 정지·해제(ON-08·10 · 전이표 §2)는 되돌릴 수 있는 운영 조치지만 구성원 전원이 즉시 차단되므로 AlertDialog로 확인한다.
 */

const PAGE_SIZE = 20

export function AdminCompanyListPage() {
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Number(params.get('page') ?? 0))
  const query = useMemo(() => ({ page, size: PAGE_SIZE }), [page])
  const { data, isPending, isFetching, error, refetch } = useCompanyList(query)
  const { suspend, reactivate } = useCompanyMutations()

  const [target, setTarget] = useState<{ action: 'suspend' | 'reactivate'; company: CompanyResponse } | null>(null)
  const [reason, setReason] = useState('')

  const close = () => {
    setTarget(null)
    setReason('')
    suspend.reset()
    reactivate.reset()
  }

  const suspendError = suspend.error instanceof ApiError ? suspend.error : null

  return (
    <>
      <PageHeader
        title="회사"
        badge={data && <Badge variant="soft" size="2">{data.totalElements}곳</Badge>}
        description="정지하면 구성원 전원이 즉시 차단되고 고객 열람 링크는 열람만 가능합니다. 해제하면 구성원은 다시 로그인해야 합니다."
      />

      {error && <ErrorCallout code={codeOf(error)} onRetry={() => refetch()} />}

      {isPending ? (
        <TableSkeleton columns={[2, 1.2, 1, 0.8, 0.8, 1.5, 1]} />
      ) : data && data.content.length === 0 ? (
        <EmptyState title="등록된 회사가 없습니다" description="가입 신청을 승인하면 회사가 생성됩니다." />
      ) : (
        data && (
          <Card className="enter-fade" style={{ opacity: isFetching ? 0.7 : 1, transition: 'opacity var(--motion-fast)' }}>
            <Table.Root variant="ghost" size="2">
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeaderCell>회사명</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="130px">사업자번호</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="100px">상태</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="80px" align="right">구성원</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="90px">가입일</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>정지 사유</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="110px" />
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {data.content.map((company) => (
                  <Table.Row key={company.id} className="row-hover">
                    <Table.RowHeaderCell>
                      <Text size="2" weight="medium">
                        {company.name}
                      </Text>
                    </Table.RowHeaderCell>
                    <Table.Cell>
                      <Text size="2" style={{ fontVariantNumeric: 'tabular-nums' }}>
                        {company.businessNo}
                      </Text>
                    </Table.Cell>
                    <Table.Cell>
                      <CompanyStatusBadge status={company.status} />
                    </Table.Cell>
                    <Table.Cell align="right">
                      <Text size="2">{company.memberCount}명</Text>
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2" color="gray">
                        {dateShort(company.createdAt)}
                      </Text>
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2" color="gray" truncate style={{ maxWidth: 260, display: 'block' }}>
                        {company.suspendReason ?? '—'}
                      </Text>
                    </Table.Cell>
                    <Table.Cell>
                      {company.status === 'ACTIVE' ? (
                        <Button size="1" variant="soft" color="amber" onClick={() => setTarget({ action: 'suspend', company })}>
                          <PauseIcon /> 정지
                        </Button>
                      ) : (
                        <Button size="1" variant="soft" color="green" onClick={() => setTarget({ action: 'reactivate', company })}>
                          <CheckIcon /> 해제
                        </Button>
                      )}
                    </Table.Cell>
                  </Table.Row>
                ))}
              </Table.Body>
            </Table.Root>
            <Pagination data={data} unit="곳" onPageChange={(next) => setParams({ page: String(next) }, { replace: true })} />
          </Card>
        )
      )}

      {/* ── 정지 — 사유 필수 (ON-08) */}
      <ConfirmDialog
        open={target?.action === 'suspend'}
        onOpenChange={(open) => !open && close()}
        title={`${target?.company.name ?? ''}을(를) 정지하시겠습니까?`}
        description="구성원 전원의 세션이 즉시 폐기되고 로그인이 막힙니다. 고객 열람 링크는 열람만 되고 승인·반려가 차단되며, 리마인드·임박 안내 메일이 중단됩니다."
        confirmLabel="정지"
        confirmColor="red"
        loading={suspend.isPending}
        onConfirm={() => target && suspend.mutate({ id: target.company.id, body: { reason: reason.trim() } }, { onSuccess: close })}
      >
        <Box mt="3">
          <Text as="label" htmlFor="suspend-reason" size="2" weight="medium">
            정지 사유 (필수)
          </Text>
          <TextArea
            id="suspend-reason"
            mt="1"
            rows={3}
            placeholder="예: 이용료 미납 — 2026-08-30 통보"
            value={reason}
            disabled={suspend.isPending}
            onChange={(e) => setReason(e.target.value)}
            color={suspendError?.reasonOf('reason') ? 'red' : undefined}
          />
          {suspendError?.reasonOf('reason') && (
            <Text as="p" size="1" color="red" mt="1">
              {suspendError.reasonOf('reason')}
            </Text>
          )}
          {!reason.trim() && (
            <Text as="p" size="1" color="gray" mt="1">
              사유를 입력해야 정지할 수 있습니다.
            </Text>
          )}
        </Box>
        {suspendError && suspendError.code !== 'VALIDATION_FAILED' && <ErrorCallout code={suspendError.code} />}
      </ConfirmDialog>

      {/* ── 해제 — 데이터는 그대로, 구성원은 재로그인 (ON-10, Q-27) */}
      <ConfirmDialog
        open={target?.action === 'reactivate'}
        onOpenChange={(open) => !open && close()}
        title={`${target?.company.name ?? ''}의 정지를 해제하시겠습니까?`}
        description="데이터는 그대로 복구되고 고객 링크·알림도 자동으로 되살아납니다. 구성원은 다시 로그인해야 합니다."
        confirmLabel="정지 해제"
        confirmColor="green"
        loading={reactivate.isPending}
        onConfirm={() => target && reactivate.mutate(target.company.id, { onSuccess: close })}
      >
        {reactivate.error && <ErrorCallout code={codeOf(reactivate.error)} />}
      </ConfirmDialog>

      <Flex mt="3" justify="end">
        <Text size="1" color="gray">
          이용 현황은 구성원 수만 제공됩니다. 영업 데이터는 플랫폼 관리자가 조회할 수 없습니다.
        </Text>
      </Flex>
    </>
  )
}
