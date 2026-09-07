import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { Button, Callout, Flex, TextField } from '@radix-ui/themes'
import { CheckCircledIcon, InfoCircledIcon } from '@radix-ui/react-icons'
import { ApiError, clearSession } from '../../../shared/api/client'
import { ErrorCallout, Field } from '../../../shared/ui'
import { messageOf } from '../../../shared/api/errors'
import { useChangePassword } from '../hooks'

/**
 * 비밀번호 변경 (AU-04 · 08 §A ChangePasswordRequest).
 * - 현재 비밀번호 불일치는 422 CURRENT_PASSWORD_MISMATCH — 세션은 유효하고 값만 틀렸다 (07 v1.6.5). 필드 아래에 표시
 * - 성공(204) 시 해당 구성원 refresh_token 전 행이 폐기된다(전이표 §9) → 본인도 다시 로그인해야 한다
 */
export function PasswordForm() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [done, setDone] = useState(false)
  const mutation = useChangePassword()
  const apiError = mutation.error instanceof ApiError ? mutation.error : null

  const tooShort = next.length > 0 && next.length < 8
  const mismatch = confirm.length > 0 && next !== confirm
  const currentError = apiError?.code === 'CURRENT_PASSWORD_MISMATCH' ? messageOf(apiError.code) : apiError?.reasonOf('currentPassword')

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (tooShort || mismatch) return
    mutation.mutate({ currentPassword: current, newPassword: next }, { onSuccess: () => setDone(true) })
  }

  const relogin = () => {
    clearSession()
    queryClient.clear()
    navigate('/login', { replace: true })
  }

  if (done) {
    return (
      <Flex direction="column" gap="4" style={{ maxWidth: 480 }}>
        <Callout.Root color="green">
          <Callout.Icon>
            <CheckCircledIcon />
          </Callout.Icon>
          <Callout.Text>비밀번호가 변경되었습니다. 보안을 위해 모든 기기의 로그인 세션이 종료됩니다 — 새 비밀번호로 다시 로그인해 주세요.</Callout.Text>
        </Callout.Root>
        <Flex justify="end">
          <Button onClick={relogin}>다시 로그인</Button>
        </Flex>
      </Flex>
    )
  }

  return (
    <form onSubmit={handleSubmit}>
      <Flex direction="column" gap="4" style={{ maxWidth: 480 }}>
        <Callout.Root color="gray" size="1">
          <Callout.Icon>
            <InfoCircledIcon />
          </Callout.Icon>
          <Callout.Text>변경하면 모든 기기에서 로그아웃되고 다시 로그인해야 합니다.</Callout.Text>
        </Callout.Root>
        {apiError && apiError.code !== 'VALIDATION_FAILED' && apiError.code !== 'CURRENT_PASSWORD_MISMATCH' && <ErrorCallout code={apiError.code} />}

        <Field label="현재 비밀번호" required htmlFor="pw-current" error={currentError}>
          <TextField.Root id="pw-current" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} color={currentError ? 'red' : undefined} disabled={mutation.isPending} />
        </Field>
        <Field label="새 비밀번호" required htmlFor="pw-next" hint="8자 이상" error={tooShort ? '8자 이상 입력해 주세요.' : apiError?.reasonOf('newPassword')}>
          <TextField.Root id="pw-next" type="password" autoComplete="new-password" value={next} onChange={(e) => setNext(e.target.value)} color={tooShort ? 'red' : undefined} disabled={mutation.isPending} />
        </Field>
        <Field label="새 비밀번호 확인" required htmlFor="pw-confirm" error={mismatch ? '비밀번호가 일치하지 않습니다.' : undefined}>
          <TextField.Root id="pw-confirm" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} color={mismatch ? 'red' : undefined} disabled={mutation.isPending} />
        </Field>

        <Flex justify="end">
          <Button type="submit" loading={mutation.isPending} disabled={!current || !next || !confirm || tooShort || mismatch}>
            비밀번호 변경
          </Button>
        </Flex>
      </Flex>
    </form>
  )
}
