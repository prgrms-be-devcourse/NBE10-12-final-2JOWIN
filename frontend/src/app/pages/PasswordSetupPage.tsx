import { useState, type FormEvent } from 'react'
import { Box, Button, Callout, Card, Flex, Heading, Text, TextField } from '@radix-ui/themes'
import { CheckCircledIcon } from '@radix-ui/react-icons'
import { useSearchParams } from 'react-router'
import { ErrorCallout } from '../../shared/ui'
import { ApiError } from '../../shared/api/client'
import { resetPassword } from '../api'

/**
 * 비밀번호 설정 — 재설정(AU-05)과 최초 설정(Q-33·34)이 **같은 화면**이다.
 *
 * 링크의 `purpose`가 수명을 가른다: `RESET` 30분 · `INITIAL_SETUP` 7일.
 * 승인 통보 메일의 "비밀번호 설정" 링크도 이 화면으로 온다 — 가입 승인 시 계정은
 * 비밀번호 없이 만들어지고(Q-33), 본인이 여기서 정한다.
 *
 * 만료·사용된 토큰은 `RESET_TOKEN_NOT_ACTIVE`(409) — 다시 요청하라고 안내한다.
 */
export function PasswordSetupPage() {
  const [params] = useSearchParams()
  const initialSetup = params.get('purpose') === 'INITIAL_SETUP'
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [errorCode, setErrorCode] = useState<string>()
  const [loading, setLoading] = useState(false)
  const [done, setDone] = useState(false)

  const mismatch = confirm.length > 0 && password !== confirm
  const tooShort = password.length > 0 && password.length < 8

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    if (mismatch || tooShort) return
    setErrorCode(undefined)
    setLoading(true)
    try {
      await resetPassword(params.get('token'), password)
      setDone(true)
    } catch (error) {
      setErrorCode(error instanceof ApiError ? error.code : 'INTERNAL_ERROR')
    } finally {
      setLoading(false)
    }
  }

  if (done) {
    return (
      <Box className="center-page">
        <Card size="4" style={{ maxWidth: 380, width: '100%' }} className="enter">
          <Flex direction="column" align="center" gap="3" py="5">
            <CheckCircledIcon width="36" height="36" color="var(--green-9)" />
            <Heading size="4">비밀번호가 설정되었습니다</Heading>
            <Text size="2" color="gray" align="center">
              새 비밀번호로 로그인해 주세요.
            </Text>
            <Button mt="2" size="3" onClick={() => (window.location.href = '/login')}>
              로그인하러 가기
            </Button>
          </Flex>
        </Card>
      </Box>
    )
  }

  return (
    <Box className="center-page">
      <Box width="100%" style={{ maxWidth: 380 }} className="enter">
        <Card size="4">
          <form onSubmit={handleSubmit}>
            <Flex direction="column" gap="4">
              <Box>
                <Heading size="4">{initialSetup ? '비밀번호 설정' : '비밀번호 재설정'}</Heading>
                <Text as="p" size="2" color="gray" mt="1">
                  {initialSetup
                    ? '가입이 승인되었습니다. 사용하실 비밀번호를 정해 주세요.'
                    : '새로 사용하실 비밀번호를 입력해 주세요.'}
                </Text>
              </Box>

              {errorCode && <ErrorCallout code={errorCode} />}

              <Flex direction="column" gap="1">
                <Text as="label" htmlFor="new-password" size="2" weight="medium">
                  새 비밀번호
                </Text>
                <TextField.Root
                  id="new-password"
                  type="password"
                  size="3"
                  autoComplete="new-password"
                  color={tooShort ? 'red' : undefined}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
                <Text size="1" color={tooShort ? 'red' : 'gray'}>
                  8자 이상 입력해 주세요.
                </Text>
              </Flex>

              <Flex direction="column" gap="1">
                <Text as="label" htmlFor="confirm-password" size="2" weight="medium">
                  비밀번호 확인
                </Text>
                <TextField.Root
                  id="confirm-password"
                  type="password"
                  size="3"
                  autoComplete="new-password"
                  color={mismatch ? 'red' : undefined}
                  value={confirm}
                  onChange={(e) => setConfirm(e.target.value)}
                  required
                />
                {mismatch && (
                  <Text size="1" color="red">
                    비밀번호가 일치하지 않습니다.
                  </Text>
                )}
              </Flex>

              <Button
                type="submit"
                size="3"
                loading={loading}
                disabled={!password || !confirm || mismatch || tooShort}
              >
                설정 완료
              </Button>
            </Flex>
          </form>
        </Card>

        {initialSetup && (
          <Callout.Root color="gray" mt="4" size="1">
            <Callout.Text>이 링크는 발송 후 7일간 유효합니다.</Callout.Text>
          </Callout.Root>
        )}
      </Box>
    </Box>
  )
}
