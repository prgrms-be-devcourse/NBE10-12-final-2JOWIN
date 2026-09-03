import { useState, type FormEvent } from 'react'
import { Button, Dialog, Flex, Select, Text, TextArea, TextField } from '@radix-ui/themes'
import { ApiError } from '../../../shared/api/client'
import { ErrorCallout } from '../../../shared/ui'
import type { CreateCustomerRequest, CustomerResponse } from '../../../shared/api/types'
import { COMPANY_SIZES, INDUSTRIES, SELECT_CONTENT } from '../constants'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 넘기면 수정, 없으면 등록 */
  customer?: CustomerResponse
  loading: boolean
  error: unknown
  onSubmit: (body: CreateCustomerRequest) => void
}

/** Select는 빈 값을 가질 수 없어 sentinel을 쓴다 */
const NONE = '__none__'

interface Form {
  name: string
  industry: string
  size: string
  note: string
}

const toForm = (customer?: CustomerResponse): Form => ({
  name: customer?.name ?? '',
  industry: customer?.industry ?? NONE,
  size: customer?.size ?? NONE,
  note: customer?.note ?? '',
})

/** 고객사 등록·수정 (CU-01·02·06). 되돌릴 수 있는 입력이라 Dialog (10 §2.5). fieldErrors는 입력 아래 표시 (§6.3) */
export function CustomerFormDialog({ open, onOpenChange, customer, loading, error, onSubmit }: Props) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content maxWidth="480px">
        {/* 폼 상태는 내부 컴포넌트에 둔다 — Dialog 닫힘 시 언마운트되어 초기화된다 */}
        <CustomerForm customer={customer} loading={loading} error={error} onSubmit={onSubmit} />
      </Dialog.Content>
    </Dialog.Root>
  )
}

function CustomerForm({ customer, loading, error, onSubmit }: Omit<Props, 'open' | 'onOpenChange'>) {
  const [form, setForm] = useState<Form>(() => toForm(customer))
  const apiError = error instanceof ApiError ? error : null
  const nameError = apiError?.reasonOf('name')

  const set = <K extends keyof Form>(key: K) => (value: Form[K]) => setForm((prev) => ({ ...prev, [key]: value }))

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    onSubmit({
      name: form.name.trim(),
      industry: form.industry === NONE ? null : form.industry,
      size: form.size === NONE ? null : form.size,
      note: form.note.trim() || null,
    })
  }

  return (
    <>
      <Dialog.Title>{customer ? '고객사 정보 수정' : '고객사 등록'}</Dialog.Title>
      <Dialog.Description size="2" color="gray">
        {customer ? '바꾼 내용은 회사 전체에 바로 반영됩니다.' : '등록한 고객사는 회사 구성원 모두가 볼 수 있습니다.'}
      </Dialog.Description>

      <form onSubmit={handleSubmit}>
        <Flex direction="column" gap="4" mt="4">
          <Field label="고객사명" required error={nameError}>
            <TextField.Root
              value={form.name}
              onChange={(e) => set('name')(e.target.value)}
              placeholder="예: 도담건설"
              color={nameError ? 'red' : undefined}
              autoFocus
              disabled={loading}
            />
          </Field>

          <Flex gap="3">
            <Field label="업종" grow>
              <Select.Root value={form.industry} onValueChange={set('industry')} disabled={loading}>
                <Select.Trigger placeholder="선택" style={{ width: '100%' }} />
                <Select.Content {...SELECT_CONTENT}>
                  <Select.Item value={NONE}>선택 안 함</Select.Item>
                  {INDUSTRIES.map((item) => (
                    <Select.Item key={item} value={item}>
                      {item}
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </Field>
            <Field label="규모" grow>
              <Select.Root value={form.size} onValueChange={set('size')} disabled={loading}>
                <Select.Trigger placeholder="선택" style={{ width: '100%' }} />
                <Select.Content {...SELECT_CONTENT}>
                  <Select.Item value={NONE}>선택 안 함</Select.Item>
                  {COMPANY_SIZES.map((item) => (
                    <Select.Item key={item} value={item}>
                      {item}
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </Field>
          </Flex>

          <Field label="비고" hint="어떻게 알게 됐는지, 어떤 상황인지 — 다음 사람이 읽고 바로 이해할 수 있게">
            <TextArea
              value={form.note}
              onChange={(e) => set('note')(e.target.value)}
              placeholder="예: 사무실 리모델링 진행 중 — 전시회에서 명함 교환"
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
            <Button type="submit" loading={loading} disabled={!form.name.trim()}>
              {customer ? '저장' : '등록'}
            </Button>
          </Flex>
        </Flex>
      </form>
    </>
  )
}

/** 라벨·입력·오류/힌트 공통 배치 */
export function Field({
  label, required, error, hint, grow, children,
}: { label: string; required?: boolean; error?: string; hint?: string; grow?: boolean; children: React.ReactNode }) {
  return (
    <Flex direction="column" gap="1" style={grow ? { flex: 1, minWidth: 0 } : undefined}>
      <Text as="label" size="2" weight="medium">
        {label}
        {required && (
          <Text color="red" aria-hidden>
            {' '}*
          </Text>
        )}
      </Text>
      {children}
      {error ? (
        <Text size="1" color="red">
          {error}
        </Text>
      ) : (
        hint && (
          <Text size="1" color="gray">
            {hint}
          </Text>
        )
      )}
    </Flex>
  )
}
