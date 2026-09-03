import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Badge, Box, Button, Card, Flex, Grid, Heading, Skeleton, Tabs, Text } from '@radix-ui/themes'
import { ArrowLeftIcon, CheckCircledIcon, ColumnsIcon, Pencil1Icon, TrashIcon } from '@radix-ui/react-icons'
import { ConfirmDialog, EmptyState, ErrorCallout, Money, PageHeader } from '../../../shared/ui'
import { ApiError } from '../../../shared/api/client'
import { date } from '../../../shared/lib/format'
import type { ContactResponse } from '../../../shared/api/types'
import { useSession } from '../../../app/session'
import { useContactMutations, useCustomerActivities, useCustomerDetail, useDeleteCustomer, useUpdateCustomer } from '../hooks'
import { CustomerFormDialog } from '../components/CustomerFormDialog'
import { ContactFormDialog } from '../components/ContactFormDialog'
import { ContactCards } from '../components/ContactCards'
import { DealHistoryTable } from '../components/DealHistoryTable'
import { ActivityTimeline } from '../components/ActivityTimeline'
import { IndustryBadge } from '../components/IndustryBadge'

/**
 * 고객사 상세 (CU-05·12, 10 §5.9) — 구성원 앱 상세 화면의 예제.
 * 제목 → 정보 카드 → 담당자 카드 → Deal 이력/활동 탭.
 *
 * - 404는 존재 여부를 구별하지 않는다 (SC-09)
 * - 삭제만 AlertDialog, 나머지는 Dialog (§2.5)
 * - 서버 거절 사유는 모달 안에 표시하고, 진행 중 딜이면 딜 보드 링크를 함께 준다 (CU-08)
 */
