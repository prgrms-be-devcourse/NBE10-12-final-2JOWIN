import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router'
import { Badge, Box, Button, Card, Flex, Heading, Separator, Text, TextField } from '@radix-ui/themes'
import { CheckCircledIcon, EnvelopeClosedIcon } from '@radix-ui/react-icons'
import { ErrorCallout } from '../../../shared/ui'
import { messageOf } from '../../../shared/api/errors'
import { ApiError } from '../../../shared/api/client'
import { acceptInvitation, fetchInvitation } from '../api'

/**
 * 초대 수락 — 계정 만들기 (MB-03·04).
 *
 * 초대받은 사람은 아직 구성원이 아니므로 로그인 화면을 거치지 않는다.
 * 화면이 답해야 하는 것 셋: 누가 불렀는가 · 무슨 역할인가 · 언제까지 유효한가.
 *
 * 만료·취소된 초대는 `INVITATION_NOT_PENDING`(409) —
 * "관리자에게 재발송을 요청하세요"가 부록 문구에 이미 들어 있다.
 */

export function InviteAcceptPage() {
  const { token = '' } = useParams()
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [loading, setLoading] = useState(false)
  const [errorCode, setErrorCode] = useState<string>()
  const [done, setDone] = useState(false)

  const { data: invitation, isPending, error } = useQuery({
    queryKey: ['invitation', token],
    queryFn: () => fetchInvitation(token),
    retry: false,
  })

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setLoading(true)
    setErrorCode(undefined)
    try {
      await acceptInvitation(token, name, password)
      setDone(true)
    } catch (error) {
      setErrorCode(error instanceof ApiError ? error.code : 'INTERNAL_ERROR')
    } finally {
      setLoading(false)
    }
  }

  if (isPending) {
    return (
      <Box className="center-page">
        <Card size="4" style={{ maxWidth: 380, width: '100%' }} className="enter-fade">
          <Box height="120px" />
        </Card>
      </Box>
    )
  }

  if (error) {
    return (
      <Box className="center-page">
        <Card size="4" style={{ maxWidth: 380, width: '100%' }} className="enter">
          <Flex direction="column" align="center" gap="3" py="6">
            <Heading size="4" align="center">
              초대가 만료되었습니다
            </Heading>
            <Text size="2" color="gray" align="center">
              {messageOf(error instanceof ApiError ? error.code : undefined)}
            </Text>
          </Flex>
        </Card>
      </Box>
    )
  }

  if (done) {
    return (
      <Box className="center-page">
        <Card size="4" style={{ maxWidth: 380, width: '100%' }} className="enter">
          <Flex direction="column" align="center" gap="3" py="5">
            <CheckCircledIcon width="36" height="36" color="var(--green-9)" />
            <Heading size="4">가입이 완료되었습니다</Heading>
            <Text size="2" color="gray" align="center">
              {invitation.companyName}의 구성원으로 등록되었습니다.
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
          {/* 누가 불렀는가 — 초대 메일의 맥락을 화면에서 다시 확인시킨다 */}
          <Flex direction="column" gap="2" align="center" py="2">
            <EnvelopeClosedIcon width="22" height="22" color="var(--blue-9)" />
            <Heading size="4" align="center">
              {invitation.companyName}에서 초대했습니다
            </Heading>
            <Flex align="center" gap="2">
              <Badge color="blue" variant="soft" radius="full">
                {invitation.role === 'COMPANY_ADMIN' ? '기업 관리자' : '영업 담당자'}
              </Badge>
              <Text size="2" color="gray">
                {invitation.email}
              </Text>
            </Flex>
          </Flex>

          <Separator size="4" my="4" />

          <form onSubmit={handleSubmit}>
            <Flex direction="column" gap="4">
              {errorCode && <ErrorCallout code={errorCode} />}

              <Flex direction="column" gap="1">
                <Text as="label" htmlFor="invite-name" size="2" weight="medium">
                  이름
                </Text>
                <TextField.Root
                  id="invite-name"
                  size="3"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                />
              </Flex>

              <Flex direction="column" gap="1">
                <Text as="label" htmlFor="invite-password" size="2" weight="medium">
                  비밀번호
                </Text>
                <TextField.Root
                  id="invite-password"
                  type="password"
                  size="3"
                  autoComplete="new-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                />
                <Text size="1" color="gray">
                  8자 이상 입력해 주세요.
                </Text>
              </Flex>

              <Button
                type="submit"
                size="3"
                loading={loading}
                disabled={!name.trim() || password.length < 8}
              >
                가입 완료
              </Button>
            </Flex>
          </form>
        </Card>

        <Text as="p" align="center" size="1" color="gray" mt="4">
          이 초대는 발송 후 7일간 유효합니다.
        </Text>
      </Box>
    </Box>
  )
}
