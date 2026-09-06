import { useState } from 'react'
import { Box, Button, Callout, Flex, Skeleton, Text } from '@radix-ui/themes'
import { CheckCircledIcon, ChatBubbleIcon, Pencil1Icon, PlusIcon, TrashIcon } from '@radix-ui/react-icons'
import { ActivityChannelBadge, AutoBadge, ConfirmDialog, ErrorCallout } from '../../../shared/ui'
import { ApiError, codeOf } from '../../../shared/api/client'
import { dateTime } from '../../../shared/lib/format'
import type { ActivityResponse } from '../../../shared/api/types'
import { useSession } from '../../../app/session'
import { useActivityMutations, useCreateTask, useDealActivities } from '../hooks'
import { ActivityFormDialog } from './ActivityFormDialog'
import { TaskFormDialog } from './TaskFormDialog'

/**
 * Deal 타임라인 (10 §5.3 · AC-06·07) — 수동 기록과 자동 기록을 한 줄기로.
 *
 * - 자동 기록은 `자동` 배지 + 수정·삭제 없음
 * - 수동 기록은 **본인이 작성한 것에만** 수정·삭제 노출 (AC-04·05 — 관리자도 타인 기록 불가)
 * - `+ 상담 기록` · `+ 할 일` — 할 일은 등록만 있고 목록은 대시보드 후속 필요가 담당 (GAP-04)
 */
export function DealTimeline({ dealId, readOnly = false }: { dealId: string; readOnly?: boolean }) {
  const session = useSession()
  const { data, isPending, error, refetch } = useDealActivities(dealId)
  const mutations = useActivityMutations(dealId)
  const createTask = useCreateTask(dealId)

  const [dialog, setDialog] = useState<{ kind: 'create' } | { kind: 'edit'; activity: ActivityResponse } | { kind: 'task' } | null>(null)
  const [toDelete, setToDelete] = useState<ActivityResponse | null>(null)
  const [taskDone, setTaskDone] = useState(false)

  const close = () => {
    setDialog(null)
    mutations.create.reset()
    mutations.update.reset()
    createTask.reset()
  }

  return (
    <Box>
      {!readOnly && (
        <Flex gap="2" mb="4" wrap="wrap">
          <Button variant="soft" size="2" onClick={() => setDialog({ kind: 'create' })}>
            <PlusIcon /> 상담 기록
          </Button>
          <Button variant="soft" color="gray" size="2" onClick={() => setDialog({ kind: 'task' })}>
            <PlusIcon /> 할 일
          </Button>
        </Flex>
      )}

      {taskDone && (
        <Callout.Root color="green" size="1" mb="3" className="enter">
          <Callout.Icon>
            <CheckCircledIcon />
          </Callout.Icon>
          <Callout.Text>할 일을 등록했습니다. 대시보드 「후속 필요」에서 확인할 수 있습니다.</Callout.Text>
        </Callout.Root>
      )}

      {error && <ErrorCallout code={codeOf(error)} onRetry={() => refetch()} />}
      {mutations.remove.error && <ErrorCallout code={codeOf(mutations.remove.error)} />}

      {isPending ? (
        <Flex direction="column" gap="3">
          {[0, 1, 2].map((i) => (
            <Skeleton key={i} height="44px" />
          ))}
        </Flex>
      ) : !data || data.content.length === 0 ? (
        <Box py="5">
          <Text as="p" size="2" color="gray" align="center">
            아직 기록이 없습니다. 첫 상담을 기록해 두면 다음 사람이 맥락을 이어받을 수 있습니다.
          </Text>
        </Box>
      ) : (
        <Box style={{ position: 'relative', paddingLeft: 20 }}>
          <Box aria-hidden style={{ position: 'absolute', left: 5, top: 8, bottom: 8, width: 2, background: 'var(--gray-a4)', borderRadius: 1 }} />
          <Flex direction="column" gap="4">
            {data.content.map((activity) => {
              const mine = activity.type === 'MANUAL' && activity.authorMemberId === session.memberId
              return (
                <Box key={activity.id} style={{ position: 'relative' }}>
                  <Box
                    aria-hidden
                    style={{
                      position: 'absolute', left: -19, top: 6, width: 10, height: 10, borderRadius: '50%',
                      background: activity.type === 'MANUAL' ? 'var(--blue-9)' : 'var(--gray-7)',
                      boxShadow: '0 0 0 2px var(--color-panel-solid)',
                    }}
                  />
                  <Flex align="start" justify="between" gap="3">
                    <Box minWidth="0">
                      <Flex align="center" gap="2" wrap="wrap">
                        {activity.type === 'AUTO' ? <AutoBadge /> : activity.channel && <ActivityChannelBadge channel={activity.channel} />}
                        <Text size="2" style={{ whiteSpace: 'pre-wrap' }}>
                          {activity.content}
                        </Text>
                      </Flex>
                      <Text as="div" size="1" color="gray" mt="1">
                        {dateTime(activity.occurredAt)}
                        {activity.type === 'MANUAL' && (
                          <>
                            {' '}· {activity.authorMemberName}
                            {!activity.authorActive && ' (퇴사)'}
                          </>
                        )}
                      </Text>
                    </Box>
                    {mine && !readOnly && (
                      <Flex gap="1" style={{ flexShrink: 0 }}>
                        <Button size="1" variant="ghost" color="gray" onClick={() => setDialog({ kind: 'edit', activity })}>
                          <Pencil1Icon /> 수정
                        </Button>
                        <Button size="1" variant="ghost" color="red" onClick={() => setToDelete(activity)}>
                          <TrashIcon /> 삭제
                        </Button>
                      </Flex>
                    )}
                  </Flex>
                </Box>
              )
            })}
          </Flex>
        </Box>
      )}

      <ActivityFormDialog
        open={dialog?.kind === 'create' || dialog?.kind === 'edit'}
        onOpenChange={(open) => !open && close()}
        activity={dialog?.kind === 'edit' ? dialog.activity : undefined}
        loading={mutations.create.isPending || mutations.update.isPending}
        error={mutations.create.error ?? mutations.update.error}
        onSubmit={(body) => {
          if (dialog?.kind === 'edit') mutations.update.mutate({ id: dialog.activity.id, body }, { onSuccess: close })
          else mutations.create.mutate(body, { onSuccess: close })
        }}
      />

      <TaskFormDialog
        open={dialog?.kind === 'task'}
        onOpenChange={(open) => !open && close()}
        loading={createTask.isPending}
        error={createTask.error}
        onSubmit={(body) =>
          createTask.mutate(body, {
            onSuccess: () => {
              close()
              setTaskDone(true)
            },
          })
        }
      />

      <ConfirmDialog
        open={toDelete !== null}
        onOpenChange={(open) => {
          if (!open) {
            setToDelete(null)
            mutations.remove.reset()
          }
        }}
        title="상담 기록을 삭제하시겠습니까?"
        description="삭제한 기록은 타임라인과 고객사 이력에서 함께 사라집니다."
        confirmLabel="삭제"
        confirmColor="red"
        loading={mutations.remove.isPending}
        onConfirm={() => toDelete && mutations.remove.mutate(toDelete.id, { onSuccess: () => setToDelete(null) })}
      >
        {toDelete && (
          <Flex align="center" gap="2" mt="3">
            <ChatBubbleIcon color="var(--gray-9)" />
            <Text size="2" color="gray" truncate>
              {toDelete.content}
            </Text>
          </Flex>
        )}
        {mutations.remove.error instanceof ApiError && <ErrorCallout code={mutations.remove.error.code} />}
      </ConfirmDialog>
    </Box>
  )
}
