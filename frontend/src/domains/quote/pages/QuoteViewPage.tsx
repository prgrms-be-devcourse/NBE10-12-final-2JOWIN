import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from 'react-router'
import { Box, Button, Callout, Card, Flex, Heading, Separator, Text } from '@radix-ui/themes'
import { CheckCircledIcon, ClockIcon } from '@radix-ui/react-icons'
import { dateLong } from '../../../shared/lib/format'
import { messageOf } from '../../../shared/api/errors'
import type { ApproveQuoteRequest, CreateInquiryRequest, RejectQuoteRequest } from '../../../shared/api/types'
import { codeOf } from '../../../shared/api/client'
import { approveQuote, createInquiry, fetchPublicQuote, rejectQuote } from '../api'
import { ApproveDialog, InquiryDialog, RejectDialog } from '../components/RespondDialogs'
import { QuoteDocument } from '../components/QuoteDocument'

/**
 * 고객 열람 페이지 — 제품의 얼굴 (10-screen-design.md §5.6 · 커트라인 8번).
 *
 * 고객사 담당자는 계정이 없고, 이 화면을 평생 한 번만 볼 수도 있다.
 * 그래서 웹앱이 아니라 받은 문서처럼 만든다 — 상단 내비도 탭도 없다(§1).
 * 문서 자체는 `QuoteDocument` — 구성원 미리보기(QT-12)와 같은 컴포넌트다.
 *
 * 화면이 지는 책임:
 *  - 응답 완료·정지 회사면 버튼을 막고 이유를 말한다 (AP-11, SC-10). 문의는 응답 완료 뒤에도 되지만
 *    정지 회사는 서버가 409 COMPANY_SUSPENDED라 같이 막는다 (07 §D) — 응답에 사유 필드가 없어
 *    "발송됨·열람됨인데 respondable=false"를 정지로 읽는다 (조립기 assembleForView와 같은 축)
 *  - 만료 링크는 410 — 재발송을 요청하라고 안내한다 (AP-05)
 *  - 실패는 모달 안에 보여주고 닫지 않는다 — 입력한 사유를 잃지 않게. 링크·견적 상태가 바뀐 실패(409·410)면 다시 조회한다
 */

type Action =
  | { kind: 'approve'; body: ApproveQuoteRequest }
  | { kind: 'reject'; body: RejectQuoteRequest }
  | { kind: 'inquiry'; body: CreateInquiryRequest }

function send(token: string, action: Action) {
  switch (action.kind) {
    case 'approve':
      return approveQuote(token, action.body)
    case 'reject':
      return rejectQuote(token, action.body)
    case 'inquiry':
      return createInquiry(token, action.body)
  }
}

