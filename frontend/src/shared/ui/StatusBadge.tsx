import { Badge, Flex } from '@radix-ui/themes'
import {
  CheckCircledIcon, ClockIcon, CrossCircledIcon, EyeNoneIcon, EyeOpenIcon,
  Pencil1Icon, PaperPlaneIcon, ResetIcon,
} from '@radix-ui/react-icons'
import type { ComponentProps, ReactNode } from 'react'
import { daysSince } from '../lib/format'
import type { DealStage, QuoteStatus } from './status'

/**
 * 상태 표시는 전부 Badge (10-screen-design.md §6.1).
 *
 * **색만으로 정보를 전달하지 않는다** (§2.6) — 색 + 아이콘 + 텍스트 셋을 항상 함께 낸다.
 * 색각 이상 사용자에게 색은 없는 정보이고, 흑백 인쇄되는 견적서에서도 마찬가지다.
 *
 * 색의 뜻은 하나씩만 (§2.3): indigo=진행 · amber=봐야 할 것 · green=성사 · red=늦은 것 · gray=중립
 */

type Color = ComponentProps<typeof Badge>['color']



const DEAL: Record<DealStage, { label: string; color: Color; icon?: ReactNode }> = {
  LEAD: { label: '리드', color: 'indigo' },
  CONSULT: { label: '상담', color: 'indigo' },
  QUOTE: { label: '견적', color: 'indigo' },
  NEGOTIATION: { label: '협상', color: 'indigo' },
  WON: { label: '성사', color: 'green', icon: <CheckCircledIcon /> },
  LOST: { label: '실패', color: 'gray', icon: <CrossCircledIcon /> },
}

const QUOTE: Record<QuoteStatus, { label: string; color: Color; icon: ReactNode }> = {
  DRAFT: { label: '작성 중', color: 'gray', icon: <Pencil1Icon /> },
  SENT: { label: '발송됨', color: 'indigo', icon: <PaperPlaneIcon /> },
  VIEWED: { label: '열람됨', color: 'amber', icon: <EyeOpenIcon /> },
  APPROVED: { label: '승인됨', color: 'green', icon: <CheckCircledIcon /> },
  REJECTED: { label: '반려됨', color: 'gray', icon: <CrossCircledIcon /> },
  WITHDRAWN: { label: '회수됨', color: 'gray', icon: <ResetIcon /> },
  EXPIRED: { label: '기간 만료', color: 'gray', icon: <ClockIcon /> },
}

/** 딜 단계 — 딜 보드의 현재 단계만 current로 강조한다 (§6.1) */
export function DealStageBadge({ stage, current = false }: { stage: DealStage; current?: boolean }) {
  const { label, color, icon } = DEAL[stage]
  return (
    <Badge color={color} variant={current ? 'solid' : 'soft'} radius="full">
      {icon}
      {label}
    </Badge>
  )
}

/** 견적 상태 7종 */
export function QuoteStatusBadge({ status }: { status: QuoteStatus }) {
  const { label, color, icon } = QUOTE[status]
  return (
    <Badge color={color} variant="soft" radius="full">
      {icon}
      {label}
    </Badge>
  )
}

/**
 * 열람 여부 — `firstViewedAt`이 null이면 미열람 (GAP-08).
 *
 * "안 봤다"와 "봤는데 답이 없다"는 담당자가 취할 행동이 다르다 (AP-06) —
 * 그래서 대시보드 응답 대기 목록에서 이 배지가 가장 중요한 정보다.
 */
export function ViewedBadge({
  firstViewedAt, sentAt,
}: { firstViewedAt: string | null; sentAt: string | null }) {
  if (firstViewedAt) {
    return (
      <Badge color="amber" variant="soft" radius="full">
        <EyeOpenIcon />
        열람 {daysSince(firstViewedAt)}일 전
      </Badge>
    )
  }
  return (
    <Badge color="gray" variant="soft" radius="full">
      <EyeNoneIcon />
      미열람{sentAt ? ` ${daysSince(sentAt)}일` : ''}
    </Badge>
  )
}

/** 자동 기록 표시 — 사람이 쓴 것과 구별한다 (§6.1) */
export function AutoBadge() {
  return (
    <Badge color="gray" variant="soft" radius="full">
      자동
    </Badge>
  )
}

/** 남은 기간 — 지난 것은 red, 남은 것은 amber (§2.3: amber=확인 필요, red=이미 늦음) */
export function RemainingBadge({ until }: { until: string }) {
  const days = -daysSince(until)
  return (
    <Flex asChild align="center">
      <Badge color={days < 0 ? 'red' : 'amber'} variant="soft" radius="full">
        <ClockIcon />
        {days > 0 ? `${days}일 남음` : days === 0 ? '오늘 마감' : `${-days}일 지남`}
      </Badge>
    </Flex>
  )
}
