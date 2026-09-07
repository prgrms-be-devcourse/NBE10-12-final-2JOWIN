import { useState, type FormEvent } from 'react'
import { Button, Callout, Dialog, Flex, TextField } from '@radix-ui/themes'
import { InfoCircledIcon } from '@radix-ui/react-icons'
import { ApiError } from '../../../shared/api/client'
import { ErrorCallout, Field } from '../../../shared/ui'
import type { CreateTaskRequest } from '../../../shared/api/types'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  loading: boolean
  error: unknown
  onSubmit: (body: CreateTaskRequest) => void
}

/**
 * 다음 할 일 등록 (AC-09). 배정 개념이 없다 — 담당 Deal의 할 일이 곧 담당자의 할 일 (Q-29).
 * 할 일 목록 API는 v1에 없고(GAP-04 확정), 등록한 항목은 대시보드 「후속 필요」에서만 보인다 (DB-05).
 */
export function TaskFormDialog({ open, onOpenChange, loading, error, onSubmit }: Props) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content maxWidth="440px">
        <TaskForm loading={loading} error={error} onSubmit={onSubmit} />
      </Dialog.Content>
    </Dialog.Root>
  )
}

function TaskForm({ loading, error, onSubmit }: Omit<Props, 'open' | 'onOpenChange'>) {
  const [content, setContent] = useState('')
  const [dueDate, setDueDate] = useState('')
  const apiError = error instanceof ApiError ? error : null

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!content.trim() || !dueDate) return
    onSubmit({ content: content.trim(), dueDate })
  }

  return (
    <>
      <Dialog.Title>다음 할 일</Dialog.Title>
      <Dialog.Description size="2" color="gray">
        이 딜의 담당자가 챙길 일을 마감일과 함께 남깁니다.
      </Dialog.Description>

      <form onSubmit={handleSubmit}>
        <Flex direction="column" gap="4" mt="4">
          <Field label="내용" required error={apiError?.reasonOf('content')} hint="500자 이내">
            <TextField.Root value={content} onChange={(e) => setContent(e.target.value)} placeholder="예: 금요일까지 견적 발송" autoFocus disabled={loading} maxLength={500} />
          </Field>
          <Field label="마감일" required error={apiError?.reasonOf('dueDate')}>
            <TextField.Root type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} disabled={loading} />
          </Field>

          <Callout.Root color="gray" size="1">
            <Callout.Icon>
              <InfoCircledIcon />
            </Callout.Icon>
            <Callout.Text>등록한 할 일은 대시보드의 「후속 필요」에서 확인하고 완료 처리합니다.</Callout.Text>
          </Callout.Root>

          {apiError && apiError.code !== 'VALIDATION_FAILED' && <ErrorCallout code={apiError.code} />}

          <Flex gap="3" justify="end" mt="2">
            <Dialog.Close>
              <Button type="button" variant="soft" color="gray" disabled={loading}>
                취소
              </Button>
            </Dialog.Close>
            <Button type="submit" loading={loading} disabled={!content.trim() || !dueDate}>
              등록
            </Button>
          </Flex>
        </Flex>
      </form>
    </>
  )
}
