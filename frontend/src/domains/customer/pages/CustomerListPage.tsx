import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { Badge, Button, Card, Flex, IconButton, Select, Skeleton, Table, Text, TextField } from '@radix-ui/themes'
import { ChevronLeftIcon, ChevronRightIcon, Cross2Icon, MagnifyingGlassIcon, PlusIcon } from '@radix-ui/react-icons'
import { EmptyState, ErrorCallout, PageHeader } from '../../../shared/ui'
import { ApiError } from '../../../shared/api/client'
import { dateShort } from '../../../shared/lib/format'
import type { CustomerResponse } from '../../../shared/api/types'
import { INDUSTRIES, PAGE_SIZE, SELECT_CONTENT } from '../constants'
import { useCreateCustomer, useCustomerList } from '../hooks'
import { CustomerFormDialog } from '../components/CustomerFormDialog'
import { IndustryBadge } from '../components/IndustryBadge'

/**
 * 고객사 목록 (CU-03·04, 10 §5.9) — 구성원 앱 목록 화면의 예제.
 *
 * - 검색·필터·페이지는 URL 쿼리로 관리한다
 * - 응답 DTO(CustomerResponse)에 없는 값은 표시하지 않는다
 * - 빈 화면은 데이터 없음 / 검색 결과 없음을 구분한다 (§6.2)
 */

const ALL = '__all__'

export function CustomerListPage() {
  const navigate = useNavigate()
  const [params, setParams] = useSearchParams()
  const keyword = params.get('keyword') ?? ''
  const industry = params.get('industry') ?? ''
  const page = Math.max(0, Number(params.get('page') ?? 0))

  const query = useMemo(() => ({ keyword: keyword || undefined, industry: industry || undefined, page, size: PAGE_SIZE }), [keyword, industry, page])
  const { data, isPending, isFetching, error, refetch } = useCustomerList(query)
  const createMutation = useCreateCustomer()
  const [creating, setCreating] = useState(false)

  const update = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value) merged.set(key, value)
      else merged.delete(key)
    }
    if (!('page' in next)) merged.delete('page') // 검색·필터를 바꾸면 1페이지로
    setParams(merged, { replace: true })
  }

  const filtered = keyword !== '' || industry !== ''
  const total = data?.totalElements ?? 0

  return (
    <>
      <PageHeader
        title="고객사"
        badge={data && <Badge color="blue" variant="soft" size="2">{total}곳</Badge>}
        description="회사 전체가 공유합니다. 등록자는 기록으로만 남고, 누구나 수정할 수 있습니다."
        actions={
          <Button onClick={() => setCreating(true)}>
            <PlusIcon /> 고객사 등록
          </Button>
        }
      />

      <Flex gap="3" mb="4" align="center" wrap="wrap">
        <SearchInput value={keyword} onChange={(value) => update({ keyword: value })} />
        <Select.Root value={industry || ALL} onValueChange={(value) => update({ industry: value === ALL ? '' : value })}>
          <Select.Trigger placeholder="업종" style={{ minWidth: 120 }} />
          <Select.Content {...SELECT_CONTENT}>
            <Select.Item value={ALL}>전체 업종</Select.Item>
            {INDUSTRIES.map((item) => (
              <Select.Item key={item} value={item}>
                {item}
              </Select.Item>
            ))}
          </Select.Content>
        </Select.Root>
        {filtered && (
          <Button variant="ghost" color="gray" onClick={() => update({ keyword: '', industry: '' })}>
            <Cross2Icon /> 필터 해제
          </Button>
        )}
      </Flex>

      {error && <ErrorCallout code={error instanceof ApiError ? error.code : 'INTERNAL_ERROR'} onRetry={() => refetch()} />}

      {isPending ? (
        <TableSkeleton />
      ) : data && data.content.length === 0 ? (
        filtered ? (
          <EmptyState
            icon={<MagnifyingGlassIcon width="28" height="28" />}
            title="조건에 맞는 고객사가 없습니다"
            description="검색어나 업종 필터를 바꿔 보세요."
            action={{ label: '필터 해제', onClick: () => update({ keyword: '', industry: '' }) }}
          />
        ) : (
          <EmptyState
            icon={<PlusIcon width="28" height="28" />}
            title="아직 등록된 고객사가 없습니다"
            description="고객사를 등록하면 딜을 만들고 견적을 보낼 수 있습니다."
            action={{ label: '첫 고객사 등록', onClick: () => setCreating(true) }}
          />
        )
      ) : (
        data && (
          <Card className="enter-fade" style={{ opacity: isFetching ? 0.7 : 1, transition: 'opacity var(--motion-fast)' }}>
            <Table.Root variant="ghost" size="2">
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeaderCell>고객사</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="110px">업종</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="120px">규모</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="100px">등록일</Table.ColumnHeaderCell>
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {data.content.map((customer) => (
                  <CustomerRow key={customer.id} customer={customer} onOpen={() => navigate(`/customers/${customer.id}`)} />
                ))}
              </Table.Body>
            </Table.Root>

            <Flex align="center" justify="between" px="3" pt="3" mt="2" style={{ borderTop: '1px solid var(--gray-a4)' }}>
              <Text size="1" color="gray">
                전체 {total}곳 중 {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, total)}
              </Text>
              {data.totalPages > 1 && (
                <Flex align="center" gap="2">
                  <IconButton variant="soft" color="gray" size="1" aria-label="이전 페이지" disabled={page === 0} onClick={() => update({ page: String(page - 1) })}>
                    <ChevronLeftIcon />
                  </IconButton>
                  <Text size="1" color="gray">
                    {page + 1} / {data.totalPages}
                  </Text>
                  <IconButton variant="soft" color="gray" size="1" aria-label="다음 페이지" disabled={page + 1 >= data.totalPages} onClick={() => update({ page: String(page + 1) })}>
                    <ChevronRightIcon />
                  </IconButton>
                </Flex>
              )}
            </Flex>
          </Card>
        )
      )}

      <CustomerFormDialog
        open={creating}
        onOpenChange={(open) => {
          setCreating(open)
          if (!open) createMutation.reset()
        }}
        loading={createMutation.isPending}
        error={createMutation.error}
        onSubmit={(body) =>
          createMutation.mutate(body, {
            onSuccess: (created) => {
              setCreating(false)
              navigate(`/customers/${created.id}`) // 등록 직후 할 일은 담당자 추가다 — 상세로 보낸다
            },
          })
        }
      />
    </>
  )
}

