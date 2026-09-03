import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from 'react-router'
import {
  Avatar, Badge, Box, Button, Callout, Card, Flex, Heading, Separator, Table, Text,
} from '@radix-ui/themes'
import { CheckCircledIcon, ClockIcon, EnvelopeClosedIcon, MobileIcon } from '@radix-ui/react-icons'
import { Money } from '../../../shared/ui'
import { dateLong, dateShort, daysUntil } from '../../../shared/lib/format'
import { messageOf } from '../../../shared/api/errors'
import type { ApproveQuoteRequest, CreateInquiryRequest, RejectQuoteRequest } from '../../../shared/api/types'
import { ApiError } from '../../../shared/api/client'
import { approveQuote, createInquiry, fetchPublicQuote, rejectQuote } from '../api'
import { ApproveDialog, InquiryDialog, RejectDialog } from '../components/RespondDialogs'

/**
 * 고객 열람 페이지 — 제품의 얼굴 (10-screen-design.md §5.6 · 커트라인 8번).
 *
 * 고객사 담당자는 계정이 없고, 이 화면을 평생 한 번만 볼 수도 있다.
 * 그래서 웹앱이 아니라 받은 문서처럼 만든다 — 상단 내비도 탭도 없다(§1).
 *
 * 화면이 지는 책임:
 *  - 발신 회사를 최상단에 크게 — 누가 보냈는지 0.5초 안에 알아야 한다 (GAP-05)
 *  - 담당자는 Deal의 현재 담당자다 — 발송자 스냅샷이 아니다 (AP-18)
 *  - 금액 3분리 — 공급가액·부가세·합계 (QT-25)
 *  - 응답 완료·정지 회사면 버튼을 막고 이유를 말한다 (AP-11, SC-10)
 *  - 만료 링크는 410 — 재발송을 요청하라고 안내한다 (AP-05)
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
  })

  if (isPending) return <QuoteSkeleton />

  if (error) {
    const code = error instanceof ApiError ? error.code : 'INTERNAL_ERROR'
    return (
      <Notice
        tone={code === 'LINK_EXPIRED' ? 'amber' : 'gray'}
        title={code === 'LINK_EXPIRED' ? '만료된 링크입니다' : '견적을 찾을 수 없습니다'}
        description={messageOf(code)}
      />
    )
  }

  const vatExcluded = quote.vatMode === 'EXCLUDED'
  const remaining = daysUntil(`${quote.validUntil}T00:00:00Z`)

  return (
    <>
      {done && <ResponseNotice kind={done} />}

      <Card size="4" className="enter-slow">
        {/* 발신 회사 — 고객이 가장 먼저 봐야 하는 것 (GAP-05) */}
        <Flex direction="column" align="center" gap="2" py="4">
          <Avatar size="4" radius="full" color="blue" fallback={quote.companyName.slice(0, 1)} />
          <Heading size="5">{quote.companyName}</Heading>
          <Text size="2" color="gray">
            사업자등록번호 {quote.companyBusinessNo}
          </Text>
        </Flex>

        <Separator size="4" my="4" />

        <Flex justify="between" align="start" wrap="wrap" gap="3" mb="4">
          <Box>
            <Heading size="6">견적서</Heading>
            <Text as="div" size="2" color="gray" mt="1">
              유효기간 {dateShort(`${quote.validUntil}T00:00:00Z`)}
            </Text>
          </Box>
          <Flex direction="column" align="end" gap="2">
            <Text size="3" weight="medium" style={{ fontVariantNumeric: 'tabular-nums' }}>
              {quote.quoteNo}
            </Text>
            {quote.respondable && (
              <Badge color={remaining < 0 ? 'red' : 'amber'} variant="soft" radius="full">
                <ClockIcon />
                {remaining > 0 ? `${remaining}일 남음` : remaining === 0 ? '오늘 마감' : `${-remaining}일 지남`}
              </Badge>
            )}
          </Flex>
        </Flex>

        <Box style={{ overflowX: 'auto' }}>
          <Table.Root variant="surface" size="2">
            <Table.Header>
              <Table.Row>
                <Table.ColumnHeaderCell>품목</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell>단위</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell align="right">수량</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell align="right">단가</Table.ColumnHeaderCell>
                <Table.ColumnHeaderCell align="right">금액</Table.ColumnHeaderCell>
              </Table.Row>
            </Table.Header>
            <Table.Body>
              {quote.items.map((item, index) => (
                <Table.Row key={`${item.name}-${index}`}>
                  <Table.RowHeaderCell>{item.name}</Table.RowHeaderCell>
                  <Table.Cell>
                    <Text color="gray">{item.unit}</Text>
                  </Table.Cell>
                  <Table.Cell align="right">{item.quantity}</Table.Cell>
                  <Table.Cell align="right">
                    <Money value={item.unitPrice} />
                  </Table.Cell>
                  <Table.Cell align="right">
                    <Money value={item.amount} />
                  </Table.Cell>
                </Table.Row>
              ))}
            </Table.Body>
          </Table.Root>
        </Box>

        {/* 금액 3분리 — 합계만 Heading 크기로 (QT-25) */}
        <Flex direction="column" align="end" gap="1" mt="4">
          <AmountRow label="공급가액" value={quote.supplyAmount} />
          <AmountRow label={`부가세${vatExcluded ? ' (별도)' : ''}`} value={quote.vatAmount} />
          <Flex align="baseline" gap="4" mt="1">
            <Text size="2" color="gray">
              합계
            </Text>
            <Money value={quote.totalAmount} unit size="6" weight="bold" />
          </Flex>
        </Flex>

        {quote.terms && (
          <Text as="p" size="2" color="gray" mt="4">
            {quote.terms}
          </Text>
        )}

        {/* 담당자 — Deal의 현재 담당자 (AP-18) */}
        <Card variant="surface" mt="5" style={{ background: 'var(--blue-2)' }}>
          <Text as="div" size="1" color="gray" mb="2">
            문의하실 곳
          </Text>
          <Flex align="center" gap="3" wrap="wrap">
            <Avatar size="2" radius="full" color="blue" fallback={quote.assignee.name.slice(0, 1)} />
            <Text size="2" weight="medium">
              {quote.assignee.name}
            </Text>
            <Flex align="center" gap="1">
              <MobileIcon color="var(--gray-9)" />
              <Text size="2" color="gray">
                {quote.assignee.phone}
              </Text>
            </Flex>
            <Flex align="center" gap="1">
              <EnvelopeClosedIcon color="var(--gray-9)" />
              <Text size="2" color="gray">
                {quote.assignee.email}
              </Text>
            </Flex>
          </Flex>
        </Card>

        {!quote.respondable && !done && (
          <Callout.Root color="gray" mt="4">
            <Callout.Text>
              이미 응답이 완료된 견적입니다. 내용은 계속 확인하실 수 있습니다.
            </Callout.Text>
          </Callout.Root>
        )}

        {/* 승인이 solid·가장 넓게, 나머지는 surface. 모두 size 3(40px) */}
        <Flex gap="2" mt="5" wrap="wrap" className="no-print">
          <Button size="3" variant="surface" color="gray" onClick={() => setDialog('inquiry')}>
            문의 남기기
          </Button>
          <Button
            size="3"
            variant="surface"
            color="red"
            disabled={!quote.respondable}
            onClick={() => setDialog('reject')}
          >
            반려
          </Button>
          <Button
            size="3"
            style={{ flexGrow: 1 }}
            disabled={!quote.respondable}
            onClick={() => setDialog('approve')}
          >
            승인하기
          </Button>
        </Flex>

        <Text as="p" align="center" size="1" color="gray" mt="4">
          이 링크는 {dateLong(`${quote.validUntil}T00:00:00Z`)}까지 유효합니다.
        </Text>
      </Card>

      <ApproveDialog
        open={dialog === 'approve'}
        onOpenChange={(open) => setDialog(open ? 'approve' : null)}
        companyName={quote.companyName}
        quoteNo={quote.quoteNo}
        totalAmount={quote.totalAmount}
        vatExcluded={vatExcluded}
        loading={mutation.isPending}
        onConfirm={(responder) =>
          mutation.mutate({
            kind: 'approve',
            body: { responderName: responder.name, responderTitle: responder.title || undefined },
          })
        }
      />

      <RejectDialog
        open={dialog === 'reject'}
        onOpenChange={(open) => setDialog(open ? 'reject' : null)}
        loading={mutation.isPending}
        onConfirm={(responder, reason) =>
          mutation.mutate({
            kind: 'reject',
            body: {
              reason,
              responderName: responder.name,
              responderTitle: responder.title || undefined,
            },
          })
        }
      />

      <InquiryDialog
        open={dialog === 'inquiry'}
        onOpenChange={(open) => setDialog(open ? 'inquiry' : null)}
        loading={mutation.isPending}
        onSubmit={(content) => mutation.mutate({ kind: 'inquiry', body: { content } })}
      />
    </>
  )
}

function AmountRow({ label, value }: { label: string; value: number }) {
  return (
    <Flex align="baseline" gap="4">
      <Text size="2" color="gray">
        {label}
      </Text>
      <Money value={value} size="3" />
    </Flex>
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
            width: 44,
            height: 44,
            borderRadius: '50%',
            display: 'grid',
            placeItems: 'center',
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
function QuoteSkeleton() {
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
