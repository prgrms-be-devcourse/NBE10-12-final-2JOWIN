import { Badge, Flex } from '@radix-ui/themes'
import {
  BellIcon, ChatBubbleIcon, CheckCircledIcon, ClockIcon, CrossCircledIcon, EnvelopeClosedIcon, EyeNoneIcon, EyeOpenIcon,
  ExclamationTriangleIcon, MobileIcon, PersonIcon, Pencil1Icon, PaperPlaneIcon, ResetIcon,
} from '@radix-ui/react-icons'
import type { ComponentProps, ReactNode } from 'react'
import { daysSince } from '../lib/format'
import {
  ACTIVITY_CHANNEL_LABEL, APPLICATION_STATUS_LABEL, COMPANY_STATUS_LABEL, DEAL_STAGE_LABEL, INVITATION_STATUS_LABEL,
  MEMBER_STATUS_LABEL, NOTIFICATION_TYPE_LABEL, PRODUCT_STATUS_LABEL, QUOTE_STATUS_LABEL, ROLE_LABEL,
  type ActivityChannel, type ApplicationStatus, type CompanyStatus, type DealStage, type InvitationStatus,
  type MemberStatus, type NotificationType, type ProductStatus, type QuoteStatus, type Role,
} from './status'

/**
 * 상태 표시는 전부 Badge (10-screen-design.md §6.1).
 *
 * 색만으로 정보를 전달하지 않는다 (§2.6) — 색 + 아이콘 + 텍스트 셋을 항상 함께 낸다.
 * 색각 이상 사용자에게 색은 없는 정보이고, 흑백 인쇄되는 견적서에서도 마찬가지다.
 *
 * 색의 뜻은 하나씩만 (§2.3): blue=진행 · amber=봐야 할 것 · green=성사 · red=늦은 것 · gray=중립
 */

type Color = ComponentProps<typeof Badge>['color']
type Size = ComponentProps<typeof Badge>['size']

const DEAL: Record<DealStage, { color: Color; icon?: ReactNode }> = {
  LEAD: { color: 'blue' },
  CONSULT: { color: 'blue' },
  QUOTE: { color: 'blue' },
  NEGOTIATION: { color: 'blue' },
  WON: { color: 'green', icon: <CheckCircledIcon /> },
  LOST: { color: 'gray', icon: <CrossCircledIcon /> },
}

const QUOTE: Record<QuoteStatus, { color: Color; icon: ReactNode }> = {
  DRAFT: { color: 'gray', icon: <Pencil1Icon /> },
  SENT: { color: 'blue', icon: <PaperPlaneIcon /> },
  VIEWED: { color: 'amber', icon: <EyeOpenIcon /> },
  APPROVED: { color: 'green', icon: <CheckCircledIcon /> },
  REJECTED: { color: 'gray', icon: <CrossCircledIcon /> },
  WITHDRAWN: { color: 'gray', icon: <ResetIcon /> },
  EXPIRED: { color: 'gray', icon: <ClockIcon /> },
}

/** 딜 단계 — 딜 보드의 현재 단계만 current로 강조한다 (§6.1) */
export function DealStageBadge({ stage, current = false, size }: { stage: DealStage; current?: boolean; size?: Size }) {
  const { color, icon } = DEAL[stage]
  return (
    <Badge color={color} variant={current ? 'solid' : 'soft'} radius="full" size={size}>
      {icon}
      {DEAL_STAGE_LABEL[stage]}
    </Badge>
  )
}

