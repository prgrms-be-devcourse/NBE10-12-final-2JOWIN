import { useState, type FormEvent } from 'react'
import { Box, Button, Card, Flex, Heading, Link, Text, TextField } from '@radix-ui/themes'
import { CheckCircledIcon } from '@radix-ui/react-icons'
import { Link as RouterLink } from 'react-router'
import { Logo } from '../../../shared/brand'
import { ErrorCallout, Field } from '../../../shared/ui'
import { ApiError } from '../../../shared/api/client'
import { createApplication } from '../api'

/**
 * 회사 사용 신청 (ON-01·02 · 04 S-02 1단계 · 07 §A `POST /public/api/v1/applications`).
 *
 * 방문자(비로그인)가 회사명·사업자번호·신청자 이름·이메일로 신청하면 검토 대기(PENDING)가 된다.
 * 플랫폼 관리자가 승인하면 회사가 생기고, 이 이메일이 기업 관리자 계정이 되어
 * 비밀번호 설정 링크가 메일로 온다 (Q-33 · NT-13). 여기서는 접수만 하고 로그인할 수 없다 (ON-13).
 * 신청자 이름은 승인 시 그 계정의 이름(member.name)이 된다 (08 v1.6.11 · ON-07) — 본인이 AU-07로 고칠 수 있다.
 */
export function ApplyPage() {
  const [form, setForm] = useState({ companyName: '', businessNo: '', applicantName: '', email: '' })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [done, setDone] = useState(false)

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [key]: e.target.value }))

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await createApplication({
        companyName: form.companyName.trim(),
        businessNo: form.businessNo.trim(),
        email: form.email.trim(),
        applicantName: form.applicantName.trim(),
      })
      setDone(true)
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError('INTERNAL_ERROR', undefined))
    } finally {
      setLoading(false)
    }
  }

  if (done) {
    return (
      <Box className="center-page">
        <Card size="4" style={{ maxWidth: 400, width: '100%' }} className="enter">
          <Flex direction="column" align="center" gap="3" py="5">
            <CheckCircledIcon width="36" height="36" color="var(--green-9)" />
            <Heading size="4">신청이 접수되었습니다</Heading>
            <Text size="2" color="gray" align="center">
              검토 후 승인되면 <b>{form.email}</b>으로 비밀번호 설정 링크가 발송됩니다. 그 전에는 로그인할 수 없습니다.
            </Text>
            <Button mt="2" size="3" asChild>
              <RouterLink to="/login">로그인으로</RouterLink>
            </Button>
          </Flex>
        </Card>
      </Box>
    )
  }

  const canSubmit =
    form.companyName.trim() !== '' && form.businessNo.trim() !== '' && form.applicantName.trim() !== '' && form.email.trim() !== ''

  return (
    <Box className="center-page">
      <Box width="100%" style={{ maxWidth: 400 }} className="enter">
        <Flex direction="column" align="center" gap="1" mb="5">
          <Logo height={32} />
          <Text size="2" color="gray" align="center" mt="2">
            회사 단위로 신청하고, 승인 뒤 팀원을 초대합니다.
          </Text>
        </Flex>

        <Card size="4">
          <form onSubmit={handleSubmit}>
            <Flex direction="column" gap="4">
              <Heading size="4">회사 사용 신청</Heading>

              {error && error.code !== 'VALIDATION_FAILED' && <ErrorCallout code={error.code} />}

              <Field label="회사명" required error={error?.reasonOf('companyName')} htmlFor="apply-company">
                <TextField.Root
                  id="apply-company"
                  size="3"
                  placeholder="예: 한빛오피스"
                  value={form.companyName}
                  onChange={set('companyName')}
                  disabled={loading}
                  color={error?.reasonOf('companyName') ? 'red' : undefined}
                  autoFocus
                />
              </Field>

              <Field label="사업자등록번호" required error={error?.reasonOf('businessNo')} hint="승인 시 전역 중복 검사를 합니다" htmlFor="apply-bizno">
                <TextField.Root
                  id="apply-bizno"
                  size="3"
                  placeholder="000-00-00000"
                  value={form.businessNo}
                  onChange={set('businessNo')}
                  disabled={loading}
                  color={error?.reasonOf('businessNo') ? 'red' : undefined}
                />
              </Field>

              <Field label="신청자 이름" required error={error?.reasonOf('applicantName')} hint="승인되면 이 이름으로 기업 관리자 계정이 만들어집니다" htmlFor="apply-name">
                <TextField.Root
                  id="apply-name"
                  size="3"
                  autoComplete="name"
                  placeholder="예: 김서연"
                  maxLength={100}
                  value={form.applicantName}
                  onChange={set('applicantName')}
                  disabled={loading}
                  color={error?.reasonOf('applicantName') ? 'red' : undefined}
                />
              </Field>

              <Field label="이메일" required error={error?.reasonOf('email')} hint="승인되면 이 이메일이 기업 관리자 계정이 됩니다" htmlFor="apply-email">
                <TextField.Root
                  id="apply-email"
                  type="email"
                  size="3"
                  autoComplete="email"
                  placeholder="name@company.co.kr"
                  value={form.email}
                  onChange={set('email')}
                  disabled={loading}
                  color={error?.reasonOf('email') ? 'red' : undefined}
                />
              </Field>

              <Button type="submit" size="3" loading={loading} disabled={!canSubmit} mt="1">
                신청하기
              </Button>
            </Flex>
          </form>
        </Card>

        <Flex justify="center" mt="4">
          <Link asChild size="2" color="gray">
            <RouterLink to="/login">이미 계정이 있으신가요? 로그인</RouterLink>
          </Link>
        </Flex>
      </Box>
    </Box>
  )
}
