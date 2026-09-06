import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Badge, Button, Callout, Card, Flex, Grid, IconButton, Select, Separator, Table, Text, TextArea, TextField, Tooltip } from '@radix-ui/themes'
import { ArrowDownIcon, ArrowUpIcon, EyeOpenIcon, InfoCircledIcon, PaperPlaneIcon, Pencil2Icon, PlusIcon, TrashIcon } from '@radix-ui/react-icons'
import { ApiError } from '../../../shared/api/client'
import { ErrorCallout, Field, Money } from '../../../shared/ui'
import { VAT_MODES, VAT_MODE_LABEL, type VatMode } from '../../../shared/ui/status'
import type { ProductResponse, QuoteDetailResponse, UpdateQuoteRequest } from '../../../shared/api/types'
import { fetchProducts } from '../../product/api'
import { useUpdateQuote } from '../hooks'

/**
 * 견적 편집기 (10 §5.4 · QT-02~11·23·24) — 작성 중(DRAFT)에만 나타난다. 발송 뒤에는 읽기 전용 (QT-16).
 *
 *  - 카탈로그 선택 시 품목명·단위·단가가 자동 채워지고 그 값이 저장된다 (QT-24). 판매 중지 상품은 목록에 없다 (PR-06)
 *  - 단가를 카탈로그와 다르게 쓰면 amber 배지 — 할인 필드가 아니라 단가 직접 수정 (Q-02). 편집 중 비교 대상은 카탈로그의 현재 단가
 *  - 금액은 입력 중 클라이언트가 미리 계산하되, 저장 후 서버 값으로 덮어쓴다 (QT-08·22)
 *  - 전체 갱신(PUT)이므로 항목 편집은 로컬에서 모으고 저장 때 한 번에
 *  - 순서는 위/아래 버튼 (드래그 핸들 대신 — QT-07의 순서 변경은 만족한다)
 */

interface Row {
  key: string
  productId: string | null
  name: string
  unit: string
  quantity: number
  unitPrice: number
}

interface Props {
  quote: QuoteDetailResponse
  /** 미저장 변경이 있으면 저장부터 하고 부른다 */
  onSend: () => void
  onPreview: () => void
}

const NONE = '__manual__'
const SELECT_CONTENT = { position: 'popper', style: { maxHeight: 'min(328px, var(--radix-select-content-available-height))' } } as const

const toRows = (quote: QuoteDetailResponse): Row[] =>
  quote.items.slice().sort((a, b) => a.sortOrder - b.sortOrder).map((it) => ({ key: it.id, productId: it.productId, name: it.name, unit: it.unit, quantity: it.quantity, unitPrice: it.unitPrice }))

/** 클라이언트 미리 계산 — 서버와 같은 규칙 (별도: vat=supply×10% · 포함: total=합, vat=total×10/110) */
function preview(rows: Row[], vatMode: VatMode) {
  const sum = rows.reduce((acc, r) => acc + r.quantity * r.unitPrice, 0)
  if (vatMode === 'INCLUDED') {
    const vat = Math.round((sum * 10) / 110)
    return { supply: sum - vat, vat, total: sum }
  }
  const vat = Math.round(sum * 0.1)
  return { supply: sum, vat, total: sum + vat }
}