/** 견적 상태 7종 */
export function QuoteStatusBadge({ status, size }: { status: QuoteStatus; size?: Size }) {
  const { color, icon } = QUOTE[status]
  return (
    <Badge color={color} variant="soft" radius="full" size={size}>
      {icon}
      {QUOTE_STATUS_LABEL[status]}
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

/** 상품 — 판매 중 gray(중립) · 판매 중지 red 아니라 gray: 늦은 것이 아니라 그냥 없는 것 */
export function ProductStatusBadge({ status }: { status: ProductStatus }) {
  return (
    <Badge color={status === 'ACTIVE' ? 'green' : 'gray'} variant="soft" radius="full">
      {status === 'ACTIVE' ? <CheckCircledIcon /> : <CrossCircledIcon />}
      {PRODUCT_STATUS_LABEL[status]}
    </Badge>
  )
}

/** 구성원 활성/비활성 */
export function MemberStatusBadge({ status }: { status: MemberStatus }) {
  return (
    <Badge color={status === 'ACTIVE' ? 'green' : 'gray'} variant="soft" radius="full">
      {status === 'ACTIVE' ? <CheckCircledIcon /> : <CrossCircledIcon />}
      {MEMBER_STATUS_LABEL[status]}
    </Badge>
  )
}

const INVITATION: Record<InvitationStatus, { color: Color; icon: ReactNode }> = {
  PENDING: { color: 'blue', icon: <PaperPlaneIcon /> },
  ACCEPTED: { color: 'green', icon: <CheckCircledIcon /> },
  CANCELED: { color: 'gray', icon: <CrossCircledIcon /> },
  EXPIRED: { color: 'gray', icon: <ClockIcon /> },
}
export function InvitationStatusBadge({ status }: { status: InvitationStatus }) {
  const { color, icon } = INVITATION[status]
  return (
    <Badge color={color} variant="soft" radius="full">
      {icon}
      {INVITATION_STATUS_LABEL[status]}
    </Badge>
  )
}

const APPLICATION: Record<ApplicationStatus, { color: Color; icon: ReactNode }> = {
  PENDING: { color: 'amber', icon: <ClockIcon /> },
  APPROVED: { color: 'green', icon: <CheckCircledIcon /> },
  REJECTED: { color: 'gray', icon: <CrossCircledIcon /> },
}
export function ApplicationStatusBadge({ status }: { status: ApplicationStatus }) {
  const { color, icon } = APPLICATION[status]
  return (
    <Badge color={color} variant="soft" radius="full">
      {icon}
      {APPLICATION_STATUS_LABEL[status]}
    </Badge>
  )
}

/** 회사 — 정지는 되돌릴 수 있는 운영 조치라 red가 아니라 amber */
export function CompanyStatusBadge({ status }: { status: CompanyStatus }) {
  return (
    <Badge color={status === 'ACTIVE' ? 'green' : 'amber'} variant="soft" radius="full">
      {status === 'ACTIVE' ? <CheckCircledIcon /> : <ExclamationTriangleIcon />}
      {COMPANY_STATUS_LABEL[status]}
    </Badge>
  )
}

/** 역할 — 관리자 blue · 영업 gray */
export function RoleBadge({ role, size }: { role: Role; size?: Size }) {
  return (
    <Badge color={role === 'COMPANY_ADMIN' ? 'blue' : 'gray'} variant="soft" radius="full" size={size}>
      <PersonIcon />
      {ROLE_LABEL[role]}
    </Badge>
  )
}

const NOTIFICATION: Record<NotificationType, { color: Color; icon: ReactNode }> = {
  QUOTE_VIEWED: { color: 'amber', icon: <EyeOpenIcon /> },
  QUOTE_APPROVED: { color: 'green', icon: <CheckCircledIcon /> },
  QUOTE_REJECTED: { color: 'gray', icon: <CrossCircledIcon /> },
  REMIND_NO_RESPONSE: { color: 'amber', icon: <BellIcon /> },
  INQUIRY_RECEIVED: { color: 'blue', icon: <ChatBubbleIcon /> },
  EMAIL_FAILED: { color: 'red', icon: <ExclamationTriangleIcon /> },
}
/** 알림 종류 — 최근 활동·알림 목록에서 종류를 앞세워 훑기 쉽게 (10 §5.1) */
export function NotificationTypeBadge({ type }: { type: NotificationType }) {
  const { color, icon } = NOTIFICATION[type]
  return (
    <Badge color={color} variant="soft" radius="full">
      {icon}
      {NOTIFICATION_TYPE_LABEL[type]}
    </Badge>
  )
}

const CHANNEL: Record<ActivityChannel, ReactNode> = {
  CALL: <MobileIcon />,
  MEETING: <PersonIcon />,
  EMAIL: <EnvelopeClosedIcon />,
}
/** 상담 수단 (AC-02) — 뜻 없는 분류라 slate */
export function ActivityChannelBadge({ channel }: { channel: ActivityChannel }) {
  return (
    <Badge color="gray" variant="soft" radius="full">
      {CHANNEL[channel]}
      {ACTIVITY_CHANNEL_LABEL[channel]}
    </Badge>
  )
}
