import { HttpResponse } from 'msw'
import { ERROR_MESSAGES, type ErrorCode } from '../shared/api/errors'
import type { ErrorResponse, PageResponse } from '../shared/api/types'
import type { DealStage, NotificationSettingType, NotificationType, Role } from '../shared/ui/status'
import { date } from '../shared/lib/format'
import * as fx from './fixtures'

/**
 * 목 상태 저장소 — 모든 도메인 핸들러가 **같은 상태**를 읽고 쓴다.
 *
 * 견적을 발송하면 딜 단계가 오르고(Q-25), 주문으로 전환하면 딜이 성사되고(OD-06),
 * 대시보드는 그 결과를 집계한다. 도메인별 핸들러가 각자 배열을 들고 있으면 이 연쇄가 끊긴다.
 *
 * - 픽스처(`fixtures.ts`)는 읽기 전용 원본이고, 여기서 깊은 복사로 시작한다
 * - 새로고침하면 픽스처로 돌아간다 (메모리 상태)
 * - 응답 형태는 각 핸들러가 DTO에 맞춰 조립한다 — 저장소는 시드 테이블에 가깝다
 */

const clone = <T>(value: T): T => structuredClone(value)

export const db = {
  members: clone(fx.members),
  customers: clone(fx.customers).map((c) => ({ ...c, deleted: false })),
  contacts: new Map(Object.entries(clone(fx.contacts))),
  products: clone(fx.products),
  deals: clone(fx.deals).map((d) => ({ ...d, deleted: false })),
  quotes: clone(fx.quotes),
  quoteItems: new Map(Object.entries(clone(fx.quoteItems))),
  viewTokens: clone(fx.viewTokens),
  invitations: clone(fx.invitations),
  orders: clone(fx.orders),
  orderItems: new Map(Object.entries(clone(fx.orderItems))),
  activities: clone(fx.activities).map((a) => ({ ...a, deleted: false })),
  tasks: clone(fx.tasks),
  inquiries: clone(fx.inquiries),
  notifications: clone(fx.notifications),
  auditLogs: clone(fx.auditLogs),
  applications: clone(fx.applications),
  companies: clone(fx.companies),
  /** 채번 카운터 — global/sequence/DocumentSequence (PR #80). 발급은 이 행을 +1 하는 것뿐 */
  documentSequences: clone(fx.documentSequences),
  /** 메일 수신 설정 — 행 없으면 기본 ON (08 §A NotificationSettingResponse) */
  notificationSettings: new Map<string, { type: NotificationSettingType; emailEnabled: boolean }[]>(),
  /**
   * 자동 기록(AC-07) — 도메인 이벤트에서 파생되는 타임라인 항목. 시드에는 테이블이 없고
   * 실제로는 audit_log에서 만들어지므로, 목에서는 발송·열람·단계 이동 시점에 여기에 쌓는다.
   */
  autoActivities: [] as { id: string; dealId: string; content: string; authorMemberId: string | null; occurredAt: string }[],
}

// 시드의 발송·열람 이력을 자동 기록으로 미리 채운다 — 타임라인이 처음부터 비어 보이지 않게
for (const deal of db.deals) {
  db.autoActivities.push({ id: `auto-deal-${deal.id}`, dealId: deal.id, content: '딜이 생성되었습니다', authorMemberId: deal.assigneeMemberId, occurredAt: deal.createdAt })
}
for (const quote of db.quotes) {
  const deal = db.deals.find((d) => d.id === quote.dealId)
  if (quote.sentAt) db.autoActivities.push({ id: `auto-sent-${quote.id}`, dealId: quote.dealId, content: `견적을 발송했습니다 — ${quote.quoteNo}`, authorMemberId: deal?.assigneeMemberId ?? null, occurredAt: quote.sentAt })
  if (quote.firstViewedAt) db.autoActivities.push({ id: `auto-view-${quote.id}`, dealId: quote.dealId, content: `고객이 견적을 열람했습니다 — ${quote.quoteNo}`, authorMemberId: null, occurredAt: quote.firstViewedAt })
  if (quote.respondedAt) {
    const verb = quote.status === 'APPROVED' ? '승인' : '반려'
    db.autoActivities.push({ id: `auto-resp-${quote.id}`, dealId: quote.dealId, content: `고객이 견적을 ${verb}했습니다 — ${quote.quoteNo}`, authorMemberId: null, occurredAt: quote.respondedAt })
  }
}
for (const order of db.orders) {
  db.autoActivities.push({ id: `auto-order-${order.id}`, dealId: order.dealId, content: `주문으로 전환했습니다 — ${order.orderNo}`, authorMemberId: null, occurredAt: order.createdAt })
}