export function CustomerDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const session = useSession()
  const { data: customer, isPending, error, refetch } = useCustomerDetail(id)
  const activities = useCustomerActivities(id)

  const updateMutation = useUpdateCustomer(id)
  const deleteMutation = useDeleteCustomer()
  const contactMutations = useContactMutations(id)

  const [editing, setEditing] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [contactDialog, setContactDialog] = useState<{ mode: 'create' } | { mode: 'edit'; contact: ContactResponse } | null>(null)
  const [contactToDelete, setContactToDelete] = useState<ContactResponse | null>(null)

  if (isPending) return <DetailSkeleton />

  if (error || !customer) {
    const code = error instanceof ApiError ? error.code : 'INTERNAL_ERROR'
    return (
      <>
        <BackLink />
        {code === 'RESOURCE_NOT_FOUND' ? (
          <EmptyState
            title="요청한 대상을 찾을 수 없습니다"
            description="삭제되었거나 주소가 잘못되었을 수 있습니다."
            action={{ label: '고객사 목록으로', onClick: () => navigate('/customers') }}
          />
        ) : (
          <ErrorCallout code={code} onRetry={() => refetch()} />
        )}
      </>
    )
  }

  const activeDeals = customer.deals.filter((d) => !['WON', 'LOST'].includes(d.stage)).length
  const wonDeals = customer.deals.filter((d) => d.stage === 'WON')
  const wonTotal = wonDeals.reduce((sum, d) => sum + (d.wonAmount ?? 0), 0)
  const deleteError = deleteMutation.error instanceof ApiError ? deleteMutation.error : null
  const isMe = customer.createdByMemberId === session.memberId

  const closeContactDialog = () => setContactDialog(null)
  const submitContact = (body: Parameters<typeof contactMutations.create.mutate>[0], makePrimary: boolean) => {
    if (contactDialog?.mode === 'edit') {
      const contactId = contactDialog.contact.id
      contactMutations.update.mutate(
        { contactId, body },
        {
          onSuccess: () => {
            if (makePrimary) contactMutations.setPrimary.mutate(contactId)
            closeContactDialog()
          },
        },
      )
    } else {
      contactMutations.create.mutate(body, {
        onSuccess: (created) => {
          if (makePrimary) contactMutations.setPrimary.mutate(created.id)
          closeContactDialog()
        },
      })
    }
  }

  return (
    <Box className="enter-fade">
      <BackLink />
      <PageHeader
        title={customer.name}
        badge={customer.industry && <IndustryBadge industry={customer.industry} size="2" />}
        description={
          <>
            {customer.createdByMemberName}
            {isMe && ' (나)'} 등록 · {date(customer.createdAt)}
          </>
        }
        actions={
          <>
            <Button variant="soft" color="gray" onClick={() => setEditing(true)}>
              <Pencil1Icon /> 수정
            </Button>
            <Button variant="soft" color="red" onClick={() => setDeleting(true)}>
              <TrashIcon /> 삭제
            </Button>
          </>
        }
      />

      {/* 정보 카드 */}
      <Grid columns={{ initial: '1', sm: '1fr 1fr 2fr' }} gap="3" mb="7">
        <InfoCard label="업종" value={customer.industry} />
        <InfoCard label="규모" value={customer.size} />
        <InfoCard label="비고" value={customer.note} />
      </Grid>

      {/* 담당자 */}
      <Flex align="baseline" justify="between" mb="3">
        <Heading size="4">
          고객사 담당자{' '}
          <Text size="2" color="gray" weight="regular">
            {customer.contacts.length}명
          </Text>
        </Heading>
      </Flex>
      <Box mb="7">
        <ContactCards
          contacts={customer.contacts}
          onAdd={() => setContactDialog({ mode: 'create' })}
          onEdit={(contact) => setContactDialog({ mode: 'edit', contact })}
          onDelete={setContactToDelete}
          onSetPrimary={(contact) => contactMutations.setPrimary.mutate(contact.id)}
          settingPrimary={contactMutations.setPrimary.isPending}
        />
        {contactMutations.setPrimary.error && (
          <ErrorCallout code={contactMutations.setPrimary.error instanceof ApiError ? contactMutations.setPrimary.error.code : 'INTERNAL_ERROR'} />
        )}
      </Box>

      {/* Deal 이력 / 활동 */}
      <Card size="3">
        <Tabs.Root defaultValue="deals">
          <Flex align="center" justify="between" wrap="wrap" gap="3">
            <Tabs.List>
              <Tabs.Trigger value="deals">
                Deal 이력{' '}
                <Badge color="gray" variant="soft" size="1" ml="1">
                  {customer.deals.length}
                </Badge>
              </Tabs.Trigger>
              <Tabs.Trigger value="activities">
                활동 모아보기
                {activities.data && (
                  <Badge color="gray" variant="soft" size="1" ml="1">
                    {activities.data.totalElements}
                  </Badge>
                )}
              </Tabs.Trigger>
            </Tabs.List>
            <Flex gap="2" wrap="wrap">
              <StatChip label="진행 중" value={`${activeDeals}건`} color="blue" icon={<ColumnsIcon />} />
              <StatChip label="성사" value={`${wonDeals.length}건`} color="green" icon={<CheckCircledIcon />} />
              {wonTotal > 0 && <StatChip label="성사 금액" value={<Money value={wonTotal} unit weight="bold" />} color="red" />}
            </Flex>
          </Flex>
          <Box pt="3">
            <Tabs.Content value="deals">
              <DealHistoryTable deals={customer.deals} />
            </Tabs.Content>
            <Tabs.Content value="activities">
              <Box pt="2">
                <ActivityTimeline activities={activities.data?.content} loading={activities.isPending} />
              </Box>
            </Tabs.Content>
          </Box>
        </Tabs.Root>
      </Card>

      {/* ── 고객사 수정 · 삭제 */}
      <CustomerFormDialog
        open={editing}
        onOpenChange={(open) => {
          setEditing(open)
          if (!open) updateMutation.reset()
        }}
        customer={customer}
        loading={updateMutation.isPending}
        error={updateMutation.error}
        onSubmit={(body) => updateMutation.mutate(body, { onSuccess: () => setEditing(false) })}
      />
      <ConfirmDialog
        open={deleting}
        onOpenChange={(open) => {
          setDeleting(open)
          if (!open) deleteMutation.reset()
        }}
        title={`${customer.name}을(를) 삭제하시겠습니까?`}
        description="담당자와 이력이 함께 숨겨집니다. 진행 중인 딜이 있으면 삭제할 수 없습니다."
        confirmLabel="삭제"
        confirmColor="red"
        loading={deleteMutation.isPending}
        onConfirm={() => deleteMutation.mutate(customer.id, { onSuccess: () => navigate('/customers', { replace: true }) })}
      >
        {deleteError && (
          <>
            <ErrorCallout code={deleteError.code} />
            {deleteError.code === 'CUSTOMER_HAS_ACTIVE_DEALS' && (
              <Button variant="soft" color="amber" onClick={() => navigate('/deals')}>
                <ColumnsIcon /> 진행 중 Deal 보기 ({activeDeals}건)
              </Button>
            )}
          </>
        )}
      </ConfirmDialog>

      {/* ── 담당자 추가 · 수정 · 삭제 */}
      <ContactFormDialog
        open={contactDialog !== null}
        onOpenChange={(open) => {
          if (!open) {
            closeContactDialog()
            contactMutations.create.reset()
            contactMutations.update.reset()
          }
        }}
        contact={contactDialog?.mode === 'edit' ? contactDialog.contact : undefined}
        loading={contactMutations.create.isPending || contactMutations.update.isPending}
        error={contactMutations.create.error ?? contactMutations.update.error}
        onSubmit={submitContact}
      />
      <ConfirmDialog
        open={contactToDelete !== null}
        onOpenChange={(open) => {
          if (!open) {
            setContactToDelete(null)
            contactMutations.remove.reset()
          }
        }}
        title={`${contactToDelete?.name ?? ''} 담당자를 삭제하시겠습니까?`}
        description="대표 담당자이거나 견적 발송 이력이 있으면 삭제할 수 없습니다."
        confirmLabel="삭제"
        confirmColor="red"
        loading={contactMutations.remove.isPending}
        onConfirm={() => contactToDelete && contactMutations.remove.mutate(contactToDelete.id, { onSuccess: () => setContactToDelete(null) })}
      >
        {contactMutations.remove.error && (
          <ErrorCallout code={contactMutations.remove.error instanceof ApiError ? contactMutations.remove.error.code : 'INTERNAL_ERROR'} />
        )}
      </ConfirmDialog>
    </Box>
  )
}