function CustomerRow({ customer, onOpen }: { customer: CustomerResponse; onOpen: () => void }) {
  return (
    <Table.Row
      className="row-hover"
      tabIndex={0}
      role="link"
      aria-label={`${customer.name} 상세`}
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
        <Text as="div" size="2" weight="medium">
          {customer.name}
        </Text>
        {customer.note && (
          <Text as="div" size="1" color="gray" truncate style={{ maxWidth: 480 }}>
            {customer.note}
          </Text>
        )}
      </Table.RowHeaderCell>
      <Table.Cell>
        {customer.industry ? (
          <IndustryBadge industry={customer.industry} />
        ) : (
          <Text size="2" color="gray">
            —
          </Text>
        )}
      </Table.Cell>
      <Table.Cell>
        <Text size="2" color={customer.size ? undefined : 'gray'}>
          {customer.size ?? '—'}
        </Text>
      </Table.Cell>
      <Table.Cell>
        <Text size="2" color="gray">
          {dateShort(customer.createdAt)}
        </Text>
      </Table.Cell>
    </Table.Row>
  )
}

/** 250ms 디바운스. 외부 값이 바뀌면 렌더 중에 동기화한다 */
function SearchInput({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const [draft, setDraft] = useState(value)
  const [seen, setSeen] = useState(value)
  if (value !== seen) {
    setSeen(value)
    setDraft(value)
  }
  useEffect(() => {
    if (draft === value) return
    const timer = setTimeout(() => onChange(draft), 250)
    return () => clearTimeout(timer)
  }, [draft, value, onChange])

  return (
    <TextField.Root value={draft} onChange={(e) => setDraft(e.target.value)} placeholder="고객사명 검색" style={{ width: 260 }} aria-label="고객사명 검색">
      <TextField.Slot>
        <MagnifyingGlassIcon />
      </TextField.Slot>
      {draft && (
        <TextField.Slot>
          <IconButton size="1" variant="ghost" color="gray" aria-label="검색어 지우기" onClick={() => setDraft('')}>
            <Cross2Icon />
          </IconButton>
        </TextField.Slot>
      )}
    </TextField.Root>
  )
}

function TableSkeleton() {
  return (
    <Card>
      <Flex direction="column" gap="3" p="2">
        {[0, 1, 2, 3, 4].map((i) => (
          <Flex key={i} gap="4" align="center">
            <Skeleton height="20px" style={{ flex: 3 }} />
            <Skeleton height="20px" width="80px" />
            <Skeleton height="20px" width="90px" />
            <Skeleton height="20px" width="70px" />
          </Flex>
        ))}
      </Flex>
    </Card>
  )
}