// ── 세션 (목) ────────────────────────────────────────────────────────────────

/** 마지막으로 로그인한 계정 — refresh·/me가 Bearer 없이 와도 이 계정으로 답한다 (새로고침 직후) */
let currentAccount = fx.demoAccounts[0]
let adminLoggedIn = false

export const session = {
  login(account: (typeof fx.demoAccounts)[number]) {
    currentAccount = account
  },
  /** Authorization 헤더의 목 토큰으로 계정을 찾는다. 모르면 마지막 로그인 계정 */
  accountOf(request: Request) {
    const token = request.headers.get('Authorization')?.replace(/^Bearer\s+/i, '')
    return fx.demoAccounts.find((a) => a.accessToken === token) ?? currentAccount
  },
  current: () => currentAccount,
  adminLogin: () => {
    adminLoggedIn = true
  },
  adminLogout: () => {
    adminLoggedIn = false
  },
  isAdminLoggedIn: () => adminLoggedIn,
}

/** 요청을 보낸 구성원 — AccessContext에 해당 */
export function currentMember(request: Request) {
  const account = session.accountOf(request)
  return db.members.find((m) => m.id === account.memberId) ?? db.members[0]
}

/** boundary/AccessScope — 영업 담당자는 본인 담당 Deal만 (OWNED_ONLY) */
export const canSee = (member: { id: string; role: Role }, deal: { assigneeMemberId: string }): boolean =>
  member.role === 'COMPANY_ADMIN' || deal.assigneeMemberId === member.id

/** 담당 스코프 안의 진행 중·종결 Deal 전부 (삭제 제외) */
export const visibleDeals = (member: { id: string; role: Role }) => db.deals.filter((d) => !d.deleted && canSee(member, d))

// ── 응답 도우미 ──────────────────────────────────────────────────────────────

const STATUS: Record<ErrorCode, number> = {
  VALIDATION_FAILED: 400, RESOURCE_NOT_FOUND: 404, FORBIDDEN: 403, INTERNAL_ERROR: 500,
  LOGIN_FAILED: 401, LOGIN_LOCKED: 429, REFRESH_TOKEN_NOT_ACTIVE: 401, RESET_TOKEN_NOT_ACTIVE: 409, CURRENT_PASSWORD_MISMATCH: 422,
  EMAIL_ALREADY_MEMBER: 422, APPLICATION_ALREADY_PENDING: 409, APPLICATION_ALREADY_DECIDED: 409, COMPANY_BUSINESS_NO_DUPLICATED: 409,
  INVITATION_ALREADY_PENDING: 409, INVITATION_NOT_PENDING: 409, LAST_ADMIN_PROTECTED: 422, MEMBER_INACTIVE_TRANSFER_REQUIRED: 422,
  CUSTOMER_HAS_ACTIVE_DEALS: 409, PRIMARY_CONTACT_REQUIRED: 422, CONTACT_HAS_QUOTES: 409,
  PRODUCT_NAME_DUPLICATED: 409, PRODUCT_DISCONTINUED: 409, ACTIVITY_NOT_AUTHOR: 404,
  DEAL_WON_REQUIRES_ORDER: 409, DEAL_ALREADY_WON: 409, DEAL_HAS_QUOTES: 409, DEAL_NOT_OPEN: 409, DEAL_NOT_LOST: 409, DEAL_NO_PREVIOUS_STAGE: 409,
  QUOTE_NOT_DRAFT: 409, QUOTE_EMPTY_ITEMS: 409, QUOTE_NOT_WITHDRAWABLE: 409, QUOTE_NOT_RESENDABLE: 409, QUOTE_VALID_UNTIL_PASSED: 409,
  QUOTE_DEAL_CLOSED: 409, QUOTE_NOT_RESPONDABLE: 409, CONTACT_NOT_IN_CUSTOMER: 409, STALE_VERSION: 409,
  QUOTE_NOT_APPROVED: 409, QUOTE_ALREADY_CONVERTED: 409,
  LINK_EXPIRED: 410, LINK_ALREADY_RESPONDED: 409, COMPANY_SUSPENDED: 409,
}