export function QuoteViewPage() {
  const { token = '' } = useParams()
  const queryClient = useQueryClient()
  const [dialog, setDialog] = useState<'approve' | 'reject' | 'inquiry' | null>(null)
  const [done, setDone] = useState<'approved' | 'rejected' | 'inquired' | null>(null)

  const { data: quote, isPending, error } = useQuery({
    queryKey: ['quote', 'public', token],
    queryFn: () => fetchPublicQuote(token),
    retry: false,
  })

  const mutation = useMutation({
    mutationFn: (action: Action) => send(token, action),
    onSuccess: (_result, action) => {
      setDialog(null)
      setDone(action.kind === 'approve' ? 'approved' : action.kind === 'reject' ? 'rejected' : 'inquired')
      queryClient.invalidateQueries({ queryKey: ['quote', 'public', token] })
    },
    // 다른 탭에서 먼저 응답했거나 링크가 닫힌 경우 — 화면의 버튼 상태도 서버 기준으로 다시 맞춘다
    onError: (e) => {
      if (codeOf(e) !== 'VALIDATION_FAILED') queryClient.invalidateQueries({ queryKey: ['quote', 'public', token] })
    },
  })
  const openDialog = (kind: NonNullable<typeof dialog>) => (open: boolean) => {
    setDialog(open ? kind : null)
    if (!open) mutation.reset()
  }

  if (isPending) return <QuoteSkeleton />

  if (error) {
    const code = codeOf(error)
    return (
      <Notice
        tone={code === 'LINK_EXPIRED' ? 'amber' : 'gray'}
        title={code === 'LINK_EXPIRED' ? '만료된 링크입니다' : '견적을 찾을 수 없습니다'}
        description={messageOf(code)}
      />
    )
  }

  const vatExcluded = quote.vatMode === 'EXCLUDED'
  // 발송됨·열람됨인데 응답 불가 = 발송 회사 정지 (SC-10, Q-27). 그 밖의 respondable=false는 응답 완료다
  const suspended = !quote.respondable && (quote.status === 'SENT' || quote.status === 'VIEWED')

  return (
    <>
      {done && <ResponseNotice kind={done} />}

      <QuoteDocument quote={quote} showRemaining={quote.respondable}>
        {!quote.respondable && !done && (
          <Callout.Root color="gray" mt="4">
            <Callout.Text>
              {suspended ? messageOf('COMPANY_SUSPENDED') : '이미 응답이 완료된 견적입니다. 내용은 계속 확인하실 수 있습니다.'}
            </Callout.Text>
          </Callout.Root>
        )}

        {/* 승인이 solid·가장 넓게, 나머지는 surface. 모두 size 3(40px) */}
        <Flex gap="2" mt="5" wrap="wrap" className="no-print">
          <Button size="3" variant="surface" color="gray" disabled={suspended} onClick={() => setDialog('inquiry')}>
            문의 남기기
          </Button>
          <Button size="3" variant="surface" color="red" disabled={!quote.respondable} onClick={() => setDialog('reject')}>
            반려
          </Button>
          <Button size="3" style={{ flexGrow: 1 }} disabled={!quote.respondable} onClick={() => setDialog('approve')}>
            승인하기
          </Button>
        </Flex>

        <Text as="p" align="center" size="1" color="gray" mt="4">
          이 링크는 {dateLong(`${quote.validUntil}T00:00:00Z`)}까지 유효합니다.
        </Text>
      </QuoteDocument>

      <ApproveDialog
        open={dialog === 'approve'}
        onOpenChange={openDialog('approve')}
        companyName={quote.companyName}
        quoteNo={quote.quoteNo}
        totalAmount={quote.totalAmount}
        vatExcluded={vatExcluded}
        loading={mutation.isPending}
        error={mutation.error}
        onConfirm={(responder) =>
          mutation.mutate({ kind: 'approve', body: { responderName: responder.name, responderTitle: responder.title || undefined } })
        }
      />

      <RejectDialog
        open={dialog === 'reject'}
        onOpenChange={openDialog('reject')}
        loading={mutation.isPending}
        error={mutation.error}
        onConfirm={(responder, reason) =>
          mutation.mutate({ kind: 'reject', body: { reason, responderName: responder.name, responderTitle: responder.title || undefined } })
        }
      />

      <InquiryDialog
        open={dialog === 'inquiry'}
        onOpenChange={openDialog('inquiry')}
        loading={mutation.isPending}
        error={mutation.error}
        onSubmit={(content) => mutation.mutate({ kind: 'inquiry', body: { content } })}
      />
    </>
  )
}

/** 응답 직후 안내 — 새 화면으로 보내지 않는다. 방금 승인한 문서를 그대로 두고 위에 붙인다 */
function ResponseNotice({ kind }: { kind: 'approved' | 'rejected' | 'inquired' }) {
  const text = {
    approved: '승인이 접수되었습니다. 담당자에게 전달되었습니다.',
    rejected: '반려가 접수되었습니다. 사유가 담당자에게 전달되었습니다.',
    inquired: '문의가 접수되었습니다. 담당자가 이메일로 회신드립니다.',
  }[kind]

  return (
    <Callout.Root color={kind === 'rejected' ? 'gray' : 'green'} mb="4" className="enter no-print">
      <Callout.Icon>
        <CheckCircledIcon />
      </Callout.Icon>
      <Callout.Text>{text}</Callout.Text>
    </Callout.Root>
  )
}

/** 만료·부재 안내 — 고객에게는 "왜 안 되는지"와 "무엇을 하면 되는지"만 말한다 */
function Notice({ tone, title, description }: { tone: 'amber' | 'gray'; title: string; description: string }) {
  return (
    <Card size="4" className="enter">
      <Flex direction="column" align="center" gap="3" py="7" px="4">
        <Box
          style={{
            width: 44, height: 44, borderRadius: '50%', display: 'grid', placeItems: 'center',
            background: tone === 'amber' ? 'var(--amber-3)' : 'var(--gray-3)',
          }}
        >
          <ClockIcon width="20" height="20" color={tone === 'amber' ? 'var(--amber-11)' : 'var(--gray-11)'} />
        </Box>
        <Heading size="4" align="center">
          {title}
        </Heading>
        <Text size="2" color="gray" align="center">
          {description}
        </Text>
      </Flex>
    </Card>
  )
}

/** 로딩 — 문서 모양을 미리 잡아둔다. 화면이 튀지 않게 */
export function QuoteSkeleton() {
  return (
    <Card size="4" className="enter-fade">
      <Flex direction="column" gap="3" py="4">
        <Box mx="auto" width="120px" height="20px" style={skeletonStyle} />
        <Box mx="auto" width="180px" height="14px" style={skeletonStyle} />
        <Separator size="4" my="3" />
        {[0, 1, 2, 3].map((i) => (
          <Box key={i} width="100%" height="16px" style={skeletonStyle} />
        ))}
      </Flex>
    </Card>
  )
}

const skeletonStyle = {
  background: 'var(--gray-3)',
  borderRadius: 'var(--radius-2)',
}
