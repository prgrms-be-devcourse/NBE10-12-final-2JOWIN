import { useState, type FormEvent } from 'react'
import { Box, Button, Card, Checkbox, Flex, Heading, Link, Text, TextField } from '@radix-ui/themes'
import { useNavigate } from 'react-router'
import { ErrorCallout } from '../../shared/ui'
import { ApiError } from '../../shared/api/client'
import { login } from '../api'

/**
 * 로그인 — 구성원 앱의 입구 (커트라인 1번 · 07-api-spec.md §A).
 *
 * 화면이 지는 책임:
 *  - 이메일은 서비스 전체에서 유일하므로 **회사 선택 절차가 없다** (Q-14)
 *  - `rememberMe`는 refresh 쿠키 수명을 가른다 — 14일 vs 브라우저 종료 시 소멸 (AU-10, Q-32)
 *  - 실패 응답을 구별해 말하지 않는다 — 미가입·비활성·정지 회사 전부 LOGIN_FAILED (SC-09)
 *  - 5회 연속 실패는 429 LOGIN_LOCKED (AU-06·09) — 부록 문구에 이미 "10분 후"가 들어 있다
 *
 * 플랫폼 관리자는 이 경로가 아니라 /admin으로 들어간다 (AU-08).
 */
export function LoginPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [rememberMe, setRememberMe] = useState(false)
  const [errorCode, setErrorCode] = useState<string>()
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setErrorCode(undefined)
    setLoading(true)
    try {
      // access는 응답 바디, refresh는 Set-Cookie — 클라이언트가 access를 메모리에 담는다 (§6.3-7)
      await login(email, password, rememberMe)
      navigate('/')
    } catch (error) {
      setErrorCode(error instanceof ApiError ? error.code : 'INTERNAL_ERROR')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Box className="center-page">
      <Box width="100%" style={{ maxWidth: 380 }} className="enter">
        <Flex direction="column" align="center" gap="1" mb="5">
          <Text size="6" weight="bold" style={{ letterSpacing: '-0.03em' }}>
            2JO
          </Text>
          <Text size="2" color="gray">
            견적을 보내고, 승인까지 추적합니다
          </Text>
        </Flex>

        <Card size="4">
          <form onSubmit={handleSubmit}>
            <Flex direction="column" gap="4">
              <Heading size="4">로그인</Heading>

              {errorCode && <ErrorCallout code={errorCode} />}

              <Flex direction="column" gap="1">
                <Text as="label" htmlFor="email" size="2" weight="medium">
                  이메일
                </Text>
                <TextField.Root
                  id="email"
                  type="email"
                  size="3"
                  autoComplete="username"
                  placeholder="name@company.co.kr"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </Flex>

              <Flex direction="column" gap="1">
                <Text as="label" htmlFor="password" size="2" weight="medium">
                  비밀번호
                </Text>
                <TextField.Root
                  id="password"
                  type="password"
                  size="3"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </Flex>

              <Flex align="center" justify="between">
                <Text as="label" size="2" color="gray">
                  <Flex align="center" gap="2">
                    <Checkbox
                      checked={rememberMe}
                      onCheckedChange={(checked) => setRememberMe(checked === true)}
                    />
                    로그인 상태 유지
                  </Flex>
                </Text>
                <Link href="/password-reset" size="2">
                  비밀번호 재설정
                </Link>
              </Flex>

              <Button type="submit" size="3" loading={loading} mt="1">
                로그인
              </Button>
            </Flex>
          </form>
        </Card>
      </Box>
    </Box>
  )
}
