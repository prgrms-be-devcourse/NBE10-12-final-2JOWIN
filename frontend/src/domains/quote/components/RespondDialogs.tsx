import { useState } from 'react'
import {
  AlertDialog, Box, Button, Callout, Card, Dialog, Flex, Text, TextArea, TextField,
} from '@radix-ui/themes'
import { ExclamationTriangleIcon } from '@radix-ui/react-icons'
import { codeOf } from '../../../shared/api/client'
import { ErrorCallout, Money } from '../../../shared/ui'

/**
 * 고객 응답 모달 3종 (10-screen-design.md §5.7 · §2.5).
 *
 * 되돌릴 수 있는가로 컴포넌트를 가른다.
 *  - 승인·반려 → `AlertDialog` — ESC·바깥 클릭으로 닫히지 않는다
 *  - 문의 → `Dialog` — 취소해도 잃는 것이 없다
 *
 * 응답자 이름·직책은 검증 없는 자기 신고다 (AP-19, Q-44). 계정 없는 고객이
 * 링크만으로 응답하므로 시스템은 신원을 확인할 방법이 없고, 화면도 그렇게 안내한다.
 *
 * 길이 제한은 서버 DTO(approval/dto)와 같다 — 이름·직책 50 · 반려 사유 500(reject_reason VARCHAR(500)) · 문의 1000.
 * 넘기면 서버가 400 VALIDATION_FAILED라 입력 단계에서 막고 남은 글자 수를 보여준다.
 * 실패(409 등)는 모달 안에 보여준다 — 모달을 닫지 않아야 고객이 입력한 사유를 잃지 않는다.
 */

const RESPONSE_MAX = { responder: 50, reason: 500, inquiry: 1000 } as const

/** 서버 실패를 모달 안에 — 링크 만료(410)·응답 완료·정지 회사(409)는 코드 문구 그대로 */
function SubmitError({ error }: { error: unknown }) {
  return error ? <ErrorCallout code={codeOf(error)} /> : null
}

interface ResponderState {
  name: string
  title: string
}

const EMPTY: ResponderState = { name: '', title: '' }

/** 이름(필수)·직책(선택) 입력 — 승인·반려가 공유한다 */
function ResponderFields({
  value, onChange, disabled,
}: { value: ResponderState; onChange: (next: ResponderState) => void; disabled?: boolean }) {
  return (
    <Flex direction="column" gap="2" mt="3">
      <Text size="2" weight="medium">
        응답자 정보
      </Text>
      <Flex gap="2">
        <Box flexGrow="1">
          <Text as="label" htmlFor="responder-name" size="1" color="gray">
            이름 (필수)
          </Text>
          <TextField.Root
            id="responder-name"
            mt="1"
            value={value.name}
            maxLength={RESPONSE_MAX.responder}
            disabled={disabled}
            onChange={(e) => onChange({ ...value, name: e.target.value })}
          />
        </Box>
        <Box flexGrow="1">
          <Text as="label" htmlFor="responder-title" size="1" color="gray">
            직책 (선택)
          </Text>
          <TextField.Root
            id="responder-title"
            mt="1"
            value={value.title}
            maxLength={RESPONSE_MAX.responder}
            disabled={disabled}
            onChange={(e) => onChange({ ...value, title: e.target.value })}
          />
        </Box>
      </Flex>
      <Text size="1" color="gray">
        직접 입력하신 정보로 기록됩니다.
      </Text>
    </Flex>
  )
}

// ── 승인 ────────────────────────────────────────────────────────────────────

interface ApproveProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  companyName: string
  quoteNo: string
  totalAmount: number
  vatExcluded: boolean
  loading?: boolean
  error?: unknown
  onConfirm: (responder: ResponderState) => void
}

export function ApproveDialog({
  open, onOpenChange, companyName, quoteNo, totalAmount, vatExcluded, loading, error, onConfirm,
}: ApproveProps) {
  const [responder, setResponder] = useState(EMPTY)

  return (
    <AlertDialog.Root open={open} onOpenChange={onOpenChange}>
      <AlertDialog.Content maxWidth="440px" onOpenAutoFocus={(e) => e.preventDefault()}>
        <AlertDialog.Title>견적을 승인하시겠습니까?</AlertDialog.Title>

        {/* 금액을 한 번 더 보여준다 — 오클릭으로 수백만 원 거래가 확정되지 않도록 */}
        <Card variant="surface" mt="3">
          <Text as="div" size="2" color="gray">
            {companyName} · {quoteNo}
          </Text>
          <Money value={totalAmount} unit size="6" weight="bold" />
          {vatExcluded && (
            <Text as="div" size="1" color="gray">
              부가세 별도
            </Text>
          )}
        </Card>

        <ResponderFields value={responder} onChange={setResponder} disabled={loading} />

        <Callout.Root color="amber" mt="3" size="1">
          <Callout.Icon>
            <ExclamationTriangleIcon />
          </Callout.Icon>
          <Callout.Text>승인하면 이 링크로는 다시 응답할 수 없습니다.</Callout.Text>
        </Callout.Root>
        <SubmitError error={error} />

        <Flex gap="3" mt="4" justify="end">
          <AlertDialog.Cancel>
            <Button variant="soft" color="gray" disabled={loading}>
              취소
            </Button>
          </AlertDialog.Cancel>
          <Button
            color="green"
            loading={loading}
            disabled={!responder.name.trim()}
            onClick={() => onConfirm(responder)}
          >
            승인합니다
          </Button>
        </Flex>
      </AlertDialog.Content>
    </AlertDialog.Root>
  )
}

