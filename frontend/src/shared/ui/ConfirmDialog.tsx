import { AlertDialog, Button, Flex } from '@radix-ui/themes'
import type { ComponentProps, ReactNode } from 'react'

type ButtonColor = ComponentProps<typeof Button>['color']

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  /** 무슨 일이 일어나는지 — 되돌릴 수 없다는 사실을 여기서 밝힌다 */
  description?: ReactNode
  /** 확인 전에 보여줄 요약·입력 (금액 재확인, 승인자 이름 등) */
  children?: ReactNode
  confirmLabel: string
  confirmColor?: ButtonColor
  cancelLabel?: string
  onConfirm: () => void
  loading?: boolean
}

/**
 * 되돌릴 수 없는 행동의 확인 — **`AlertDialog`** (10-screen-design.md §2.5).
 *
 * `Dialog`가 아닌 이유: ESC·바깥 클릭으로 닫히면 안 된다. 승인·반려·구성원 비활성화가
 * 여기에 해당한다(견적 발송은 취소해도 잃는 것이 없어 `Dialog`를 쓴다).
 *
 * **확인 버튼에 자동 포커스를 두지 않는다** — 엔터 연타로 수백만 원 거래가 확정되지 않게.
 */
export function ConfirmDialog({
  open, onOpenChange, title, description, children,
  confirmLabel, confirmColor = 'indigo', cancelLabel = '취소', onConfirm, loading = false,
}: Props) {
  return (
    <AlertDialog.Root open={open} onOpenChange={onOpenChange}>
      <AlertDialog.Content
        maxWidth="440px"
        onOpenAutoFocus={(e) => e.preventDefault()}
      >
        <AlertDialog.Title>{title}</AlertDialog.Title>
        {description && (
          <AlertDialog.Description size="2">{description}</AlertDialog.Description>
        )}
        {children}
        <Flex gap="3" mt="4" justify="end">
          <AlertDialog.Cancel>
            <Button variant="soft" color="gray" disabled={loading}>
              {cancelLabel}
            </Button>
          </AlertDialog.Cancel>
          <Button color={confirmColor} onClick={onConfirm} loading={loading}>
            {confirmLabel}
          </Button>
        </Flex>
      </AlertDialog.Content>
    </AlertDialog.Root>
  )
}
