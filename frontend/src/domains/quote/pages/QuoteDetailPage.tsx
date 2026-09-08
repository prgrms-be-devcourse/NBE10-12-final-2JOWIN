import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Badge, Box, Button, Callout, Card, DropdownMenu, Flex, Grid, Skeleton, Table, Text } from '@radix-ui/themes'
import { ArrowRightIcon, CopyIcon, DotsHorizontalIcon, EyeOpenIcon, Link2Icon, PaperPlaneIcon, ResetIcon } from '@radix-ui/react-icons'
import { BackLink, ErrorCallout, Money, NotFound, PageHeader, QuoteStatusBadge, RemainingBadge, ViewedBadge } from '../../../shared/ui'
import { DEAL_STAGE_LABEL, VAT_MODE_LABEL } from '../../../shared/ui/status'
import { codeOf } from '../../../shared/api/client'
import { date, dateTime } from '../../../shared/lib/format'
import type { QuoteDetailResponse } from '../../../shared/api/types'
import { useQuoteActions, useQuoteDetail } from '../hooks'
import { QuoteEditor } from '../components/QuoteEditor'
import { ConvertDialog, ExpireLinkDialog, ResendDialog, SendQuoteDialog, WithdrawDialog } from '../components/QuoteActionDialogs'

/**
 * 견적 상세 — 작성 중이면 편집기(10 §5.4), 그 뒤로는 읽기 전용 + 상태별 액션 (전이표 §6·§7).
 *
 * 상태별 액션 (05-state-transitions.md §6 · 07 §C)
 *  - DRAFT: 편집·발송·복제 (삭제 API는 v1에 없다)
 *  - SENT·VIEWED: 회수(QT-17) · 수신인 변경 재발송(AP-13) · 링크 수동 만료(AP-14) · 복제
 *  - APPROVED: 주문 전환(OD-01) · 복제 — 이미 전환됐는지는 응답에 없어 409 QUOTE_ALREADY_CONVERTED로 안다
 *  - REJECTED·WITHDRAWN: 복제 + 대체 견적으로 이동 (QT-28)
 *  - EXPIRED: 복제
 */
type Dialog = 'send' | 'withdraw' | 'resend' | 'expire' | 'convert' | null