function InfoCard({ label, value }: { label: string; value: string | null }) {
  return (
    <Card size="2">
      <Text as="div" size="1" color="gray" mb="1">
        {label}
      </Text>
      <Text as="div" size="3" weight={value ? 'medium' : 'regular'} color={value ? undefined : 'gray'}>
        {value ?? '—'}
      </Text>
    </Card>
  )
}

function BackLink() {
  return (
    <Text asChild size="2" color="gray" mb="3" style={{ display: 'inline-flex', alignItems: 'center', gap: 4, textDecoration: 'none' }}>
      <Link to="/customers">
        <ArrowLeftIcon /> 고객사
      </Link>
    </Text>
  )
}

/** 집계 칩. 진행 중 blue · 성사 건수 green · 성사 금액 red (10 §2.3 예외) */
function StatChip({ label, value, color, icon }: { label: string; value: React.ReactNode; color: 'blue' | 'green' | 'red'; icon?: React.ReactNode }) {
  return (
    <Flex
      align="center"
      gap="2"
      px="3"
      py="1"
      style={{ background: `var(--${color}-a3)`, color: `var(--${color}-11)`, borderRadius: 'var(--radius-3)', lineHeight: 1 }}
    >
      {icon && <Box style={{ display: 'inline-flex' }}>{icon}</Box>}
      <Text size="1" weight="medium" style={{ opacity: 0.85 }}>
        {label}
      </Text>
      <Text size="3" weight="bold">
        {value}
      </Text>
    </Flex>
  )
}

function DetailSkeleton() {
  return (
    <Box>
      <Skeleton height="20px" width="60px" mb="3" />
      <Skeleton height="32px" width="240px" mb="5" />
      <Grid columns={{ initial: '1', sm: '1fr 1fr 2fr' }} gap="3" mb="7">
        <Skeleton height="72px" />
        <Skeleton height="72px" />
        <Skeleton height="72px" />
      </Grid>
      <Skeleton height="24px" width="140px" mb="3" />
      <Grid columns={{ initial: '1', sm: '2', lg: '3' }} gap="3" mb="6">
        <Skeleton height="140px" />
        <Skeleton height="140px" />
        <Skeleton height="140px" />
      </Grid>
      <Skeleton height="260px" />
    </Box>
  )
}
