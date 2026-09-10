import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { Badge, Button, Card, Flex, Select, Table, Tabs, Text, Tooltip } from '@radix-ui/themes'
import { Cross2Icon, PaperPlaneIcon, PersonIcon, PlusIcon, ReloadIcon } from '@radix-ui/react-icons'
import {
  ConfirmDialog, EmptyState, ErrorCallout, InvitationStatusBadge, MemberStatusBadge, PageHeader, Pagination, RoleBadge, TableSkeleton,
  INVITATION_STATUSES, INVITATION_STATUS_LABEL, ROLES, ROLE_LABEL, type InvitationStatus, type Role,
} from '../../../shared/ui'
import { codeOf } from '../../../shared/api/client'
import { date, dateShort } from '../../../shared/lib/format'
import type { InvitationResponse, MemberResponse } from '../../../shared/api/types'
import { useSession } from '../../../app/session'
import { PAGE_SIZE, SELECT_CONTENT } from '../../customer/constants'
import { useInvitationList, useInvitationMutations, useMemberList, useMemberMutations, useMemberOptions } from '../hooks'
import { DeactivateMemberDialog } from '../components/DeactivateMemberDialog'
import { InviteDialog } from '../components/InviteDialog'

/**
 * 구성원 · 초대 (MB-01~14 · 07 §A · 08 §A) — 기업 관리자 전용 (09 매트릭스, RequireAdmin이 진입을 막는다).
 *
 * - 구성원 탭: 역할 변경(즉시 PATCH), 비활성화(이관 강제 AlertDialog), 재활성화
 * - 초대 탭: 발송·재발송·취소. 재발송은 기존 초대를 만료시키고 새 행을 만든다 (Q-31)
 * - 마지막 관리자는 비활성화·강등 불가 (MB-11) — 버튼을 비활성 처리하고 이유를 툴팁으로.
 *   disabled 요소는 포인터 이벤트가 없어 Radix Tooltip이 안 뜬다 — span으로 감싸 툴팁은 span이 받게 한다
 * - 본인은 비활성화하지 않고 역할도 바꾸지 않는다 — 세션이 즉시 끊기거나(비활성화) 이 화면 진입 권한을 잃어(강등)
 *   화면 위에서 되돌릴 수 없다. 본인 역할은 다른 기업 관리자가 바꾼다
 */

const ALL = '__all__'

export function MemberListPage() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab') === 'invitations' ? 'invitations' : 'members'

  const setTab = (next: string) => {
    const merged = new URLSearchParams()
    if (next === 'invitations') merged.set('tab', 'invitations')
    setParams(merged, { replace: true })
  }

  return (
    <>
      <PageHeader
        title="구성원"
        description="회사에는 기업 관리자가 최소 한 명 남아 있어야 합니다. 초대는 발송 후 7일간 유효합니다."
      />
      <Tabs.Root value={tab} onValueChange={setTab}>
        <Tabs.List mb="4">
          <Tabs.Trigger value="members">
            <PersonIcon /> 구성원
          </Tabs.Trigger>
          <Tabs.Trigger value="invitations">
            <PaperPlaneIcon /> 초대
          </Tabs.Trigger>
        </Tabs.List>
        <Tabs.Content value="members">
          <MembersTab />
        </Tabs.Content>
        <Tabs.Content value="invitations">
          <InvitationsTab />
        </Tabs.Content>
      </Tabs.Root>
    </>
  )
}

// ── 구성원 ──────────────────────────────────────────────────────────────────

