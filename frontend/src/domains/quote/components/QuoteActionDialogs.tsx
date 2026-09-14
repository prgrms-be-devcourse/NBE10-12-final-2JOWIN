import { useState } from 'react'
import { Button, Callout, Card, Dialog, Flex, Select, Text, TextArea } from '@radix-ui/themes'
import { ExclamationTriangleIcon } from '@radix-ui/react-icons'
import { ApiError, codeOf } from '../../../shared/api/client'
import { ConfirmDialog, ErrorCallout, Field, Money } from '../../../shared/ui'
import { VAT_MODE_LABEL } from '../../../shared/ui/status'
import type { ContactResponse, QuoteDetailResponse } from '../../../shared/api/types'
import { useQuoteRecipients } from '../hooks'

/**
 * 견적 액션 모달 — 되돌릴 수 있는가로 컴포넌트를 가른다 (10 §2.5).
 *  - 발송·재발송 → `Dialog` (수신인을 고르는 입력이 있고 취소해도 잃는 것이 없다)
 *  - 회수·링크 만료·주문 전환 → `ConfirmDialog`(AlertDialog)
 */

const SELECT_CONTENT = { position: 'popper', style: { maxHeight: 'min(328px, var(--radix-select-content-available-height))' } } as const

/** 수신인 Select — 딜 고객사의 담당자만, 기본은 대표 (AP-01, Q-07) */
function RecipientSelect({
  contacts, value, onChange, disabled,
}: { contacts: ContactResponse[]; value: string; onChange: (id: string) => void; disabled?: boolean }) {
  const selected = contacts.find((c) => c.id === value)
  return (
    <Field label="받는 사람" required hint={selected ? `${selected.primary ? '대표 담당자 · ' : ''}${selected.email}` : undefined}>
      <Select.Root value={value} onValueChange={onChange} disabled={disabled || contacts.length === 0}>
        <Select.Trigger placeholder={contacts.length ? '담당자 선택' : '고객사에 담당자가 없습니다'} style={{ width: '100%' }} />
        <Select.Content {...SELECT_CONTENT}>
          {contacts.map((c) => (
            <Select.Item key={c.id} value={c.id}>
              {c.name}
              {c.title ? ` (${c.title})` : ''}
              {c.primary ? ' · 대표' : ''}
            </Select.Item>
          ))}
        </Select.Content>
      </Select.Root>
    </Field>
  )
}

interface SendProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  quote: QuoteDetailResponse
  loading: boolean
  error: unknown
  onSubmit: (recipientContactId: string, message: string | null) => void
}

/**
 * 경로별 행동 안내 (#300) — 부록 문구(`errors.ts`)는 <b>상태만</b> 알리고, 무엇을 할지는 여기서 말한다.
 *
 * 같은 코드라도 취할 행동이 경로마다 다르다. `QUOTE_VALID_UNTIL_PASSED`는 발송(작성 중)에서는
 * 편집기의 유효기간을 고치면 되지만, 재발송에서는 발송된 견적이라 그 값을 바꿀 수 없어(QT-16)
 * 복제(QT-19)뿐이다. 부록에 한쪽 행동을 박으면 다른 쪽에서 틀린 안내가 된다.
 */
const ACTION_HINTS: Record<'send' | 'resend', Record<string, string>> = {
  send: {
    QUOTE_VALID_UNTIL_PASSED: '유효기간을 다시 지정한 뒤 발송해 주세요.',
  },
  resend: {
    QUOTE_VALID_UNTIL_PASSED: '발송된 견적은 유효기간을 바꿀 수 없습니다 — 복제해 새 견적으로 보내 주세요.',
    QUOTE_NOT_RESENDABLE: '발송됨·열람됨 상태에서만 재발송할 수 있습니다. 다시 제안하려면 복제해 주세요.',
  },
}

