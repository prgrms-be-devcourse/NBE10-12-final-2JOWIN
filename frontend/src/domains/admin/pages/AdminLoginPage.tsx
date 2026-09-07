import { useState, type FormEvent } from 'react'
import { Badge, Box, Button, Card, Checkbox, Flex, Heading, Link, Text, TextField, Theme } from '@radix-ui/themes'
import { Link as RouterLink, useNavigate } from 'react-router'
import { Logo } from '../../../shared/brand'
import { ErrorCallout } from '../../../shared/ui'
import { codeOf } from '../../../shared/api/client'
import { adminLogin } from '../api'
import { saveAdminProfile } from '../session'

/**
 * 플랫폼 관리자 로그인 — 구성원 로그인과 별도 입구 (AU-08 · 07 §A `POST /admin/api/v1/auth/login`).
 *
 * 요청·응답 형태는 구성원과 같은 LoginRequest/LoginResponse지만 세션은 완전히 따로다 —
 * refresh 쿠키가 `2jo_admin_rt`(Path /admin/api/v1/auth)라 한 브라우저에서 두 세션이 공존한다 (Q-28).
 * 실패는 LOGIN_FAILED · 5회 연속 실패 LOGIN_LOCKED (Q-30 — login_attempt actor_type으로 구성원과 구분).
 */
export function AdminLoginPage() {
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
      const result = await adminLogin({ email, password, rememberMe })
      // 관리자에게는 /me가 없어 표시용 이름을 여기서 보관한다 — 토큰은 메모리에만
      saveAdminProfile({ memberId: result.memberId, name: result.name })
      navigate('/admin', { replace: true })
    } catch (error) {
      setErrorCode(codeOf(error))
    } finally {
      setLoading(false)
    }
  }

  return (
    // 관리자 앱의 accent(indigo) — 레이아웃과 같은 색으로 "다른 입구"임을 로그인부터 보인다
    <Theme accentColor="indigo" asChild>
    <Box className="center-page">
      <Box width="100%" style={{ maxWidth: 380 }} className="enter">
        <Flex direction="column" align="center" gap="2" mb="5">
          <Logo height={32} />
          <Badge color="gray" variant="soft" radius="full" size="2">
            플랫폼 관리자
          </Badge>
          <Text size="2" color="gray" align="center">
            가입 신청 심사와 회사 관리만 할 수 있습니다.
          </Text>
        </Flex>

        <Card size="4">
          <form onSubmit={handleSubmit}>
            <Flex direction="column" gap="4">
              <Heading size="4">관리자 로그인</Heading>

              {errorCode && <ErrorCallout code={errorCode} />}

              <Flex direction="column" gap="1">
                <Text as="label" htmlFor="admin-email" size="2" weight="medium">
                  이메일
                </Text>
                <TextField.Root
                  id="admin-email"
                  type="email"
                  size="3"
                  autoComplete="username"
                  placeholder="admin@2jo.io"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  required
                />
              </Flex>

              <Flex direction="column" gap="1">
                <Text as="label" htmlFor="admin-password" size="2" weight="medium">
                  비밀번호
                </Text>
                <TextField.Root
                  id="admin-password"
                  type="password"
                  size="3"
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
              </Flex>

              <Text as="label" size="2" color="gray">
                <Flex align="center" gap="2">
                  <Checkbox checked={rememberMe} onCheckedChange={(checked) => setRememberMe(checked === true)} />
                  로그인 상태 유지
                </Flex>
              </Text>

              <Button type="submit" size="3" loading={loading} mt="1">
                로그인
              </Button>
            </Flex>
          </form>
        </Card>

        <Flex justify="center" mt="4">
          <Link asChild size="2" color="gray">
            <RouterLink to="/login">구성원 로그인으로</RouterLink>
          </Link>
        </Flex>
      </Box>
    </Box>
    </Theme>
  )
}