// ── 반려 ────────────────────────────────────────────────────────────────────

interface RejectProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  loading?: boolean
  error?: unknown
  onConfirm: (responder: ResponderState, reason: string) => void
}

export function RejectDialog({ open, onOpenChange, loading, error, onConfirm }: RejectProps) {
  const [responder, setResponder] = useState(EMPTY)
  const [reason, setReason] = useState('')

  return (
    <AlertDialog.Root open={open} onOpenChange={onOpenChange}>
      <AlertDialog.Content maxWidth="440px" onOpenAutoFocus={(e) => e.preventDefault()}>
        <AlertDialog.Title>견적을 반려하시겠습니까?</AlertDialog.Title>
        <AlertDialog.Description size="2" color="gray">
          사유는 담당자에게 그대로 전달됩니다.
        </AlertDialog.Description>

        <Box mt="3">
          <Text as="label" htmlFor="reject-reason" size="2" weight="medium">
            반려 사유 (필수)
          </Text>
          <TextArea
            id="reject-reason"
            mt="1"
            rows={3}
            placeholder="예: 예산 범위를 초과합니다."
            value={reason}
            maxLength={RESPONSE_MAX.reason}
            disabled={loading}
            onChange={(e) => setReason(e.target.value)}
          />
          <Text as="div" size="1" color="gray" align="right" mt="1">
            {reason.length}/{RESPONSE_MAX.reason}
          </Text>
        </Box>

        <ResponderFields value={responder} onChange={setResponder} disabled={loading} />

        <Callout.Root color="amber" mt="3" size="1">
          <Callout.Icon>
            <ExclamationTriangleIcon />
          </Callout.Icon>
          <Callout.Text>반려하면 이 링크로는 다시 응답할 수 없습니다.</Callout.Text>
        </Callout.Root>
        <SubmitError error={error} />

        <Flex gap="3" mt="4" justify="end">
          <AlertDialog.Cancel>
            <Button variant="soft" color="gray" disabled={loading}>
              취소
            </Button>
          </AlertDialog.Cancel>
          <Button
            color="red"
            loading={loading}
            disabled={!responder.name.trim() || !reason.trim()}
            onClick={() => onConfirm(responder, reason)}
          >
            반려합니다
          </Button>
        </Flex>
      </AlertDialog.Content>
    </AlertDialog.Root>
  )
}

// ── 문의 ────────────────────────────────────────────────────────────────────

interface InquiryProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  loading?: boolean
  error?: unknown
  onSubmit: (content: string) => void
}

/**
 * 문의 남기기 (AP-15, Q-20) — `Dialog`. 되돌릴 수 있으므로 ESC로 닫힌다.
 *
 * 답변이 이 화면으로 오지 않는다는 것을 고객이 알아야 한다 (GAP-02) —
 * v1에는 문의 조회 화면이 없고(Q-42), 담당자는 알림으로만 통지받는다.
 */
export function InquiryDialog({ open, onOpenChange, loading, error, onSubmit }: InquiryProps) {
  const [content, setContent] = useState('')

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content maxWidth="440px">
        <Dialog.Title>문의 남기기</Dialog.Title>
        <Dialog.Description size="2" color="gray">
          담당자에게 전달되며, <b>답변은 이메일로 회신드립니다.</b>
        </Dialog.Description>

        <TextArea
          mt="3"
          rows={4}
          placeholder="예: 납기를 앞당길 수 있을까요?"
          value={content}
          maxLength={RESPONSE_MAX.inquiry}
          disabled={loading}
          onChange={(e) => setContent(e.target.value)}
        />
        <Text as="div" size="1" color="gray" align="right" mt="1">
          {content.length}/{RESPONSE_MAX.inquiry}
        </Text>
        <SubmitError error={error} />

        <Flex gap="3" mt="4" justify="end">
          <Dialog.Close>
            <Button variant="soft" color="gray" disabled={loading}>
              취소
            </Button>
          </Dialog.Close>
          <Button loading={loading} disabled={!content.trim()} onClick={() => onSubmit(content)}>
            보내기
          </Button>
        </Flex>
      </Dialog.Content>
    </Dialog.Root>
  )
}