export function QuoteDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const { data: quote, isPending, error, refetch } = useQuoteDetail(id)
  const actions = useQuoteActions(id)
  const [dialog, setDialog] = useState<Dialog>(null)
  const close = () => setDialog(null)

  if (isPending) return <DetailSkeleton />
  if (error || !quote) {
    return (
      <>
        <BackLink to="/quotes" label="견적" />
        <NotFound code={codeOf(error)} backLabel="견적 목록으로" onBack={() => navigate('/quotes')} onRetry={() => refetch()} />
      </>
    )
  }

  const open = quote.status === 'SENT' || quote.status === 'VIEWED'
  const draft = quote.status === 'DRAFT'
  const cloneError = actions.clone.error ? codeOf(actions.clone.error) : null

  return (
    <Box className="enter-fade">
      <BackLink to="/quotes" label="견적" />
      <PageHeader
        title={quote.quoteNo}
        badge={<QuoteStatusBadge status={quote.status} size="2" />}
        description={
          <>
            <Link to={`/deals/${quote.dealId}`} style={{ color: 'inherit' }}>
              {quote.dealTitle}
            </Link>
            {' · '}유효기간 {date(`${quote.validUntil}T00:00:00Z`)}
            {quote.clonedFromQuoteId && (
              <>
                {' · '}
                <Link to={`/quotes/${quote.clonedFromQuoteId}`} style={{ color: 'inherit' }}>
                  복제 원본 보기
                </Link>
              </>
            )}
          </>
        }
        actions={
          <>
            {!draft && (
              <Button variant="soft" color="gray" onClick={() => navigate(`/quotes/${quote.id}/preview`)}>
                <EyeOpenIcon /> 고객 화면
              </Button>
            )}
            {quote.status === 'APPROVED' && (
              <Button color="green" onClick={() => setDialog('convert')}>
                <ArrowRightIcon /> 주문 전환
              </Button>
            )}
            <DropdownMenu.Root>
              <DropdownMenu.Trigger>
                <Button variant="soft" color="gray" aria-label="더 보기">
                  <DotsHorizontalIcon />
                </Button>
              </DropdownMenu.Trigger>
              <DropdownMenu.Content align="end">
                {open && (
                  <>
                    <DropdownMenu.Item onSelect={() => setDialog('resend')}>
                      <PaperPlaneIcon /> 수신인 변경 재발송
                    </DropdownMenu.Item>
                    <DropdownMenu.Item onSelect={() => setDialog('expire')}>
                      <Link2Icon /> 열람 링크 수동 만료
                    </DropdownMenu.Item>
                    <DropdownMenu.Separator />
                  </>
                )}
                <DropdownMenu.Item onSelect={() => actions.clone.mutate(undefined, { onSuccess: (created) => navigate(`/quotes/${created.id}`) })}>
                  <CopyIcon /> 복제해서 새 견적
                </DropdownMenu.Item>
                {open && (
                  <DropdownMenu.Item color="red" onSelect={() => setDialog('withdraw')}>
                    <ResetIcon /> 회수
                  </DropdownMenu.Item>
                )}
              </DropdownMenu.Content>
            </DropdownMenu.Root>
          </>
        }
      />

      {cloneError && <ErrorCallout code={cloneError} />}
      {actions.send.isSuccess && actions.send.data && (
        <Callout.Root color="green" mb="4" className="enter">
          <Callout.Text>
            발송했습니다. 고객에게 열람 링크가 담긴 메일이 갑니다. 딜 단계: {DEAL_STAGE_LABEL[actions.send.data.dealStage]}
          </Callout.Text>
        </Callout.Root>
      )}

      {/* 대체 견적 (QT-28) — 반려·회수된 견적에서 그것을 대신한 새 견적으로 */}
      {quote.supersededByQuoteId && (
        <Callout.Root color="blue" mb="4">
          <Callout.Text>
            이 견적은 새 견적으로 대체되었습니다.{' '}
            <Link to={`/quotes/${quote.supersededByQuoteId}`}>대체한 견적으로 이동 →</Link>
          </Callout.Text>
        </Callout.Root>
      )}
      {quote.status === 'REJECTED' && (
        <Callout.Root color="gray" mb="4">
          <Callout.Text>
            반려 사유: {quote.rejectReason ?? '—'}
          </Callout.Text>
        </Callout.Root>
      )}

      <TrackingCards quote={quote} />

      {draft ? (
        <QuoteEditor quote={quote} onSend={() => setDialog('send')} onPreview={() => navigate(`/quotes/${quote.id}/preview`)} />
      ) : (
        <ReadOnlyItems quote={quote} />
      )}

      <SendQuoteDialog
        open={dialog === 'send'}
        onOpenChange={(o) => { if (!o) { close(); actions.send.reset() } }}
        quote={quote}
        loading={actions.send.isPending}
        error={actions.send.error}
        onSubmit={(recipientContactId, message) => actions.send.mutate({ recipientContactId, message }, { onSuccess: close })}
      />
      <ResendDialog
        open={dialog === 'resend'}
        onOpenChange={(o) => { if (!o) { close(); actions.resend.reset() } }}
        quote={quote}
        loading={actions.resend.isPending}
        error={actions.resend.error}
        onSubmit={(recipientContactId) => actions.resend.mutate({ recipientContactId }, { onSuccess: close })}
      />
      <WithdrawDialog
        open={dialog === 'withdraw'}
        onOpenChange={(o) => { if (!o) { close(); actions.withdraw.reset() } }}
        quote={quote}
        loading={actions.withdraw.isPending}
        error={actions.withdraw.error}
        onConfirm={() => actions.withdraw.mutate(undefined, { onSuccess: close })}
      />
      <ExpireLinkDialog
        open={dialog === 'expire'}
        onOpenChange={(o) => { if (!o) { close(); actions.expire.reset() } }}
        loading={actions.expire.isPending}
        error={actions.expire.error}
        onConfirm={() => actions.expire.mutate(undefined, { onSuccess: close })}
      />
      <ConvertDialog
        open={dialog === 'convert'}
        onOpenChange={(o) => { if (!o) { close(); actions.convert.reset() } }}
        quote={quote}
        loading={actions.convert.isPending}
        error={actions.convert.error}
        onConfirm={() => actions.convert.mutate(undefined, { onSuccess: (order) => navigate(`/orders/${order.id}`) })}
      />
    </Box>
  )
}