function MembersTab() {
  const session = useSession()
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Number(params.get('page') ?? 0))
  const query = useMemo(() => ({ page, size: PAGE_SIZE }), [page])
  const { data, isPending, isFetching, error, refetch } = useMemberList(query)
  const mutations = useMemberMutations()
  const [toDeactivate, setToDeactivate] = useState<MemberResponse | null>(null)
  const options = useMemberOptions(toDeactivate !== null)

  const setPage = (next: number) => {
    const merged = new URLSearchParams(params)
    if (next > 0) merged.set('page', String(next))
    else merged.delete('page')
    setParams(merged, { replace: true })
  }

  const activeAdmins = data?.content.filter((m) => m.role === 'COMPANY_ADMIN' && m.status === 'ACTIVE').length ?? 0
  const isLastAdmin = (m: MemberResponse) => m.role === 'COMPANY_ADMIN' && m.status === 'ACTIVE' && activeAdmins <= 1

  const closeDeactivate = () => {
    setToDeactivate(null)
    mutations.deactivate.reset()
  }

  if (isPending) return <TableSkeleton columns={[2, 3, 2, 1, 1, 1, 2]} />

  return (
    <>
      {error && <ErrorCallout code={codeOf(error)} onRetry={() => refetch()} />}
      {mutations.changeRole.error && <ErrorCallout code={codeOf(mutations.changeRole.error)} />}
      {mutations.reactivate.error && <ErrorCallout code={codeOf(mutations.reactivate.error)} />}

      {data && data.content.length === 0 ? (
        <EmptyState title="구성원이 없습니다" description="초대 탭에서 팀원을 초대해 보세요." />
      ) : (
        data && (
          <Card className="enter-fade" style={{ opacity: isFetching ? 0.7 : 1, transition: 'opacity var(--motion-fast)' }}>
            <Table.Root variant="ghost" size="2">
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeaderCell>이름</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell>이메일</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="130px">연락처</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="150px">역할</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="90px">상태</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="90px">등록일</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="120px" />
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {data.content.map((member) => {
                  const me = member.id === session.memberId
                  const lastAdmin = isLastAdmin(member)
                  const inactive = member.status === 'INACTIVE'
                  return (
                    <Table.Row key={member.id} className="row-hover">
                      <Table.RowHeaderCell>
                        <Text size="2" weight="medium" color={inactive ? 'gray' : undefined}>
                          {member.name}
                          {me && (
                            <Text size="1" color="gray"> (나)</Text>
                          )}
                        </Text>
                      </Table.RowHeaderCell>
                      <Table.Cell>
                        <Text size="2" color="gray">{member.email}</Text>
                      </Table.Cell>
                      <Table.Cell>
                        <Text size="2" color="gray">{member.phone ?? '—'}</Text>
                      </Table.Cell>
                      <Table.Cell>
                        {/* 역할 변경은 즉시 반영 (MB-08). 마지막 관리자 강등은 서버가 422로 막는다 (MB-11) */}
                        {inactive ? (
                          <RoleBadge role={member.role} />
                        ) : (
                          <Tooltip content={me ? '본인 역할은 다른 기업 관리자가 바꿀 수 있습니다' : lastAdmin ? '마지막 기업 관리자는 역할을 바꿀 수 없습니다' : '역할 변경'}>
                            <span style={{ display: 'inline-flex' }}>
                              <Select.Root
                                size="1"
                                value={member.role}
                                disabled={me || lastAdmin || (mutations.changeRole.isPending && mutations.changeRole.variables?.id === member.id)}
                                onValueChange={(role) => mutations.changeRole.mutate({ id: member.id, role: role as Role })}
                              >
                                <Select.Trigger variant="soft" color={member.role === 'COMPANY_ADMIN' ? 'blue' : 'gray'} />
                                <Select.Content {...SELECT_CONTENT}>
                                  {ROLES.map((r) => (
                                    <Select.Item key={r} value={r}>
                                      {ROLE_LABEL[r]}
                                    </Select.Item>
                                  ))}
                                </Select.Content>
                              </Select.Root>
                            </span>
                          </Tooltip>
                        )}
                      </Table.Cell>
                      <Table.Cell>
                        <MemberStatusBadge status={member.status} />
                      </Table.Cell>
                      <Table.Cell>
                        <Text size="2" color="gray">{dateShort(member.createdAt)}</Text>
                      </Table.Cell>
                      <Table.Cell>
                        <Flex justify="end">
                          {inactive ? (
                            <Button
                              size="1"
                              variant="soft"
                              color="green"
                              loading={mutations.reactivate.isPending && mutations.reactivate.variables === member.id}
                              onClick={() => mutations.reactivate.mutate(member.id)}
                            >
                              재활성화
                            </Button>
                          ) : me ? null : (
                            <Tooltip content={lastAdmin ? '마지막 기업 관리자는 비활성화할 수 없습니다' : '담당 Deal은 다른 구성원에게 이관됩니다'}>
                              <span style={{ display: 'inline-flex' }}>
                                <Button size="1" variant="soft" color="red" disabled={lastAdmin} onClick={() => setToDeactivate(member)}>
                                  비활성화
                                </Button>
                              </span>
                            </Tooltip>
                          )}
                        </Flex>
                      </Table.Cell>
                    </Table.Row>
                  )
                })}
              </Table.Body>
            </Table.Root>
            <Pagination data={data} unit="명" onPageChange={setPage} />
          </Card>
        )
      )}

      <DeactivateMemberDialog
        member={toDeactivate}
        onOpenChange={(open) => {
          if (!open) closeDeactivate()
        }}
        options={options.data ?? []}
        loading={mutations.deactivate.isPending}
        error={mutations.deactivate.error}
        onConfirm={(transferToMemberId) =>
          toDeactivate &&
          mutations.deactivate.mutate(
            { id: toDeactivate.id, body: transferToMemberId ? { transferToMemberId } : {} },
            { onSuccess: closeDeactivate },
          )
        }
      />
    </>
  )
}

