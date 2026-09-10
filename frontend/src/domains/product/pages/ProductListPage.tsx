import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { Badge, Button, Card, Flex, Select, Table, Text } from '@radix-ui/themes'
import { CheckCircledIcon, Cross2Icon, Pencil1Icon, PlusIcon, StopIcon } from '@radix-ui/react-icons'
import {
  ConfirmDialog, EmptyState, ErrorCallout, Money, PageHeader, Pagination, ProductStatusBadge, TableSkeleton,
  PRODUCT_STATUSES, PRODUCT_STATUS_LABEL, type ProductStatus,
} from '../../../shared/ui'
import { ApiError, codeOf } from '../../../shared/api/client'
import type { ProductResponse } from '../../../shared/api/types'
import { isAdmin, useSession } from '../../../app/session'
import { PAGE_SIZE, SELECT_CONTENT } from '../../customer/constants'
import { useProductList, useProductMutations } from '../hooks'
import { ProductFormDialog } from '../components/ProductFormDialog'

/**
 * 상품 카탈로그 (PR-01~10 · 07 §B · product/controller/ProductController).
 *
 * - 조회는 전 구성원, 편집은 기업 관리자만 — 영업 담당자에게는 버튼 자체를 숨긴다 (PR-09, 10 §3.2)
 * - 목록은 컨트롤러 기본 정렬(name ASC) 그대로, 상태 필터·페이지는 URL 쿼리
 * - 판매 중지는 되돌릴 수 있지만(재개) 그 사이 새 견적에 못 넣으므로(PR-06) 확인을 한 번 받는다
 * - 응답 DTO(ProductResponse)에 있는 것만 표시한다
 */

const ALL = '__all__'

