import { useState } from 'react'
import { Box, Callout, Text, TextArea } from '@radix-ui/themes'
import { ExclamationTriangleIcon } from '@radix-ui/react-icons'
import { ApiError } from '../../../shared/api/client'
import { ConfirmDialog, ErrorCallout } from '../../../shared/ui'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  loading: boolean
  error: unknown
  onConfirm: (reason: string) => void
  onRetry?: () => void
}

/**
 * 실패 처리 (DL-10·11, 전이표 §5). 되돌릴 수 없는 효과가 붙으므로 AlertDialog (10 §2.5).
 *
 * 효과: 진행 중이던 견적(발송됨·열람됨)이 기간 만료로 바뀌고 열람 링크가 닫힌다(DEAL_LOST).
 * 재개(DL-12)해도 만료된 견적·링크는 돌아오지 않는다 — 그래서 여기서 미리 말한다.
 */
export function LoseDealDialog({ open, onOpenChange, title, loading, error, onConfirm, onRetry }: Props) {
  const [reason, setReason] = useState('')
  const apiError = error instanceof ApiError ? error : null

  return (
    <ConfirmDialog
      open={open}
      onOpenChange={(next) => {
        onOpenChange(next)
        if (!next) setReason('')
      }}
      title={`${title} — 실패 처리하시겠습니까?`}
      description="사유는 딜에 기록됩니다. 실패한 딜은 보드의 접힌 영역으로 이동하고, 나중에 재개할 수 있습니다."
      confirmLabel="실패 처리"
      confirmColor="red"
      loading={loading}
      onConfirm={() => reason.trim() && onConfirm(reason.trim())}
    >
      <Box mt="3">
        <Text as="label" htmlFor="lose-reason" size="2" weight="medium">
          실패 사유 (필수)
        </Text>
        <TextArea id="lose-reason" mt="1" rows={3} placeholder="예: 경쟁사 선정" value={reason} disabled={loading} onChange={(e) => setReason(e.target.value)} />
        {apiError?.reasonOf('reason') && (
          <Text as="div" size="1" color="red" mt="1">
            {apiError.reasonOf('reason')}
          </Text>
        )}
      </Box>

      <Callout.Root color="amber" mt="3" size="1">
        <Callout.Icon>
          <ExclamationTriangleIcon />
        </Callout.Icon>
        <Callout.Text>진행 중인 견적은 기간 만료로 바뀌고 고객 열람 링크가 닫힙니다. 재개해도 복원되지 않습니다.</Callout.Text>
      </Callout.Root>

      {apiError && apiError.code !== 'VALIDATION_FAILED' && <ErrorCallout code={apiError.code} onRetry={onRetry} />}
    </ConfirmDialog>
  )
}
