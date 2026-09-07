import { useState } from 'react'
import { Avatar, Box, Callout, Flex, Select, Text } from '@radix-ui/themes'
import { InfoCircledIcon } from '@radix-ui/react-icons'
import { ApiError } from '../../../shared/api/client'
import { ConfirmDialog, ErrorCallout } from '../../../shared/ui'
import { SELECT_CONTENT } from '../../customer/constants'
import { useMemberOptions } from '../hooks'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  currentAssigneeId: string
  currentAssigneeName: string
  loading: boolean
  error: unknown
  onConfirm: (assigneeMemberId: string) => void
  onRetry?: () => void
}

/**
 * 담당자 변경 (DL-05, SC-06) — 기업 관리자 전용. 이전 담당자는 즉시 접근을 잃으므로 AlertDialog.
 * 선택지는 활성 구성원뿐 (/members/options, DL-04).
 */
export function ChangeAssigneeDialog({ open, onOpenChange, currentAssigneeId, currentAssigneeName, loading, error, onConfirm, onRetry }: Props) {
  const options = useMemberOptions(open)
  const [assigneeId, setAssigneeId] = useState(currentAssigneeId)
  const apiError = error instanceof ApiError ? error : null
  const changed = assigneeId !== currentAssigneeId

  return (
    <ConfirmDialog
      open={open}
      onOpenChange={(next) => {
        onOpenChange(next)
        if (!next) setAssigneeId(currentAssigneeId)
      }}
      title="담당자를 변경합니다"
      description={`현재 담당자는 ${currentAssigneeName}입니다. 변경하면 이전 담당자는 이 딜과 딸린 견적·주문·할 일에 더 이상 접근할 수 없습니다.`}
      confirmLabel="변경"
      loading={loading}
      onConfirm={() => changed && onConfirm(assigneeId)}
    >
      <Box mt="3">
        <Text as="div" size="2" weight="medium" mb="1">
          새 담당자
        </Text>
        <Select.Root value={assigneeId} onValueChange={setAssigneeId} disabled={loading || options.isPending}>
          <Select.Trigger style={{ width: '100%' }} />
          <Select.Content {...SELECT_CONTENT}>
            {(options.data ?? []).map((m) => (
              <Select.Item key={m.id} value={m.id}>
                <Flex align="center" gap="2">
                  <Avatar size="1" radius="full" fallback={m.name.slice(0, 1)} />
                  {m.name}
                  {m.id === currentAssigneeId ? ' (현재)' : ''}
                </Flex>
              </Select.Item>
            ))}
          </Select.Content>
        </Select.Root>
        <Text as="div" size="1" color="gray" mt="1">
          같은 회사의 활성 구성원만 선택할 수 있습니다.
        </Text>
      </Box>

      <Callout.Root color="gray" mt="3" size="1">
        <Callout.Icon>
          <InfoCircledIcon />
        </Callout.Icon>
        <Callout.Text>이미 남긴 상담 기록의 작성자 표기는 그대로 유지됩니다 (AC-08).</Callout.Text>
      </Callout.Root>

      {apiError && <ErrorCallout code={apiError.code} onRetry={onRetry} />}
    </ConfirmDialog>
  )
}
