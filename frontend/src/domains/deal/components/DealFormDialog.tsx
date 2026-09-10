import { useState, type FormEvent } from 'react'
import { Button, Dialog, Flex, Select, Text, TextField } from '@radix-ui/themes'
import { ApiError } from '../../../shared/api/client'
import { ErrorCallout, Field } from '../../../shared/ui'
import type { CreateDealRequest, DealDetailResponse, UpdateDealRequest } from '../../../shared/api/types'
import { useSession, hasCompanyWideScope } from '../../../app/session'
import { useCustomerList } from '../../customer/hooks'
import { SELECT_CONTENT } from '../../customer/constants'
import { useMemberOptions } from '../hooks'

/**
 * 딜 생성·수정 (DL-01~04, 10 §5.2 「+ 새 딜」). 되돌릴 수 있는 입력이라 Dialog (10 §2.5).
 *
 * - 생성: 고객사(필수) · 제목(필수) · 예상 금액(선택, 0 이상) · 마감일(선택) · 담당자(기업 관리자만 노출, DL-04)
 * - 수정: 제목 · 예상 금액 · 마감일 + version — 고객사·담당자는 여기서 바꾸지 않는다 (담당자 변경은 별도 엔드포인트 DL-05)
 * - 수정은 PATCH라 null = 미변경이다 (DealRequests.UpdateDeal · Deal.update) — 값을 "미정"으로 되돌리는 경로가 v1에 없어
 *   비운 채 저장하면 서버가 무시한다. 화면은 비우기를 막고 이유를 말한다
 * - 409 STALE_VERSION은 [새로고침]으로 상세를 재조회한다 (10 §6.3) — 입력은 그대로 두고 새 version으로 다시 저장할 수 있다
 * - 영업 담당자는 담당자 필드를 보지 않는다 — assigneeMemberId를 생략하면 생성자 본인 (CreateDealRequest)
 */

interface CreateProps {
  mode: 'create'
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 고객사 상세에서 열 때 미리 채운다 */
  defaultCustomerId?: string
  loading: boolean
  error: unknown
  onSubmit: (body: CreateDealRequest) => void
}

interface EditProps {
  mode: 'edit'
  open: boolean
  onOpenChange: (open: boolean) => void
  deal: DealDetailResponse
  loading: boolean
  error: unknown
  /** STALE_VERSION일 때 상세 재조회 */
  onRetry?: () => void
  onSubmit: (body: UpdateDealRequest) => void
}

type Props = CreateProps | EditProps

export function DealFormDialog(props: Props) {
  return (
    <Dialog.Root open={props.open} onOpenChange={props.onOpenChange}>
      <Dialog.Content maxWidth="480px">
        {/* 폼 상태는 내부 컴포넌트에 둔다 — Dialog 닫힘 시 언마운트되어 초기화된다 */}
        <DealForm {...props} />
      </Dialog.Content>
    </Dialog.Root>
  )
}

const NONE = '__none__'

interface Form {
  customerId: string
  title: string
  expectedAmount: string
  dueDate: string
  assigneeMemberId: string
}