// ── 초대 ────────────────────────────────────────────────────────────────────

function InvitationsTab() {
  const [params, setParams] = useSearchParams()
  const status = (params.get('status') ?? '') as InvitationStatus | ''
  const page = Math.max(0, Number(params.get('page') ?? 0))
  const query = useMemo(() => ({ status: status || undefined, page, size: PAGE_SIZE }), [status, page])
  const { data, isPending, isFetching, error, refetch } = useInvitationList(query)
  const mutations = useInvitationMutations()
  const [inviting, setInviting] = useState(false)
  const [toCancel, setToCancel] = useState<InvitationResponse | null>(null)

  const update = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value) merged.set(key, value)
      else merged.delete(key)
    }
    if (!('page' in next)) merged.delete('page')
    setParams(merged, { replace: true })
  }

  const closeInvite = () => {
    setInviting(false)
    mutations.create.reset()
  }

  return (
    <>
      <Flex gap="3" mb="4" align="center" wrap="wrap" justify="between">
        <Flex gap="3" align="center" wrap="wrap">
          <Select.Root value={status || ALL} onValueChange={(value) => update({ status: value === ALL ? '' : value })}>
            <Select.Trigger placeholder="상태" style={{ minWidth: 130 }} />
            <Select.Content {...SELECT_CONTENT}>
              <Select.Item value={ALL}>전체 상태</Select.Item>
              {INVITATION_STATUSES.map((s) => (
                <Select.Item key={s} value={s}>
                  {INVITATION_STATUS_LABEL[s]}
                </Select.Item>
              ))}
            </Select.Content>
          </Select.Root>
          {status && (
            <Button variant="ghost" color="gray" onClick={() => update({ status: '' })}>
              <Cross2Icon /> 필터 해제
            </Button>
          )}
        </Flex>
        <Button onClick={() => setInviting(true)}>
          <PlusIcon /> 초대
        </Button>
      </Flex>

      {error && <ErrorCallout code={codeOf(error)} onRetry={() => refetch()} />}
      {mutations.resend.error && <ErrorCallout code={codeOf(mutations.resend.error)} />}

      {isPending ? (
        <TableSkeleton columns={[3, 1, 1, 1, 1, 2]} />
      ) : data && data.content.length === 0 ? (
        status ? (
          <EmptyState title={`${INVITATION_STATUS_LABEL[status]} 초대가 없습니다`} action={{ label: '필터 해제', onClick: () => update({ status: '' }) }} />
        ) : (
          <EmptyState
            icon={<PaperPlaneIcon width="28" height="28" />}
            title="보낸 초대가 없습니다"
            description="팀원을 초대하면 초대 메일의 링크로 계정을 만들 수 있습니다."
            action={{ label: '첫 초대 보내기', onClick: () => setInviting(true) }}
          />
        )
      ) : (
        data && (
          <Card className="enter-fade" style={{ opacity: isFetching ? 0.7 : 1, transition: 'opacity var(--motion-fast)' }}>
            <Table.Root variant="ghost" size="2">
              <Table.Header>
                <Table.Row>
                  <Table.ColumnHeaderCell>이메일</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="130px">역할</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="110px">상태</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="110px">만료일</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="110px">발송일</Table.ColumnHeaderCell>
                  <Table.ColumnHeaderCell width="170px" />
                </Table.Row>
              </Table.Header>
              <Table.Body>
                {data.content.map((invitation) => {
                  const pending = invitation.status === 'PENDING'
                  return (
                    <Table.Row key={invitation.id} className="row-hover">
                      <Table.RowHeaderCell>
                        <Text size="2" weight="medium" color={pending ? undefined : 'gray'}>{invitation.email}</Text>
                      </Table.RowHeaderCell>
                      <Table.Cell>
                        <RoleBadge role={invitation.role} />
                      </Table.Cell>
                      <Table.Cell>
                        <InvitationStatusBadge status={invitation.status} />
                      </Table.Cell>
                      <Table.Cell>
                        <Text size="2" color="gray">{date(invitation.expiresAt)}</Text>
                      </Table.Cell>
                      <Table.Cell>
                        <Text size="2" color="gray">{date(invitation.createdAt)}</Text>
                      </Table.Cell>
                      <Table.Cell>
                        {pending && (
                          <Flex gap="2" justify="end">
                            <Button
                              size="1"
                              variant="soft"
                              color="gray"
                              loading={mutations.resend.isPending && mutations.resend.variables === invitation.id}
                              onClick={() => mutations.resend.mutate(invitation.id)}
                            >
                              <ReloadIcon /> 재발송
                            </Button>
                            <Button size="1" variant="soft" color="red" onClick={() => setToCancel(invitation)}>
                              취소
                            </Button>
                          </Flex>
                        )}
                      </Table.Cell>
                    </Table.Row>
                  )
                })}
              </Table.Body>
            </Table.Root>
            <Pagination data={data} unit="건" onPageChange={(next) => update({ page: String(next) })} />
          </Card>
        )
      )}

      <InviteDialog
        open={inviting}
        onOpenChange={(open) => {
          if (!open) closeInvite()
        }}
        loading={mutations.create.isPending}
        error={mutations.create.error}
        onSubmit={(body) => mutations.create.mutate(body, { onSuccess: closeInvite })}
      />

      <ConfirmDialog
        open={toCancel !== null}
        onOpenChange={(open) => {
          if (!open) {
            setToCancel(null)
            mutations.cancel.reset()
          }
        }}
        title={`${toCancel?.email ?? ''} 초대를 취소하시겠습니까?`}
        description="취소한 초대 링크는 더 이상 열리지 않습니다. 필요하면 새로 초대하면 됩니다."
        confirmLabel="초대 취소"
        confirmColor="red"
        loading={mutations.cancel.isPending}
        onConfirm={() => toCancel && mutations.cancel.mutate(toCancel.id, { onSuccess: () => setToCancel(null) })}
      >
        {mutations.cancel.error && <ErrorCallout code={codeOf(mutations.cancel.error)} />}
        {toCancel && (
          <Badge color="gray" variant="soft" mt="2">
            {ROLE_LABEL[toCancel.role]}
          </Badge>
        )}
      </ConfirmDialog>
    </>
  )
}