function ActionHint({ code, path }: { code: string; path: 'send' | 'resend' }) {
  const hint = ACTION_HINTS[path][code]
  return hint ? (
    <Text size="1" color="gray" mt="-2">
      {hint}
    </Text>
  ) : null
}

/** 발송 모달 (10 §5.5) — 되돌릴 수 없는 효과 3줄을 발송 전에 알린다 */
export function SendQuoteDialog({ open, onOpenChange, quote, loading, error, onSubmit }: SendProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content maxWidth="480px">
        {open && <SendForm quote={quote} loading={loading} error={error} onSubmit={onSubmit} />}
      </Dialog.Content>
    </Dialog.Root>
  )
}

function SendForm({ quote, loading, error, onSubmit }: Omit<SendProps, 'open' | 'onOpenChange'>) {
  const recipients = useQuoteRecipients(quote.dealId)
  const contacts = recipients.data?.contacts ?? []
  const [recipient, setRecipient] = useState<string>('')
  const [message, setMessage] = useState('')
  // 기본 선택은 대표 담당자 — 응답이 오면 한 번만 채운다
  if (!recipient && contacts.length) setRecipient(contacts.find((c) => c.primary)?.id ?? contacts[0].id)
  const apiError = error instanceof ApiError ? error : null

  return (
    <>
      <Dialog.Title>견적을 발송합니다</Dialog.Title>
      <Dialog.Description size="2" color="gray">
        {quote.quoteNo} · {quote.dealTitle}
        {recipients.data && ` · ${recipients.data.customerName}`}
      </Dialog.Description>

      <Flex direction="column" gap="4" mt="4">
        {recipients.error ? (
          <ErrorCallout code={codeOf(recipients.error)} />
        ) : (
          <RecipientSelect contacts={contacts} value={recipient} onChange={setRecipient} disabled={loading || recipients.isPending} />
        )}

        <Field label="메시지 (선택)" hint={`${message.length}/500`}>
          <TextArea rows={3} value={message} maxLength={500} placeholder="안녕하세요, 요청하신 견적서입니다." disabled={loading} onChange={(e) => setMessage(e.target.value)} />
        </Field>

        <Callout.Root color="amber" size="1">
          <Callout.Icon>
            <ExclamationTriangleIcon />
          </Callout.Icon>
          <Callout.Text>
            발송하면
            <br />· 고객에게 열람 링크가 담긴 메일이 갑니다
            <br />· 이후 견적 내용은 수정할 수 없습니다
            <br />· 딜 단계가 '견적'으로 이동합니다
          </Callout.Text>
        </Callout.Root>

        {apiError && <ErrorCallout code={apiError.code} />}
        {apiError && <ActionHint code={apiError.code} path="send" />}

        <Flex gap="3" justify="end" mt="1">
          <Dialog.Close>
            <Button type="button" variant="soft" color="gray" disabled={loading}>
              취소
            </Button>
          </Dialog.Close>
          <Button loading={loading} disabled={!recipient} onClick={() => onSubmit(recipient, message.trim() || null)}>
            발송하기
          </Button>
        </Flex>
      </Flex>
    </>
  )
}

interface ResendProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  quote: QuoteDetailResponse
  loading: boolean
  error: unknown
  onSubmit: (recipientContactId: string) => void
}

/** 수신인 변경 재발송 (AP-13) — 기존 링크는 만료되고 새 링크가 나간다 */
export function ResendDialog({ open, onOpenChange, quote, loading, error, onSubmit }: ResendProps) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content maxWidth="440px">
        {open && <ResendForm quote={quote} loading={loading} error={error} onSubmit={onSubmit} />}
      </Dialog.Content>
    </Dialog.Root>
  )
}

