import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { Box, Button, Callout, Card, Flex, Grid, Skeleton, Text, TextArea } from '@radix-ui/themes'
import { CheckIcon, Cross2Icon, InfoCircledIcon } from '@radix-ui/react-icons'
import { ApplicationStatusBadge, BackLink, ConfirmDialog, ErrorCallout, NotFound, PageHeader } from '../../../shared/ui'
import { ApiError, codeOf } from '../../../shared/api/client'
import { dateTime } from '../../../shared/lib/format'
import { useApplication, useApplicationDecision } from '../hooks'

/**
 * 가입 신청 상세 — 승인·반려 (ON-04·05·06·07·14 · 전이표 §1).
 *
 * 승인: 회사 생성 + 기업 관리자 계정(비밀번호 미설정) + 설정 링크 메일 (NT-13, Q-33). 되돌릴 수 없으므로 AlertDialog (10 §2.5).
 * 반려: 사유 필수(ON-14), 이력 보존·재신청 가능 (Q-15). 반려 통보 메일 (NT-13).
 * 사업자번호가 이미 가입돼 있으면 409 COMPANY_BUSINESS_NO_DUPLICATED — 모달 안에서 반려로 유도한다 (07 §A).
 */
export function AdminApplicationDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { data: application, isPending, error, refetch } = useApplication(id)
  const { approve, reject } = useApplicationDecision(id)
  const [approving, setApproving] = useState(false)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')

  if (isPending) return <DetailSkeleton />

  if (error || !application) {
    return (
      <>
        <BackLink to="/admin/applications" label="가입 신청" />
        <NotFound code={codeOf(error)} backLabel="신청 목록으로" onBack={() => navigate('/admin/applications')} onRetry={() => refetch()} />
      </>
    )
  }

  const pending = application.status === 'PENDING'
  const approveError = approve.error instanceof ApiError ? approve.error : null
  const rejectError = reject.error instanceof ApiError ? reject.error : null

  return (
    <Box className="enter-fade">
      <BackLink to="/admin/applications" label="가입 신청" />
      <PageHeader
        title={application.companyName}
        badge={<ApplicationStatusBadge status={application.status} />}
        description={<>신청 {dateTime(application.createdAt)}{application.decidedAt && ` · 처리 ${dateTime(application.decidedAt)}`}</>}
        actions={
          pending && (
            <>
              <Button variant="soft" color="gray" onClick={() => setRejecting(true)}>
                <Cross2Icon /> 반려
              </Button>
              <Button onClick={() => setApproving(true)}>
                <CheckIcon /> 승인
              </Button>
            </>
          )
        }
      />

      <Grid columns={{ initial: '1', sm: '3' }} gap="3" mb="5">
        <InfoCard label="회사명" value={application.companyName} />
        <InfoCard label="사업자등록번호" value={application.businessNo} mono />
        <InfoCard label="이메일 (기업 관리자 계정)" value={application.email} />
      </Grid>

      {application.status === 'REJECTED' && (
        <Callout.Root color="gray" mb="4">
          <Callout.Icon>
            <InfoCircledIcon />
          </Callout.Icon>
          <Callout.Text>
            반려 사유: {application.rejectReason ?? '—'}
          </Callout.Text>
        </Callout.Root>
      )}

      {application.status === 'APPROVED' && (
        <Callout.Root color="green" mb="4">
          <Callout.Icon>
            <CheckIcon />
          </Callout.Icon>
          <Callout.Text>승인된 신청입니다. 회사가 생성되었고 비밀번호 설정 링크가 발송되었습니다.</Callout.Text>
        </Callout.Root>
      )}

      {/* ── 승인 (AlertDialog — 되돌릴 수 없다) */}
      <ConfirmDialog
        open={approving}
        onOpenChange={(open) => {
          setApproving(open)
          if (!open) approve.reset()
        }}
        title={`${application.companyName}의 사용 신청을 승인하시겠습니까?`}
        description="회사가 생성되고, 신청 이메일로 기업 관리자 계정의 비밀번호 설정 링크가 발송됩니다 (7일 유효)."
        confirmLabel="승인"
        loading={approve.isPending}
        onConfirm={() => approve.mutate(undefined, { onSuccess: () => setApproving(false) })}
      >
        {approveError && (
          <Box mt="3">
            <ErrorCallout code={approveError.code} />
            {approveError.code === 'COMPANY_BUSINESS_NO_DUPLICATED' && (
              <Text as="p" size="2" color="gray">
                같은 사업자등록번호의 회사가 이미 있습니다. 이 신청은 반려해 주세요.
              </Text>
            )}
          </Box>
        )}
      </ConfirmDialog>

      {/* ── 반려 (AlertDialog + 사유 필수) */}
      <ConfirmDialog
        open={rejecting}
        onOpenChange={(open) => {
          setRejecting(open)
          if (!open) {
            reject.reset()
            setReason('')
          }
        }}
        title={`${application.companyName}의 사용 신청을 반려하시겠습니까?`}
        description="사유는 신청자에게 메일로 통보됩니다. 반려 후에도 다시 신청할 수 있습니다."
        confirmLabel="반려"
        confirmColor="red"
        loading={reject.isPending}
        onConfirm={() => reject.mutate({ reason: reason.trim() }, { onSuccess: () => setRejecting(false) })}
      >
        <Box mt="3">
          <Text as="label" htmlFor="reject-reason" size="2" weight="medium">
            반려 사유 (필수)
          </Text>
          <TextArea
            id="reject-reason"
            mt="1"
            rows={3}
            placeholder="예: 사업자등록번호 확인 불가"
            value={reason}
            disabled={reject.isPending}
            onChange={(e) => setReason(e.target.value)}
            color={rejectError?.reasonOf('reason') ? 'red' : undefined}
          />
          {rejectError?.reasonOf('reason') && (
            <Text as="p" size="1" color="red" mt="1">
              {rejectError.reasonOf('reason')}
            </Text>
          )}
          {!reason.trim() && (
            <Text as="p" size="1" color="gray" mt="1">
              사유를 입력해야 반려할 수 있습니다.
            </Text>
          )}
        </Box>
        {rejectError && rejectError.code !== 'VALIDATION_FAILED' && <ErrorCallout code={rejectError.code} />}
      </ConfirmDialog>
    </Box>
  )
}

function InfoCard({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <Card size="2">
      <Text as="div" size="1" color="gray" mb="1">
        {label}
      </Text>
      <Text as="div" size="3" weight="medium" style={mono ? { fontVariantNumeric: 'tabular-nums' } : undefined}>
        {value}
      </Text>
    </Card>
  )
}

function DetailSkeleton() {
  return (
    <Box>
      <Skeleton height="20px" width="80px" mb="3" />
      <Skeleton height="32px" width="240px" mb="5" />
      <Grid columns={{ initial: '1', sm: '3' }} gap="3">
        <Skeleton height="72px" />
        <Skeleton height="72px" />
        <Skeleton height="72px" />
      </Grid>
      <Flex mt="4">
        <Skeleton height="40px" width="100%" />
      </Flex>
    </Box>
  )
}
