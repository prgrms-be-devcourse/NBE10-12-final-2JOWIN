import { useState, type FormEvent } from 'react'
import { Button, Dialog, Flex, Select, TextField } from '@radix-ui/themes'
import { ApiError } from '../../../shared/api/client'
import { ErrorCallout, Field, ROLES, ROLE_LABEL, type Role } from '../../../shared/ui'
import type { CreateInvitationRequest } from '../../../shared/api/types'
import { SELECT_CONTENT } from '../../customer/constants'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  loading: boolean
  error: unknown
  onSubmit: (body: CreateInvitationRequest) => void
}

/** 초대 발송 (MB-01·02, NT-01) — 역할은 필수. 타사 소속 이메일은 422 EMAIL_ALREADY_MEMBER (MB-13) */
export function InviteDialog({ open, onOpenChange, loading, error, onSubmit }: Props) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content maxWidth="440px">
        <InviteForm loading={loading} error={error} onSubmit={onSubmit} />
      </Dialog.Content>
    </Dialog.Root>
  )
}

function InviteForm({ loading, error, onSubmit }: Omit<Props, 'open' | 'onOpenChange'>) {
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<Role>('SALES_REP')
  const apiError = error instanceof ApiError ? error : null

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    onSubmit({ email: email.trim(), role })
  }

  return (
    <>
      <Dialog.Title>구성원 초대</Dialog.Title>
      <Dialog.Description size="2" color="gray">
        초대 메일의 링크로 이름과 비밀번호를 정하면 바로 활성 구성원이 됩니다. 링크는 7일간 유효합니다.
      </Dialog.Description>

      <form onSubmit={handleSubmit}>
        <Flex direction="column" gap="4" mt="4">
          <Field label="이메일" required error={apiError?.reasonOf('email')}>
            <TextField.Root
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="name@company.co.kr"
              color={apiError?.reasonOf('email') ? 'red' : undefined}
              autoFocus
              disabled={loading}
            />
          </Field>
          <Field label="역할" required error={apiError?.reasonOf('role')} hint="기업 관리자는 조직·카탈로그를 관리하고 회사 전체 Deal을 봅니다.">
            <Select.Root value={role} onValueChange={(value) => setRole(value as Role)} disabled={loading}>
              <Select.Trigger style={{ width: '100%' }} />
              <Select.Content {...SELECT_CONTENT}>
                {ROLES.map((r) => (
                  <Select.Item key={r} value={r}>
                    {ROLE_LABEL[r]}
                  </Select.Item>
                ))}
              </Select.Content>
            </Select.Root>
          </Field>

          {apiError && apiError.code !== 'VALIDATION_FAILED' && <ErrorCallout code={apiError.code} />}

          <Flex gap="3" justify="end" mt="2">
            <Dialog.Close>
              <Button type="button" variant="soft" color="gray" disabled={loading}>
                취소
              </Button>
            </Dialog.Close>
            <Button type="submit" loading={loading} disabled={!email.trim()}>
              초대 보내기
            </Button>
          </Flex>
        </Flex>
      </form>
    </>
  )
}
