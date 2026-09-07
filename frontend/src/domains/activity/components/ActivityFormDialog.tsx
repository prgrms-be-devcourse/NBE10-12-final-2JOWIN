import { useState, type FormEvent } from 'react'
import { Button, Dialog, Flex, Select, TextArea, TextField } from '@radix-ui/themes'
import { ApiError } from '../../../shared/api/client'
import { ErrorCallout, Field, ACTIVITY_CHANNELS, ACTIVITY_CHANNEL_LABEL, type ActivityChannel } from '../../../shared/ui'
import type { ActivityResponse, CreateActivityRequest } from '../../../shared/api/types'
import { SELECT_CONTENT } from '../../customer/constants'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** 넘기면 수정, 없으면 작성 — 수정은 작성자 본인만 (AC-04) */
  activity?: ActivityResponse
  loading: boolean
  error: unknown
  onSubmit: (body: CreateActivityRequest) => void
}

/** 상담 기록 작성·수정 (AC-01~04) — 수단(CALL·MEETING·EMAIL) · 내용 · 발생 시각 */
export function ActivityFormDialog({ open, onOpenChange, activity, loading, error, onSubmit }: Props) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content maxWidth="480px">
        <ActivityForm activity={activity} loading={loading} error={error} onSubmit={onSubmit} />
      </Dialog.Content>
    </Dialog.Root>
  )
}

/** ISO → datetime-local 입력값 (로컬 시각). 저장은 UTC ISO로 되돌린다 */
function toLocalInput(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function ActivityForm({ activity, loading, error, onSubmit }: Omit<Props, 'open' | 'onOpenChange'>) {
  const [channel, setChannel] = useState<ActivityChannel>(activity?.channel ?? 'CALL')
  const [content, setContent] = useState(activity?.content ?? '')
  const [occurredAt, setOccurredAt] = useState(() => toLocalInput(activity?.occurredAt ?? new Date().toISOString()))
  const apiError = error instanceof ApiError ? error : null

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault()
    if (!content.trim() || !occurredAt) return
    onSubmit({ channel, content: content.trim(), occurredAt: new Date(occurredAt).toISOString() })
  }

  return (
    <>
      <Dialog.Title>{activity ? '상담 기록 수정' : '상담 기록'}</Dialog.Title>
      <Dialog.Description size="2" color="gray">
        딜 타임라인과 고객사 이력에 함께 남습니다. 작성자 본인만 수정·삭제할 수 있습니다.
      </Dialog.Description>

      <form onSubmit={handleSubmit}>
        <Flex direction="column" gap="4" mt="4">
          <Flex gap="3">
            <Field label="수단" required error={apiError?.reasonOf('channel')} grow>
              <Select.Root value={channel} onValueChange={(v) => setChannel(v as ActivityChannel)} disabled={loading}>
                <Select.Trigger style={{ width: '100%' }} />
                <Select.Content {...SELECT_CONTENT}>
                  {ACTIVITY_CHANNELS.map((c) => (
                    <Select.Item key={c} value={c}>
                      {ACTIVITY_CHANNEL_LABEL[c]}
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </Field>
            <Field label="발생 시각" required error={apiError?.reasonOf('occurredAt')} grow>
              <TextField.Root type="datetime-local" value={occurredAt} onChange={(e) => setOccurredAt(e.target.value)} disabled={loading} />
            </Field>
          </Flex>
          <Field label="내용" required error={apiError?.reasonOf('content')}>
            <TextArea rows={4} placeholder="예: 방문 미팅 — 사양 협의, 예산 1,300만 선" value={content} onChange={(e) => setContent(e.target.value)} autoFocus disabled={loading} />
          </Field>

          {apiError && apiError.code !== 'VALIDATION_FAILED' && <ErrorCallout code={apiError.code} />}

          <Flex gap="3" justify="end" mt="2">
            <Dialog.Close>
              <Button type="button" variant="soft" color="gray" disabled={loading}>
                취소
              </Button>
            </Dialog.Close>
            <Button type="submit" loading={loading} disabled={!content.trim() || !occurredAt}>
              {activity ? '저장' : '기록'}
            </Button>
          </Flex>
        </Flex>
      </form>
    </>
  )
}