/** 공통 ErrorResponse — 문구·HTTP는 ErrorCode enum(07 부록)에서 온다. 핸들러가 문구를 적지 않는다 */
export function error(code: ErrorCode, fieldErrors: ErrorResponse['fieldErrors'] = []) {
  const body: ErrorResponse = { code, message: ERROR_MESSAGES[code], fieldErrors }
  return HttpResponse.json(body, { status: STATUS[code] })
}

export const notFound = () => error('RESOURCE_NOT_FOUND')
export const noContent = () => new HttpResponse(null, { status: 204 })

/** 목록 공통 파라미터 (Q-39) — 0-base · 기본 20 · 최대 100 */
export function paged<T>(items: T[], url: URL): PageResponse<T> {
  const page = Math.max(0, Number(url.searchParams.get('page') ?? 0))
  const size = Math.min(100, Math.max(1, Number(url.searchParams.get('size') ?? 20)))
  return {
    content: items.slice(page * size, page * size + size),
    page, size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
  }
}

/** version 불일치 → 409 STALE_VERSION (낙관적 락). 일치하면 +1 */
export function bumpVersion(entity: { version: number }, requested: number | undefined): boolean {
  if (requested === undefined || requested !== entity.version) return false
  entity.version += 1
  return true
}

// ── 조회 도우미 ──────────────────────────────────────────────────────────────

export const memberName = (id: string | null) => (id ? (db.members.find((m) => m.id === id)?.name ?? '') : '')
export const memberActive = (id: string) => db.members.find((m) => m.id === id)?.status === 'ACTIVE'
export const customerName = (id: string) => db.customers.find((c) => c.id === id)?.name ?? ''
export const contactsOf = (customerId: string) => db.contacts.get(customerId) ?? []
export const findDeal = (id: string) => db.deals.find((d) => d.id === id && !d.deleted)
export const findQuote = (id: string) => db.quotes.find((q) => q.id === id)
export const dealOfQuote = (quoteId: string) => {
  const quote = findQuote(quoteId)
  return quote ? findDeal(quote.dealId) : undefined
}
/** DL-18 — 성사 후 표시 금액 = 주문 합계. 주문이 없으면 null */
export function wonAmountOf(dealId: string): number | null {
  const sum = db.orders.filter((o) => o.dealId === dealId).reduce((acc, o) => acc + o.totalAmount, 0)
  return sum > 0 ? sum : null
}
/** 견적당 활성 링크는 최대 1개 (AP-03) */
export const activeTokenOf = (quoteId: string) => db.viewTokens.find((t) => t.quoteId === quoteId && t.status === 'ACTIVE')

// ── 변경 도우미 (도메인 간 연쇄) ─────────────────────────────────────────────

export const now = () => new Date().toISOString()
/** 오늘(KST) — 유효기간·마감 비교와 채번 연월은 한국 날짜 기준 (quote_view_token.expires_at이 KST 23:59:59) */
export const today = () => date(now())

/**
 * 채번 — backend `global/sequence/DocumentNumberService.next()`를 그대로 흉내낸다 (PR #80, 06 §document_sequence).
 *
 *  - 카운터는 회사·문서종류·연월(yyMM)마다 하나. 없으면 0으로 심고(`insertIfAbsent`) 다시 읽는다
 *  - 연월은 Asia/Seoul 기준 — UTC로 끊으면 월말 자정 무렵 발급이 지난 달 번호를 받는다
 *  - 순번은 세 자리로 채우되 1000번째부터는 자연히 늘어난다 (`%03d` → Q-2609-1000)
 *  - 시드의 last_seq를 이어받는다 — 2608 카운터가 16이면 다음 8월 번호는 017
 *  - 실제 서비스는 MANDATORY 전파라 호출자 트랜잭션 안에서만 부를 수 있고 롤백 시 번호도 되돌아간다.
 *    목에는 트랜잭션이 없으므로 **검증을 전부 통과한 뒤, 행을 실제로 만드는 자리에서만** 부른다 — 번호에 구멍을 내지 않기 위해
 */