export function QuoteEditor({ quote, onSend, onPreview }: Props) {
  const [validUntil, setValidUntil] = useState(quote.validUntil)
  const [vatMode, setVatMode] = useState<VatMode>(quote.vatMode)
  const [terms, setTerms] = useState(quote.terms ?? '')
  const [rows, setRows] = useState<Row[]>(() => toRows(quote))
  const [catalogPick, setCatalogPick] = useState('')
  const update = useUpdateQuote(quote.id)

  // 판매 중지 상품은 선택 목록에 나타나지 않는다 (PR-06)
  const products = useQuery({ queryKey: ['product', 'list', { status: 'ACTIVE', size: 100 }], queryFn: () => fetchProducts({ status: 'ACTIVE', size: 100 }) })
  const catalog = useMemo(() => new Map((products.data?.content ?? []).map((p) => [p.id, p])), [products.data])

  const body: UpdateQuoteRequest = useMemo(
    () => ({
      validUntil, vatMode, terms: terms.trim() || null, version: quote.version,
      items: rows.map((r, i) => ({ productId: r.productId, name: r.name.trim(), unit: r.unit.trim(), quantity: r.quantity, unitPrice: r.unitPrice, sortOrder: i })),
    }),
    [validUntil, vatMode, terms, rows, quote.version],
  )
  const saved: UpdateQuoteRequest = useMemo(
    () => ({
      validUntil: quote.validUntil, vatMode: quote.vatMode, terms: quote.terms, version: quote.version,
      items: toRows(quote).map((r, i) => ({ productId: r.productId, name: r.name, unit: r.unit, quantity: r.quantity, unitPrice: r.unitPrice, sortOrder: i })),
    }),
    [quote],
  )
  const dirty = JSON.stringify(body) !== JSON.stringify(saved)
  const amounts = preview(rows, vatMode)
  const today = new Date().toISOString().slice(0, 10)
  const canSubmit = rows.length > 0 && validUntil > today && rows.every((r) => r.name.trim() && r.unit.trim() && r.quantity > 0 && r.unitPrice >= 0)
  const apiError = update.error instanceof ApiError ? update.error : null

  const setRow = (key: string, patch: Partial<Row>) => setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...patch } : r)))
  const move = (index: number, dir: -1 | 1) =>
    setRows((prev) => {
      const next = prev.slice()
      const target = index + dir
      if (target < 0 || target >= next.length) return prev
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  const addCatalog = (product: ProductResponse) =>
    setRows((prev) => [...prev, { key: crypto.randomUUID(), productId: product.id, name: product.name, unit: product.unit, quantity: 1, unitPrice: product.unitPrice }])
  const addManual = () => setRows((prev) => [...prev, { key: crypto.randomUUID(), productId: null, name: '', unit: '식', quantity: 1, unitPrice: 0 }])

  const save = () => update.mutateAsync(body)
  const handleSend = async () => {
    if (dirty) {
      try {
        await save()
      } catch {
        return // 저장 실패는 ErrorCallout으로 보인다 — 발송으로 넘어가지 않는다
      }
    }
    onSend()
  }

  return (
    <Card size="3" className="enter-fade">
      <Grid columns={{ initial: '1', sm: '200px 160px' }} gap="4" mb="4">
        <Field label="유효기간" required error={apiError?.reasonOf('validUntil')} hint="유효기간이 곧 고객 열람 링크의 만료일입니다 (Q-17)">
          <TextField.Root type="date" min={today} value={validUntil} onChange={(e) => setValidUntil(e.target.value)} disabled={update.isPending} color={validUntil <= today ? 'red' : undefined} />
        </Field>
        <Field label="부가세" required>
          <Select.Root value={vatMode} onValueChange={(v) => setVatMode(v as VatMode)} disabled={update.isPending}>
            <Select.Trigger style={{ width: '100%' }} />
            <Select.Content {...SELECT_CONTENT}>
              {VAT_MODES.map((mode) => (
                <Select.Item key={mode} value={mode}>
                  {VAT_MODE_LABEL[mode]}
                </Select.Item>
              ))}
            </Select.Content>
          </Select.Root>
        </Field>
      </Grid>

      {/* 품목 */}
      <Table.Root variant="ghost" size="2">
        <Table.Header>
          <Table.Row>
            <Table.ColumnHeaderCell width="36px" />
            <Table.ColumnHeaderCell>품목</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell width="90px">단위</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell width="100px" align="right">수량</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell width="150px" align="right">단가</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell width="140px" align="right">금액</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell width="44px" />
          </Table.Row>
        </Table.Header>
        <Table.Body>
          {rows.map((row, index) => {
            const product = row.productId ? catalog.get(row.productId) : undefined
            const adjusted = product && product.unitPrice !== row.unitPrice
            const rate = product && product.unitPrice > 0 ? Math.round(((row.unitPrice - product.unitPrice) / product.unitPrice) * 100) : 0
            return (
              <Table.Row key={row.key}>
                <Table.Cell>
                  <Flex direction="column" gap="1">
                    <IconButton size="1" variant="ghost" color="gray" aria-label="위로" disabled={index === 0} onClick={() => move(index, -1)}>
                      <ArrowUpIcon />
                    </IconButton>
                    <IconButton size="1" variant="ghost" color="gray" aria-label="아래로" disabled={index === rows.length - 1} onClick={() => move(index, 1)}>
                      <ArrowDownIcon />
                    </IconButton>
                  </Flex>
                </Table.Cell>
                <Table.RowHeaderCell>
                  <TextField.Root value={row.name} placeholder="품목명" onChange={(e) => setRow(row.key, { name: e.target.value })} color={apiError?.reasonOf(`items[${index}].name`) ? 'red' : undefined} />
                  <Flex gap="1" mt="1" wrap="wrap">
                    {product ? (
                      adjusted ? (
                        <Badge color="amber" variant="soft" size="1">
                          카탈로그 <Money value={product.unitPrice} size="1" />원 → {rate > 0 ? '+' : ''}{rate}% 조정
                        </Badge>
                      ) : (
                        <Badge color="gray" variant="soft" size="1">카탈로그 · {product.name}</Badge>
                      )
                    ) : (
                      <Badge color="gray" variant="soft" size="1">직접 입력 · 카탈로그에 없음</Badge>
                    )}
                  </Flex>
                </Table.RowHeaderCell>
                <Table.Cell>
                  <TextField.Root value={row.unit} placeholder="개" onChange={(e) => setRow(row.key, { unit: e.target.value })} />
                </Table.Cell>
                <Table.Cell align="right">
                  <TextField.Root type="number" min={1} step={1} value={row.quantity} onChange={(e) => setRow(row.key, { quantity: Math.max(0, Math.floor(Number(e.target.value) || 0)) })} style={{ textAlign: 'right' }} />
                </Table.Cell>
                <Table.Cell align="right">
                  <TextField.Root type="number" min={0} step={1000} value={row.unitPrice} color={adjusted ? 'amber' : undefined} onChange={(e) => setRow(row.key, { unitPrice: Math.max(0, Math.floor(Number(e.target.value) || 0)) })} style={{ textAlign: 'right' }} />
                </Table.Cell>
                <Table.Cell align="right">
                  <Money value={row.quantity * row.unitPrice} />
                </Table.Cell>
                <Table.Cell>
                  <IconButton size="1" variant="ghost" color="red" aria-label="항목 삭제" onClick={() => setRows((prev) => prev.filter((r) => r.key !== row.key))}>
                    <TrashIcon />
                  </IconButton>
                </Table.Cell>
              </Table.Row>
            )
          })}
        </Table.Body>
      </Table.Root>

      <Flex gap="3" mt="3" align="center" wrap="wrap">
        <Select.Root
          value={catalogPick}
          onValueChange={(id) => {
            const product = catalog.get(id)
            if (product) addCatalog(product)
            setCatalogPick('')
          }}
          disabled={products.isPending}
        >
          <Select.Trigger placeholder="+ 카탈로그에서 추가" variant="soft" style={{ minWidth: 200 }} />
          <Select.Content {...SELECT_CONTENT}>
            {(products.data?.content ?? []).map((p) => (
              <Select.Item key={p.id} value={p.id}>
                {p.name} · {p.unitPrice.toLocaleString('ko-KR')}원/{p.unit}
              </Select.Item>
            ))}
            {products.data && products.data.content.length === 0 && (
              <Select.Item value={NONE} disabled>
                등록된 상품이 없습니다
              </Select.Item>
            )}
          </Select.Content>
        </Select.Root>
        <Button variant="soft" color="gray" onClick={addManual}>
          <PlusIcon /> 직접 입력
        </Button>
        <Text size="1" color="gray">판매 중지된 상품은 선택 목록에 나타나지 않습니다</Text>
      </Flex>

      {rows.length === 0 && (
        <Callout.Root color="gray" mt="3" size="1">
          <Callout.Text>항목을 1개 이상 추가해야 발송할 수 있습니다 (QT-15).</Callout.Text>
        </Callout.Root>
      )}

      <Separator size="4" my="4" />

      <Grid columns={{ initial: '1', sm: '1fr 280px' }} gap="5">
        <Field label="안내 문구" hint={`${terms.length}/2000 · 납기·설치·결제 조건처럼 고객이 알아야 할 것`} error={apiError?.reasonOf('terms')}>
          <TextArea rows={4} maxLength={2000} value={terms} placeholder="예: 설치는 납품일로부터 3일 이내 진행됩니다." onChange={(e) => setTerms(e.target.value)} disabled={update.isPending} />
        </Field>
        <Flex direction="column" align="end" gap="1">
          <AmountRow label="공급가액" value={amounts.supply} />
          <AmountRow label={`부가세(${VAT_MODE_LABEL[vatMode]})`} value={amounts.vat} />
          <Flex align="baseline" gap="4" mt="1">
            <Text size="2" color="gray">합계</Text>
            <Money value={amounts.total} unit size="5" weight="bold" />
          </Flex>
          <Text size="1" color="gray" mt="1">
            <InfoCircledIcon style={{ verticalAlign: '-2px' }} /> 저장하면 서버가 계산한 값으로 저장됩니다
          </Text>
        </Flex>
      </Grid>

      {apiError && apiError.code !== 'VALIDATION_FAILED' && <ErrorCallout code={apiError.code} />}
      {apiError?.code === 'VALIDATION_FAILED' && apiError.reasonOf('items') && <ErrorCallout code="VALIDATION_FAILED" />}

      <Flex justify="end" gap="2" mt="4" align="center" wrap="wrap">
        {dirty && (
          <Text size="1" color="amber" mr="auto">
            저장하지 않은 변경이 있습니다
          </Text>
        )}
        <Tooltip content={dirty ? '저장한 내용 기준으로 미리보기가 열립니다' : '고객이 보는 화면'}>
          <Button variant="soft" color="gray" onClick={onPreview}>
            <EyeOpenIcon /> 미리보기
          </Button>
        </Tooltip>
        <Button variant="soft" onClick={() => save().catch(() => {})} loading={update.isPending} disabled={!dirty || !canSubmit}>
          <Pencil2Icon /> 임시 저장
        </Button>
        <Button onClick={handleSend} loading={update.isPending} disabled={!canSubmit}>
          <PaperPlaneIcon /> 발송하기
        </Button>
      </Flex>
    </Card>
  )
}

function AmountRow({ label, value }: { label: string; value: number }) {
  return (
    <Flex align="baseline" gap="4">
      <Text size="2" color="gray">{label}</Text>
      <Money value={value} size="3" />
    </Flex>
  )
}
