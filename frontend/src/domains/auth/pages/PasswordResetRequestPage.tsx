import { useState, type FormEvent } from 'react'
import { Box, Button, Card, Flex, Heading, Link, Text, TextField } from '@radix-ui/themes'
import { EnvelopeClosedIcon } from '@radix-ui/react-icons'
import { Link as RouterLink } from 'react-router'
import { ErrorCallout } from '../../../shared/ui'
import { codeOf } from '../../../shared/api/client'
import { requestPasswordReset } from '../api'

/**
 * 비밀번호 재설정 요청 (AU-05 · 07 §A `POST /public/api/v1/auth/password-reset-request`).
 *
 * 미가입 이메일도 같은 202를 받는다 (SC-09 인증 확장) — 그래서 완료 문구도 "가입된 이메일이면"이다.
 * 가입 여부를 화면이 말해 주면 안 된다. 안내 메일(NT-14)의 링크는 `/password-reset?token=`으로 온다 (30분 유효).
 */
export function PasswordResetRequestPage() {
  const [email, setEmail] = useState('')
  const [loading, setLoading] = useState(false)
  const [errorCode, setErrorCode] = useState<string>()
  const [done, setDone] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErrorCode(undefined)
    setLoading(true)
    try {
      await requestPasswordReset({ email: email.trim() })
      setDone(true)
    } catch (error) {
      setErrorCode(codeOf(error))
    } finally {
      setLoading(false)
    }
  }

  if (done) {
    return (
      <Box className="center-page">
        <Card size="4" style={{ maxWidth: 380, width: '100%' }} className="enter">
          <Flex direction="column" align="center" gap="3" py="5">
            <EnvelopeClosedIcon width="32" height="32" color="var(--blue-9)" />
            <Heading size="4" align="center">
              메일을 확인해 주세요
            </Heading>
            <Text size="2" color="gray" align="center">
              가입된 이메일이면 재설정 링크를 보냈습니다. 링크는 30분간 유효합니다.
            </Text>
            <Button mt="2" size="3" variant="soft" asChild>
              <RouterLink to="/login">로그인으로</RouterLink>
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
                <Heading size="4">비밀번호 재설정</Heading>
                <Text as="p" size="2" color="gray" mt="1">
                  가입한 이메일을 입력하면 재설정 링크를 보내 드립니다.
                </Text>
              </Box>

              {errorCode && <ErrorCallout code={errorCode} />}

              <Flex direction="column" gap="1">
                <Text as="label" htmlFor="reset-email" size="2" weight="medium">
                  이메일
                </Text>
                <TextField.Root
                  id="reset-email"
                  type="email"
                  size="3"
                  autoComplete="username"
                  placeholder="name@company.co.kr"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                  autoFocus
                />
              </Flex>

              <Button type="submit" size="3" loading={loading} disabled={!email.trim()}>
                재설정 링크 보내기
              </Button>
            </Flex>
          </form>
        </Card>

        <Flex justify="center" mt="4">
          <Link asChild size="2" color="gray">
            <RouterLink to="/login">로그인으로 돌아가기</RouterLink>
          </Link>
        </Flex>
      </Box>
    </Box>
  )
}
