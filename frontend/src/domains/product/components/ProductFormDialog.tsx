import { useState, type FormEvent } from 'react'
import { Button, Dialog, Flex, TextArea, TextField } from '@radix-ui/themes'
import { ApiError } from '../../../shared/api/client'
import { ErrorCallout, Field } from '../../../shared/ui'
import type { CreateProductRequest, ProductResponse } from '../../../shared/api/types'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 넘기면 수정, 없으면 등록 */
  product?: ProductResponse
  loading: boolean
  error: unknown
  onSubmit: (body: CreateProductRequest) => void
}

interface Form {
  name: string
  unit: string
  unitPrice: string
  description: string
}

const toForm = (product?: ProductResponse): Form => ({
  name: product?.name ?? '',
  unit: product?.unit ?? '',
  unitPrice: product?.unitPrice === undefined ? '' : String(product.unitPrice),
  description: product?.description ?? '',
})

/**
 * 상품 등록·수정 (PR-01·02·04 · product/dto Create/UpdateProductRequest).
 * 되돌릴 수 있는 입력이라 Dialog (10 §2.5). 이름 중복(409 PRODUCT_NAME_DUPLICATED)은 폼 안에 표시한다.
 * 단가·이름을 바꿔도 이미 작성된 견적은 움직이지 않는다 (PR-07·08, QT-24) — 사용자가 알아야 할 사실이라 설명에 적는다.
 * 수정은 PATCH라 null = 미변경이다 (Product.update) — 설명을 비운 건 ''로 보내야 지워진다. 등록은 null이 "없음"이다.
 */
export function ProductFormDialog({ open, onOpenChange, product, loading, error, onSubmit }: Props) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content maxWidth="480px">
        {/* 폼 상태는 내부 컴포넌트에 둔다 — Dialog 닫힘 시 언마운트되어 초기화된다 */}
        <ProductForm product={product} loading={loading} error={error} onSubmit={onSubmit} />
      </Dialog.Content>
    </Dialog.Root>
  )
}

function ProductForm({ product, loading, error, onSubmit }: Omit<Props, 'open' | 'onOpenChange'>) {
  const [form, setForm] = useState<Form>(() => toForm(product))
  const apiError = error instanceof ApiError ? error : null
  const priceNumber = Number(form.unitPrice)
  const priceInvalid = form.unitPrice !== '' && (!Number.isInteger(priceNumber) || priceNumber < 0)

  const set = (key: keyof Form) => (value: string) => setForm((prev) => ({ ...prev, [key]: value }))

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    const description = form.description.trim()
    onSubmit({
      name: form.name.trim(),
      unit: form.unit.trim(),
      unitPrice: priceNumber,
      description: description || (product ? '' : null),
    })
  }

  const canSubmit = form.name.trim() !== '' && form.unit.trim() !== '' && form.unitPrice !== '' && !priceInvalid

  return (
    <>
      <Dialog.Title>{product ? '상품 정보 수정' : '상품 등록'}</Dialog.Title>
      <Dialog.Description size="2" color="gray">
        {product
          ? '이미 작성된 견적의 금액은 바뀌지 않습니다 — 견적에는 작성 시점의 단가가 기록됩니다.'
          : '회사 안에서 상품명은 하나뿐입니다. 판매 중지된 상품과도 겹칠 수 없습니다.'}
      </Dialog.Description>

      <form onSubmit={handleSubmit}>
        <Flex direction="column" gap="4" mt="4">
          <Field label="상품명" required error={apiError?.reasonOf('name')}>
            <TextField.Root
              value={form.name}
              onChange={(e) => set('name')(e.target.value)}
              placeholder="예: 1200 사무책상"
              color={apiError?.reasonOf('name') ? 'red' : undefined}
              autoFocus
              disabled={loading}
              maxLength={255}
            />
          </Field>

          <Flex gap="3">
            <Field label="단위" required error={apiError?.reasonOf('unit')} grow>
              <TextField.Root
                value={form.unit}
                onChange={(e) => set('unit')(e.target.value)}
                placeholder="개 · 식 · 세트"
                disabled={loading}
                maxLength={50}
              />
            </Field>
            <Field label="단가 (원)" required error={apiError?.reasonOf('unitPrice') ?? (priceInvalid ? '0원 이상의 정수여야 합니다.' : undefined)} grow>
              <TextField.Root
                type="number"
                inputMode="numeric"
                min={0}
                step={1}
                value={form.unitPrice}
                onChange={(e) => set('unitPrice')(e.target.value)}
                placeholder="180000"
                color={priceInvalid ? 'red' : undefined}
                disabled={loading}
              />
            </Field>
          </Flex>

          <Field label="설명" hint="견적서에는 실리지 않습니다. 구성원이 고를 때 참고하는 메모입니다.">
            <TextArea
              value={form.description}
              onChange={(e) => set('description')(e.target.value)}
              placeholder="예: 폭 1200mm 표준 사무용 책상"
              rows={3}
              disabled={loading}
            />
          </Field>

          {apiError && apiError.code !== 'VALIDATION_FAILED' && <ErrorCallout code={apiError.code} />}

          <Flex gap="3" justify="end" mt="2">
            <Dialog.Close>
              <Button type="button" variant="soft" color="gray" disabled={loading}>
                취소
              </Button>
            </Dialog.Close>
            <Button type="submit" loading={loading} disabled={!canSubmit}>
              {product ? '저장' : '등록'}
            </Button>
          </Flex>
        </Flex>
      </form>
    </>
  )
}
