import { useState, type FormEvent } from 'react'
import { Button, Callout, Flex, Grid, Text, TextField } from '@radix-ui/themes'
import { CheckCircledIcon } from '@radix-ui/react-icons'
import { ApiError } from '../../../shared/api/client'
import { ErrorCallout, Field, RoleBadge } from '../../../shared/ui'
import { useSession } from '../../../app/session'
import { useUpdateMe } from '../hooks'

/**
 * 프로필 (AU-07 · 08 §A UpdateMeRequest — name 100 · phone 30).
 * 이메일·회사·역할은 본인이 바꿀 수 없다 — 역할 변경은 기업 관리자(MB-08), 이메일은 계정 식별자(Q-14).
 */
export function ProfileForm() {
  const session = useSession()
  const [name, setName] = useState(session.name)
  const [phone, setPhone] = useState(session.phone ?? '')
  const [saved, setSaved] = useState(false)
  const mutation = useUpdateMe()
  const apiError = mutation.error instanceof ApiError ? mutation.error : null

  const dirty = name.trim() !== session.name || (phone.trim() || null) !== session.phone

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    setSaved(false)
    mutation.mutate({ name: name.trim(), phone: phone.trim() || null }, { onSuccess: () => setSaved(true) })
  }

  return (
    <form onSubmit={handleSubmit}>
      <Flex direction="column" gap="4" style={{ maxWidth: 480 }}>
        {saved && !dirty && (
          <Callout.Root color="green" size="1">
            <Callout.Icon>
              <CheckCircledIcon />
            </Callout.Icon>
            <Callout.Text>저장되었습니다.</Callout.Text>
          </Callout.Root>
        )}
        {apiError && apiError.code !== 'VALIDATION_FAILED' && <ErrorCallout code={apiError.code} />}

        <Grid columns={{ initial: '1', sm: '2' }} gap="3">
          <Field label="회사">
            <Text size="2">{session.companyName}</Text>
          </Field>
          <Field label="역할">
            <RoleBadge role={session.role} />
          </Field>
        </Grid>

        <Field label="이메일" hint="로그인 계정이라 바꿀 수 없습니다.">
          <TextField.Root value={session.email} readOnly disabled />
        </Field>

        <Field label="이름" required htmlFor="me-name" error={apiError?.reasonOf('name')}>
          <TextField.Root id="me-name" value={name} maxLength={100} onChange={(e) => setName(e.target.value)} color={apiError?.reasonOf('name') ? 'red' : undefined} disabled={mutation.isPending} />
        </Field>

        <Field label="연락처" htmlFor="me-phone" hint="고객 열람 페이지의 담당자 연락처로 표시됩니다 (AP-18)." error={apiError?.reasonOf('phone')}>
          <TextField.Root id="me-phone" type="tel" value={phone} maxLength={30} placeholder="010-0000-0000" onChange={(e) => setPhone(e.target.value)} color={apiError?.reasonOf('phone') ? 'red' : undefined} disabled={mutation.isPending} />
        </Field>

        <Flex justify="end">
          <Button type="submit" loading={mutation.isPending} disabled={!name.trim() || !dirty}>
            저장
          </Button>
        </Flex>
      </Flex>
    </form>
  )
}