/** 추적 정보 — 발송·열람·응답 시각, 응답자(자기 신고), 유효기간 */
function TrackingCards({ quote }: { quote: QuoteDetailResponse }) {
  const open = quote.status === 'SENT' || quote.status === 'VIEWED'
  return (
    <Grid columns={{ initial: '1', sm: '2', md: '4' }} gap="3" mb="5">
      <Card size="2">
        <Text as="div" size="1" color="gray" mb="1">합계 (부가세 {VAT_MODE_LABEL[quote.vatMode]})</Text>
        <Money value={quote.totalAmount} unit size="4" weight="bold" />
      </Card>
      <Card size="2">
        <Text as="div" size="1" color="gray" mb="1">유효기간</Text>
        <Flex align="center" gap="2" wrap="wrap">
          <Text size="3" weight="medium">{date(`${quote.validUntil}T00:00:00Z`)}</Text>
          {(open || quote.status === 'DRAFT') && <RemainingBadge until={`${quote.validUntil}T00:00:00Z`} />}
        </Flex>
      </Card>
      <Card size="2">
        <Text as="div" size="1" color="gray" mb="1">발송 · 열람</Text>
        {quote.sentAt ? (
          <Flex direction="column" gap="1">
            <Text size="2">{dateTime(quote.sentAt)} 발송</Text>
            {open ? <ViewedBadge firstViewedAt={quote.firstViewedAt} sentAt={quote.sentAt} /> : quote.firstViewedAt && <Text size="2" color="gray">{dateTime(quote.firstViewedAt)} 첫 열람</Text>}
          </Flex>
        ) : (
          <Text size="2" color="gray">아직 발송 전</Text>
        )}
      </Card>
      <Card size="2">
        <Text as="div" size="1" color="gray" mb="1">고객 응답</Text>
        {quote.respondedAt ? (
          <Flex direction="column" gap="1">
            <Text size="2">{dateTime(quote.respondedAt)}</Text>
            {quote.responderName && (
              <Flex align="center" gap="1" wrap="wrap">
                <Text size="2" weight="medium">{quote.responderName}</Text>
                {quote.responderTitle && <Text size="2" color="gray">{quote.responderTitle}</Text>}
                <Badge color="gray" variant="soft" size="1">고객이 직접 입력</Badge>
              </Flex>
            )}
          </Flex>
        ) : (
          <Text size="2" color="gray">—</Text>
        )}
      </Card>
    </Grid>
  )
}

/** 발송 후 읽기 전용 품목 (QT-16) — 단가 조정 흔적(catalogPriceAtCreation)도 그대로 보여준다 (QT-24) */
function ReadOnlyItems({ quote }: { quote: QuoteDetailResponse }) {
  const items = quote.items.slice().sort((a, b) => a.sortOrder - b.sortOrder)
  return (
    <Card size="3" className="enter-fade">
      <Table.Root variant="ghost" size="2">
        <Table.Header>
          <Table.Row>
            <Table.ColumnHeaderCell>품목</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell width="80px">단위</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell width="80px" align="right">수량</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell width="140px" align="right">단가</Table.ColumnHeaderCell>
            <Table.ColumnHeaderCell width="140px" align="right">금액</Table.ColumnHeaderCell>
          </Table.Row>
        </Table.Header>
        <Table.Body>
          {items.map((it) => (
            <Table.Row key={it.id}>
              <Table.RowHeaderCell>
                <Text size="2" weight="medium">{it.name}</Text>
                <Box mt="1">
                  {it.catalogPriceAtCreation === null ? (
                    <Badge color="gray" variant="soft" size="1">직접 입력</Badge>
                  ) : it.catalogPriceAtCreation !== it.unitPrice ? (
                    <Badge color="amber" variant="soft" size="1">
                      작성 당시 카탈로그 <Money value={it.catalogPriceAtCreation} size="1" />원
                    </Badge>
                  ) : null}
                </Box>
              </Table.RowHeaderCell>
              <Table.Cell><Text color="gray">{it.unit}</Text></Table.Cell>
              <Table.Cell align="right">{it.quantity}</Table.Cell>
              <Table.Cell align="right"><Money value={it.unitPrice} /></Table.Cell>
              <Table.Cell align="right"><Money value={it.amount} /></Table.Cell>
            </Table.Row>
          ))}
        </Table.Body>
      </Table.Root>
      <Flex direction="column" align="end" gap="1" mt="4" px="3">
        <Flex align="baseline" gap="4"><Text size="2" color="gray">공급가액</Text><Money value={quote.supplyAmount} size="3" /></Flex>
        <Flex align="baseline" gap="4"><Text size="2" color="gray">부가세 ({VAT_MODE_LABEL[quote.vatMode]})</Text><Money value={quote.vatAmount} size="3" /></Flex>
        <Flex align="baseline" gap="4" mt="1"><Text size="2" color="gray">합계</Text><Money value={quote.totalAmount} unit size="5" weight="bold" /></Flex>
      </Flex>
      {quote.terms && (
        <Box mt="4" px="3">
          <Text as="div" size="1" color="gray" mb="1">안내 문구</Text>
          <Text as="p" size="2" style={{ whiteSpace: 'pre-wrap' }}>{quote.terms}</Text>
        </Box>
      )}
    </Card>
  )
}

function DetailSkeleton() {
  return (
    <Box>
      <Skeleton height="20px" width="60px" mb="3" />
      <Skeleton height="32px" width="240px" mb="5" />
      <Grid columns={{ initial: '1', sm: '2', md: '4' }} gap="3" mb="5">
        {[0, 1, 2, 3].map((i) => <Skeleton key={i} height="72px" />)}
      </Grid>
      <Skeleton height="320px" />
    </Box>
  )
}
