import { useState, type FormEvent } from 'react'
import { Button, Checkbox, Dialog, Flex, Text, TextField } from '@radix-ui/themes'
import { ApiError } from '../../../shared/api/client'
import { ErrorCallout } from '../../../shared/ui'
import type { ContactResponse, CreateContactRequest } from '../../../shared/api/types'
import { Field } from './CustomerFormDialog'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 넘기면 수정, 없으면 추가 */
  contact?: ContactResponse
  loading: boolean
  error: unknown
  /** makePrimary — 저장 후 set-primary 호출 여부 (CU-11) */
  onSubmit: (body: CreateContactRequest, makePrimary: boolean) => void
}

interface Form {
  name: string
  title: string
  phone: string
  email: string
}

const toForm = (contact?: ContactResponse): Form => ({
  name: contact?.name ?? '',
  title: contact?.title ?? '',
  phone: contact?.phone ?? '',
  email: contact?.email ?? '',
})

/** 담당자 추가·수정 (CU-09·10). 이메일은 견적 수신 주소라 필수 */
export function ContactFormDialog({ open, onOpenChange, contact, loading, error, onSubmit }: Props) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content maxWidth="440px">
        {/* 폼 상태는 내부 컴포넌트에 둔다 — Dialog 닫힘 시 언마운트되어 초기화된다 */}
        <ContactForm contact={contact} loading={loading} error={error} onSubmit={onSubmit} />
      </Dialog.Content>
    </Dialog.Root>
  )
}

function ContactForm({ contact, loading, error, onSubmit }: Omit<Props, 'open' | 'onOpenChange'>) {
  const [form, setForm] = useState<Form>(() => toForm(contact))
  const [makePrimary, setMakePrimary] = useState(false)
  const apiError = error instanceof ApiError ? error : null

  const set = (key: keyof Form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [key]: e.target.value }))

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    onSubmit(
      { name: form.name.trim(), title: form.title.trim() || null, phone: form.phone.trim() || null, email: form.email.trim() },
      makePrimary,
    )
  }

  const canSubmit = form.name.trim() !== '' && form.email.trim() !== ''

  return (
    <>
      <Dialog.Title>{contact ? '담당자 수정' : '담당자 추가'}</Dialog.Title>
      <Dialog.Description size="2" color="gray">
        견적은 대표 담당자의 이메일로 발송됩니다.
      </Dialog.Description>

      <form onSubmit={handleSubmit}>
        <Flex direction="column" gap="4" mt="4">
          <Flex gap="3">
            <Field label="이름" required error={apiError?.reasonOf('name')} grow>
              <TextField.Root
                value={form.name}
                onChange={set('name')}
                placeholder="이수정"
                autoFocus
                disabled={loading}
                color={apiError?.reasonOf('name') ? 'red' : undefined}
              />
            </Field>
            <Field label="직책" grow>
              <TextField.Root value={form.title} onChange={set('title')} placeholder="총무팀 대리" disabled={loading} />
            </Field>
          </Flex>
          <Field label="이메일" required error={apiError?.reasonOf('email')}>
            <TextField.Root
              type="email"
              value={form.email}
              onChange={set('email')}
              placeholder="name@company.co.kr"
              disabled={loading}
              color={apiError?.reasonOf('email') ? 'red' : undefined}
            />
          </Field>
          <Field label="연락처" error={apiError?.reasonOf('phone')}>
            <TextField.Root type="tel" value={form.phone} onChange={set('phone')} placeholder="010-0000-0000" disabled={loading} />
          </Field>

          {/* 이미 대표면 표시하지 않는다 */}
          {!contact?.primary && (
            <Text as="label" size="2">
              <Flex align="center" gap="2">
                <Checkbox checked={makePrimary} onCheckedChange={(value) => setMakePrimary(value === true)} disabled={loading} />
                대표 담당자로 지정
                <Text size="1" color="gray">
                  — 견적이 이 사람에게 갑니다
                </Text>
              </Flex>
            </Text>
          )}

          {apiError && apiError.code !== 'VALIDATION_FAILED' && <ErrorCallout code={apiError.code} />}

          <Flex gap="3" justify="end" mt="2">
            <Dialog.Close>
              <Button type="button" variant="soft" color="gray" disabled={loading}>
                취소
              </Button>
            </Dialog.Close>
            <Button type="submit" loading={loading} disabled={!canSubmit}>
              {contact ? '저장' : '추가'}
            </Button>
          </Flex>
        </Flex>
      </form>
    </>
  )
}