export function nextDocNo(type: 'QUOTE' | 'ORDER', issuedOn = today()): string {
  const prefix = type === 'QUOTE' ? 'Q' : 'O'
  const yearMonth = issuedOn.slice(2, 4) + issuedOn.slice(5, 7)
  const companyId = db.companies[0].id
  let row = db.documentSequences.find((r) => r.companyId === companyId && r.docType === type && r.yearMonth === yearMonth)
  if (!row) {
    row = { id: crypto.randomUUID(), companyId, docType: type, yearMonth, lastSeq: 0 }
    db.documentSequences.push(row)
  }
  row.lastSeq += 1
  return `${prefix}-${yearMonth}-${String(row.lastSeq).padStart(3, '0')}`
}

/** 자동 기록 — 타임라인의 AUTO 항목 (AC-07) */
export function recordAuto(dealId: string, content: string, authorMemberId: string | null = null, occurredAt = now()) {
  db.autoActivities.push({ id: crypto.randomUUID(), dealId, content, authorMemberId, occurredAt })
}

/** 감사 로그 (AC-11) — 변경된 필드만 before/after */
export function recordAudit(input: {
  entityType: string; entityId: string; eventType: string
  actorType: 'MEMBER' | 'PLATFORM_ADMIN' | 'CUSTOMER_LINK' | 'SYSTEM'; actorId: string | null
  changes: Record<string, { before: unknown; after: unknown }>
}) {
  db.auditLogs.unshift({ id: crypto.randomUUID(), occurredAt: now(), ...input })
}

/**
 * 인앱 알림 (NT-03~05·10·12) — 수신자는 발송 시점의 유효한 담당자, 비활성이면 기업 관리자 (Q-26).
 * INQUIRY_RECEIVED는 담당 구성원 + 기업 관리자 모두에게 (NT-10).
 */
export function notify(dealId: string, type: NotificationType, message: string, refId: string) {
  const deal = db.deals.find((d) => d.id === dealId)
  if (!deal) return
  const admins = db.members.filter((m) => m.role === 'COMPANY_ADMIN' && m.status === 'ACTIVE').map((m) => m.id)
  const assignee = memberActive(deal.assigneeMemberId) ? [deal.assigneeMemberId] : admins
  const recipients = new Set(type === 'INQUIRY_RECEIVED' ? [...assignee, ...admins] : assignee)
  for (const recipientMemberId of recipients) {
    db.notifications.unshift({ id: crypto.randomUUID(), recipientMemberId, type, message, refType: 'QUOTE', refId, readAt: null, createdAt: now() })
  }
}

/** Deal 단계 시스템 전이 — 발송 자동 승급(Q-25)·주문 전환 자동 성사(OD-06) */
export function moveDealStage(dealId: string, to: DealStage, actorId: string | null) {
  const deal = findDeal(dealId)
  if (!deal || deal.stage === to) return
  const before = deal.stage
  deal.stage = to
  deal.version += 1
  recordAudit({ entityType: 'DEAL', entityId: deal.id, eventType: 'STAGE_MOVED', actorType: actorId ? 'MEMBER' : 'SYSTEM', actorId, changes: { stage: { before, after: to } } })
}

/** DL-10 효과 — 진행 중 견적 EXPIRED + 열람 링크 만료(DEAL_LOST) */
export function expireQuotesOfDeal(dealId: string) {
  for (const quote of db.quotes.filter((q) => q.dealId === dealId && (q.status === 'SENT' || q.status === 'VIEWED'))) {
    quote.status = 'EXPIRED'
    quote.version += 1
    const token = activeTokenOf(quote.id)
    if (token) {
      token.status = 'EXPIRED'
      token.expiredReason = 'DEAL_LOST'
    }
  }
}
