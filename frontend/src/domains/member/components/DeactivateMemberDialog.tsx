import { useState } from 'react'
import { AlertDialog, Avatar, Button, Callout, Flex, Select, Text } from '@radix-ui/themes'
import { ExclamationTriangleIcon } from '@radix-ui/react-icons'
import { ApiError } from '../../../shared/api/client'
import { ErrorCallout, Field } from '../../../shared/ui'
import type { MemberOptionResponse, MemberResponse } from '../../../shared/api/types'
import { SELECT_CONTENT } from '../../customer/constants'

interface Props {
  member: MemberResponse | null
  onOpenChange: (open: boolean) => void
  /** 이관 선택지 — 같은 회사의 활성 구성원 (본인은 호출부에서 뺀다) */
  options: MemberOptionResponse[]
  loading: boolean
  error: unknown
  onConfirm: (transferToMemberId: string | null) => void
}

/**
 * 구성원 비활성화 — 이관 강제를 화면으로 (10 §5.8 · MB-09·10·12·14 · Q-29).
 *
 * `AlertDialog`: 세션 종료와 딜 이관이 즉시 일어나 되돌릴 수 없다 (§2.5).
 * 담당 Deal 수를 알려주는 API가 없어(딜 목록은 C 도메인) 2단계로 간다 —
 * 먼저 이관 대상 없이 호출하고, 서버가 422 MEMBER_INACTIVE_TRANSFER_REQUIRED를 돌려주면
 * 그때 이관 대상 Select를 펼친다. 담당 Deal이 0건이면 첫 호출로 끝난다 (MB-14).
 *
 * 422를 한 번 받으면 다이얼로그가 닫힐 때까지 이관 단계로 고정한다 — 2차 호출이 시작되는 순간 mutation error가
 * 비워지므로 error에서 바로 파생하면 로딩 중에 Select가 사라지고, 2차가 다른 이유로 실패하면 1단계로 되돌아간다.
 */
export function DeactivateMemberDialog({ member, onOpenChange, options, loading, error, onConfirm }: Props) {
  const [transferTo, setTransferTo] = useState<string>('')
  const [transferStep, setTransferStep] = useState(false)
  const apiError = error instanceof ApiError ? error : null
  if (apiError?.code === 'MEMBER_INACTIVE_TRANSFER_REQUIRED' && !transferStep) setTransferStep(true)
  const needsTransfer = transferStep
  const candidates = options.filter((o) => o.id !== member?.id)

  return (
    <AlertDialog.Root
      open={member !== null}
      onOpenChange={(open) => {
        if (!open) {
          setTransferTo('')
          setTransferStep(false)
        }
        onOpenChange(open)
      }}
    >
      <AlertDialog.Content maxWidth="440px" onOpenAutoFocus={(e) => e.preventDefault()}>
        <AlertDialog.Title>{member?.name} 님을 비활성화합니다</AlertDialog.Title>

        {needsTransfer ? (
          <>
            <Callout.Root color="amber" size="1" mt="2">
              <Callout.Icon>
                <ExclamationTriangleIcon />
              </Callout.Icon>
              <Callout.Text>담당 중인 Deal이 있습니다. 이관받을 구성원을 지정해야 비활성화할 수 있습니다.</Callout.Text>
            </Callout.Root>
            <Flex direction="column" gap="1" mt="4">
              <Field label="이관 대상" required hint="같은 회사의 활성 구성원만 선택할 수 있습니다.">
                <Select.Root value={transferTo} onValueChange={setTransferTo} disabled={loading}>
                  <Select.Trigger placeholder="구성원 선택" style={{ width: '100%' }} />
                  <Select.Content {...SELECT_CONTENT}>
                    {candidates.map((o) => (
                      <Select.Item key={o.id} value={o.id}>
                        <Flex align="center" gap="2">
                          <Avatar size="1" radius="full" fallback={o.name.slice(0, 1)} />
                          {o.name}
                        </Flex>
                      </Select.Item>
                    ))}
                  </Select.Content>
                </Select.Root>
              </Field>
            </Flex>
          </>
        ) : (
          <AlertDialog.Description size="2" color="gray">
            담당 중인 Deal이 있으면 이관 대상을 먼저 지정하게 됩니다.
          </AlertDialog.Description>
        )}

        <Flex direction="column" gap="1" mt="4">
          <Text size="2" color="gray">· Deal과 딸린 할 일이 함께 이동합니다</Text>
          <Text size="2" color="gray">· 로그인 세션이 즉시 종료됩니다</Text>
          <Text size="2" color="gray">· 작성한 상담 기록은 이름 그대로 남습니다</Text>
        </Flex>

        {apiError && apiError.code !== 'MEMBER_INACTIVE_TRANSFER_REQUIRED' && <ErrorCallout code={apiError.code} />}

        <Flex gap="3" mt="4" justify="end">
          <AlertDialog.Cancel>
            <Button variant="soft" color="gray" disabled={loading}>
              취소
            </Button>
          </AlertDialog.Cancel>
          <Button
            color="red"
            loading={loading}
            disabled={needsTransfer && !transferTo}
            onClick={() => onConfirm(needsTransfer ? transferTo : null)}
          >
            비활성화
          </Button>
        </Flex>
      </AlertDialog.Content>
    </AlertDialog.Root>
  )
}