function DealForm(props: Props) {
  const session = useSession()
  const canAssign = props.mode === 'create' && hasCompanyWideScope(session)
  const customers = useCustomerList({ size: 100 })
  const options = useMemberOptions(canAssign)

  const [form, setForm] = useState<Form>(() =>
    props.mode === 'edit'
      ? { customerId: props.deal.customerId, title: props.deal.title, expectedAmount: props.deal.expectedAmount === null ? '' : String(props.deal.expectedAmount), dueDate: props.deal.dueDate ?? '', assigneeMemberId: props.deal.assigneeMemberId }
      : { customerId: props.defaultCustomerId ?? NONE, title: '', expectedAmount: '', dueDate: '', assigneeMemberId: session.memberId },
  )
  const apiError = props.error instanceof ApiError ? props.error : null
  const set = <K extends keyof Form>(key: K) => (value: Form[K]) => setForm((prev) => ({ ...prev, [key]: value }))

  const amount = form.expectedAmount.trim() === '' ? null : Number(form.expectedAmount.replace(/,/g, ''))
  const amountInvalid = amount !== null && (!Number.isInteger(amount) || amount < 0)
  // 수정 모드에서 있던 값을 비우면 서버는 미변경으로 본다 — 저장을 막고 안내한다
  const amountCleared = props.mode === 'edit' && props.deal.expectedAmount !== null && amount === null
  const dueDateCleared = props.mode === 'edit' && props.deal.dueDate !== null && form.dueDate === ''
  const canSubmit = form.title.trim() !== '' && !amountInvalid && !amountCleared && !dueDateCleared && (props.mode === 'edit' || form.customerId !== NONE)

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!canSubmit) return
    if (props.mode === 'edit') {
      props.onSubmit({ title: form.title.trim(), expectedAmount: amount, dueDate: form.dueDate || null, version: props.deal.version })
    } else {
      props.onSubmit({
        customerId: form.customerId,
        title: form.title.trim(),
        expectedAmount: amount,
        dueDate: form.dueDate || null,
        // 관리자만 다른 사람에게 배정한다 — 그 외는 생략(= 생성자 본인)
        assigneeMemberId: canAssign && form.assigneeMemberId !== session.memberId ? form.assigneeMemberId : null,
      })
    }
  }

  return (
    <>
      <Dialog.Title>{props.mode === 'edit' ? '딜 수정' : '새 딜'}</Dialog.Title>
      <Dialog.Description size="2" color="gray">
        {props.mode === 'edit' ? '단계·담당자는 상세 화면의 버튼으로 바꿉니다.' : '리드 단계로 시작합니다. 담당자를 지정하지 않으면 본인이 담당합니다.'}
      </Dialog.Description>

      <form onSubmit={handleSubmit}>
        <Flex direction="column" gap="4" mt="4">
          {props.mode === 'create' && (
            <Field label="고객사" required error={apiError?.reasonOf('customerId')}>
              <Select.Root value={form.customerId} onValueChange={set('customerId')} disabled={props.loading || customers.isPending}>
                <Select.Trigger placeholder="고객사 선택" style={{ width: '100%' }} />
                <Select.Content {...SELECT_CONTENT}>
                  <Select.Item value={NONE} disabled>
                    고객사 선택
                  </Select.Item>
                  {customers.data?.content.map((c) => (
                    <Select.Item key={c.id} value={c.id}>
                      {c.name}
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </Field>
          )}

          <Field label="딜 제목" required error={apiError?.reasonOf('title')}>
            <TextField.Root
              value={form.title}
              onChange={(e) => set('title')(e.target.value)}
              placeholder="예: 도담건설 사무가구 납품"
              color={apiError?.reasonOf('title') ? 'red' : undefined}
              autoFocus={props.mode === 'edit'}
              disabled={props.loading}
            />
          </Field>

          <Flex gap="3">
            <Field
              label="예상 금액"
              hint={props.mode === 'edit' ? '원 단위 · 한 번 정한 금액은 미정으로 되돌릴 수 없습니다' : '원 단위 · 미정이면 비워 둡니다 (DL-02)'}
              error={amountInvalid ? '0 이상의 정수를 입력해 주세요.' : amountCleared ? '미정으로 되돌릴 수 없습니다. 금액을 입력해 주세요.' : apiError?.reasonOf('expectedAmount')}
              grow
            >
              <TextField.Root
                inputMode="numeric"
                value={form.expectedAmount}
                onChange={(e) => set('expectedAmount')(e.target.value)}
                placeholder="12,000,000"
                color={amountInvalid ? 'red' : undefined}
                disabled={props.loading}
              >
                <TextField.Slot side="right">
                  <Text size="1" color="gray">
                    원
                  </Text>
                </TextField.Slot>
              </TextField.Root>
            </Field>
            <Field label="마감일" error={dueDateCleared ? '마감일은 비울 수 없습니다. 날짜를 골라 주세요.' : apiError?.reasonOf('dueDate')} grow>
              <TextField.Root type="date" value={form.dueDate} onChange={(e) => set('dueDate')(e.target.value)} disabled={props.loading} />
            </Field>
          </Flex>

          {canAssign && (
            <Field label="담당자" hint="활성 구성원만 지정할 수 있습니다 (DL-04)" error={apiError?.reasonOf('assigneeMemberId')}>
              <Select.Root value={form.assigneeMemberId} onValueChange={set('assigneeMemberId')} disabled={props.loading || options.isPending}>
                <Select.Trigger style={{ width: '100%' }} />
                <Select.Content {...SELECT_CONTENT}>
                  {(options.data ?? [{ id: session.memberId, name: session.name }]).map((m) => (
                    <Select.Item key={m.id} value={m.id}>
                      {m.name}
                      {m.id === session.memberId ? ' (나)' : ''}
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </Field>
          )}

          {apiError && apiError.code !== 'VALIDATION_FAILED' && (
            <ErrorCallout code={apiError.code} onRetry={props.mode === 'edit' ? props.onRetry : undefined} />
          )}

          <Flex gap="3" justify="end" mt="2">
            <Dialog.Close>
              <Button type="button" variant="soft" color="gray" disabled={props.loading}>
                취소
              </Button>
            </Dialog.Close>
            <Button type="submit" loading={props.loading} disabled={!canSubmit}>
              {props.mode === 'edit' ? '저장' : '딜 만들기'}
            </Button>
          </Flex>
        </Flex>
      </form>
    </>
  )
}