export function ProductListPage() {
  const session = useSession()
  const editable = isAdmin(session)
  const [params, setParams] = useSearchParams()
  const status = (params.get('status') ?? '') as ProductStatus | ''
  const page = Math.max(0, Number(params.get('page') ?? 0))

  const query = useMemo(() => ({ status: status || undefined, page, size: PAGE_SIZE }), [status, page])
  const { data, isPending, isFetching, error, refetch } = useProductList(query)
  const mutations = useProductMutations()

  const [dialog, setDialog] = useState<{ mode: 'create' } | { mode: 'edit'; product: ProductResponse } | null>(null)
  const [toDiscontinue, setToDiscontinue] = useState<ProductResponse | null>(null)

  const update = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value) merged.set(key, value)
      else merged.delete(key)
    }
    if (!('page' in next)) merged.delete('page')
    setParams(merged, { replace: true })
  }

  const closeDialog = () => {
    setDialog(null)
    mutations.create.reset()
    mutations.update.reset()
  }

  const total = data?.totalElements ?? 0

  return (
    <>
      <PageHeader
        title="상품"
        badge={data && <Badge color="blue" variant="soft" size="2">{total}종</Badge>}
        description={
          editable
            ? '회사 전체가 공유하는 카탈로그입니다. 단가를 바꿔도 이미 작성된 견적은 움직이지 않습니다.'
            : '회사 전체가 공유하는 카탈로그입니다. 등록·수정은 기업 관리자가 합니다.'
        }
        actions={
          editable && (
            <Button onClick={() => setDialog({ mode: 'create' })}>
              <PlusIcon /> 상품 등록
            </Button>
          )
        }
      />

      <Flex gap="3" mb="4" align="center" wrap="wrap">
        <Select.Root value={status || ALL} onValueChange={(value) => update({ status: value === ALL ? '' : value })}>
          <Select.Trigger placeholder="상태" style={{ minWidth: 130 }} />
          <Select.Content {...SELECT_CONTENT}>
            <Select.Item value={ALL}>전체 상태</Select.Item>
            {PRODUCT_STATUSES.map((s) => (
              <Select.Item key={s} value={s}>
                {PRODUCT_STATUS_LABEL[s]}
              </Select.Item>
            ))}
          </Select.Content>
        </Select.Root>
        {status && (
          <Button variant="ghost" color="gray" onClick={() => update({ status: '' })}>
            <Cross2Icon /> 필터 해제
          </Button>
        )}
      </Flex>

      {error && <ErrorCallout code={codeOf(error)} onRetry={() => refetch()} />}
      {mutations.discontinue.error && !toDiscontinue && <ErrorCallout code={codeOf(mutations.discontinue.error)} />}
      {mutations.reactivate.error && <ErrorCallout code={codeOf(mutations.reactivate.error)} />}

      {isPending ? (
        <TableSkeleton columns={[3, 1, 1, 3, 1]} />
      ) : data && data.content.length === 0 ? (
        status ? (
          <EmptyState
            title={`${PRODUCT_STATUS_LABEL[status]} 상품이 없습니다`}
            description="상태 필터를 바꿔 보세요."
            action={{ label: '필터 해제', onClick: () => update({ status: '' }) }}
          />
        ) : (
          <EmptyState
            icon={<PlusIcon width="28" height="28" />}
            title="아직 등록된 상품이 없습니다"
            description={editable ? '상품을 등록하면 견적 작성 때 카탈로그에서 바로 고를 수 있습니다.' : '기업 관리자가 상품을 등록하면 여기에 나타납니다.'}
            action={editable ? { label: '첫 상품 등록', onClick: () => setDialog({ mode: 'create' }) } : undefined}
          />
        )
      ) : (
        data && (
          <Card className="enter-fade" style={{ opacity: isFetching ? 0.7 : 1, transition: 'opacity var(--motion-fast)' }}>
            <Table.Root variant="ghost" size="2">
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeaderCell>상품명</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="70px">단위</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="130px" align="right">단가</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>설명</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="110px">상태</Table.ColumnHeaderCell>
                  {editable && <Table.ColumnHeaderCell width="200px" />}
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {data.content.map((product) => (
                  <Table.Row key={product.id} className="row-hover">
                    <Table.RowHeaderCell>
                      <Text size="2" weight="medium" color={product.status === 'DISCONTINUED' ? 'gray' : undefined}>
                        {product.name}
                      </Text>
                    </Table.RowHeaderCell>
                    <Table.Cell>
                      <Text size="2" color="gray">{product.unit}</Text>
                    </Table.Cell>
                    <Table.Cell align="right">
                      <Money value={product.unitPrice} unit />
                    </Table.Cell>
                    <Table.Cell>
                      <Text size="2" color="gray" truncate style={{ maxWidth: 360, display: 'block' }}>
                        {product.description?.trim() || '—'}
                      </Text>
                    </Table.Cell>
                    <Table.Cell>
                      <ProductStatusBadge status={product.status} />
                    </Table.Cell>
                    {editable && (
                      <Table.Cell>
                        <Flex gap="2" justify="end">
                          <Button size="1" variant="soft" color="gray" onClick={() => setDialog({ mode: 'edit', product })}>
                            <Pencil1Icon /> 수정
                          </Button>
                          {product.status === 'ACTIVE' ? (
                            <Button size="1" variant="soft" color="gray" onClick={() => setToDiscontinue(product)}>
                              <StopIcon /> 판매 중지
                            </Button>
                          ) : (
                            <Button
                              size="1"
                              variant="soft"
                              color="green"
                              loading={mutations.reactivate.isPending && mutations.reactivate.variables === product.id}
                              onClick={() => mutations.reactivate.mutate(product.id)}
                            >
                              <CheckCircledIcon /> 판매 재개
                            </Button>
                          )}
                        </Flex>
                      </Table.Cell>
                    )}
                  </Table.Row>
                ))}
              </Table.Body>
            </Table.Root>
            <Pagination data={data} unit="종" onPageChange={(next) => update({ page: String(next) })} />
          </Card>
        )
      )}

      {editable && (
        <>
          <ProductFormDialog
            open={dialog !== null}
            onOpenChange={(open) => {
              if (!open) closeDialog()
            }}
            product={dialog?.mode === 'edit' ? dialog.product : undefined}
            loading={mutations.create.isPending || mutations.update.isPending}
            error={mutations.create.error ?? mutations.update.error}
            onSubmit={(body) => {
              if (dialog?.mode === 'edit') {
                mutations.update.mutate({ id: dialog.product.id, body }, { onSuccess: closeDialog })
              } else {
                mutations.create.mutate(body, { onSuccess: closeDialog })
              }
            }}
          />

          {/* 판매 중지 — 새 견적에서 사라진다(PR-06). 기존 견적은 무영향(PR-07) */}
          <ConfirmDialog
            open={toDiscontinue !== null}
            onOpenChange={(open) => {
              if (!open) {
                setToDiscontinue(null)
                mutations.discontinue.reset()
              }
            }}
            title={`${toDiscontinue?.name ?? ''}을(를) 판매 중지하시겠습니까?`}
            description="새 견적에는 추가할 수 없게 됩니다. 이미 작성된 견적은 그대로이고, 언제든 판매를 재개할 수 있습니다."
            confirmLabel="판매 중지"
            confirmColor="gray"
            loading={mutations.discontinue.isPending}
            onConfirm={() => toDiscontinue && mutations.discontinue.mutate(toDiscontinue.id, { onSuccess: () => setToDiscontinue(null) })}
          >
            {mutations.discontinue.error instanceof ApiError && <ErrorCallout code={mutations.discontinue.error.code} />}
          </ConfirmDialog>
        </>
      )}
    </>
  )
}