function ResendForm({ quote, loading, error, onSubmit }: Omit<ResendProps, 'open' | 'onOpenChange'>) {
  const recipients = useQuoteRecipients(quote.dealId)
  const contacts = recipients.data?.contacts ?? []
  const [recipient, setRecipient] = useState('')
  const apiError = error instanceof ApiError ? error : null
  return (
    <>
      <Dialog.Title>수신인을 바꿔 재발송합니다</Dialog.Title>
      <Dialog.Description size="2" color="gray">
        기존 링크는 만료되고, 새 수신인에게 새 링크가 갑니다. 남은 유효기간은 그대로입니다.
      </Dialog.Description>
      <Flex direction="column" gap="4" mt="4">
        {recipients.error ? (
          <ErrorCallout code={codeOf(recipients.error)} />
        ) : (
          <RecipientSelect contacts={contacts} value={recipient} onChange={setRecipient} disabled={loading || recipients.isPending} />
        )}
        {apiError && <ErrorCallout code={apiError.code} />}
        {apiError && <ActionHint code={apiError.code} path="resend" />}
        <Flex gap="3" justify="end" mt="1">
          <Dialog.Close>
            <Button type="button" variant="soft" color="gray" disabled={loading}>
              취소
            </Button>
          </Dialog.Close>
          <Button loading={loading} disabled={!recipient} onClick={() => onSubmit(recipient)}>
            재발송
          </Button>
        </Flex>
      </Flex>
    </>
  )
}

interface ConfirmProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  loading: boolean
  error: unknown
  onConfirm: () => void
}

/** 회수 (QT-17) — 링크가 즉시 만료된다. 종결 상태라 되돌릴 수 없다 */
export function WithdrawDialog({ quote, ...props }: ConfirmProps & { quote: QuoteDetailResponse }) {
  return (
    <ConfirmDialog
      {...props}
      title={`${quote.quoteNo}을(를) 회수하시겠습니까?`}
      description="고객의 열람 링크가 즉시 만료되고 견적은 종결됩니다. 다시 제안하려면 복제해서 새로 보내야 합니다."
      confirmLabel="회수"
      confirmColor="red"
    >
      {props.error ? <ErrorCallout code={codeOf(props.error)} /> : null}
    </ConfirmDialog>
  )
}

/** 링크 수동 만료 (AP-14) — 견적 상태는 그대로, 링크만 닫힌다. 다른 수신인에게 재발송할 수 있다 */
export function ExpireLinkDialog(props: ConfirmProps) {
  return (
    <ConfirmDialog
      {...props}
      title="열람 링크를 만료하시겠습니까?"
      description="고객이 지금 가진 링크로는 더 이상 열람·응답할 수 없습니다. 견적은 발송된 상태로 남고, 수신인을 바꿔 다시 보낼 수 있습니다."
      confirmLabel="링크 만료"
      confirmColor="amber"
    >
      {props.error ? <ErrorCallout code={codeOf(props.error)} /> : null}
    </ConfirmDialog>
  )
}

/** 주문 전환 (OD-01~07) — 금액을 한 번 더 보여준다. 스냅샷이 생기고 딜이 성사된다 */
export function ConvertDialog({ quote, ...props }: ConfirmProps & { quote: QuoteDetailResponse }) {
  return (
    <ConfirmDialog
      {...props}
      title="주문으로 전환하시겠습니까?"
      description="전환 시점의 품목·금액이 주문에 그대로 복사되고, 딜은 성사(WON)로 이동합니다. 주문은 취소할 수 없습니다."
      confirmLabel="주문 전환"
      confirmColor="green"
    >
      <Card variant="surface" mt="3">
        <Text as="div" size="2" color="gray">
          {quote.quoteNo} · {quote.dealTitle}
        </Text>
        <Money value={quote.totalAmount} unit size="6" weight="bold" />
        <Text as="div" size="1" color="gray">
          부가세 {VAT_MODE_LABEL[quote.vatMode]}
        </Text>
      </Card>
      {props.error ? <ErrorCallout code={codeOf(props.error)} /> : null}
    </ConfirmDialog>
  )
}
