import { delay, http, HttpResponse } from 'msw'
import { currentMember, db, error, memberActive, memberName, today, visibleDeals } from '../store'
import type { DashboardPerformanceResponse, DashboardSummaryResponse } from '../../shared/api/types'
import { DEAL_STAGES, OPEN_DEAL_STAGES, isOpenStage, type DealStage } from '../../shared/ui/status'

/**
 * 대시보드 목 — dashboard/dto · 07 §D (DB-01~08).
 *
 * 값을 적어두지 않고 저장소(deals·quotes·orders·tasks·activities)에서 **계산**한다 —
 * 견적을 발송하거나 주문으로 전환하면 대시보드가 따라 바뀌어야 목이 시연과 같아진다.
 * 담당 스코프: 영업 담당자는 본인 담당 Deal 기준 집계 (🔶, DB-01~05).
 */

const thisMonth = () => today().slice(0, 7) // KST — 화면의 기본 월과 같은 기준
const monthRange = (month: string) => {
  const [y, m] = month.split('-').map(Number)
  const from = `${month}-01`
  const to = new Date(Date.UTC(y, m, 0)).toISOString().slice(0, 10) // 그 달 마지막 날
  return { from, to }
}
const inRange = (iso: string, from: string, to: string) => {
  const day = iso.slice(0, 10)
  return day >= from && day <= to
}
/** ActivityQuery.RecentActivitySummary.summary 규칙 — 줄바꿈·연속 공백은 공백 하나, 80자 넘으면 자르고 … */
const SUMMARY_MAX_LENGTH = 80
const toSummary = (content: string) => {
  const flat = content.replace(/\s+/g, ' ').trim()
  return flat.length <= SUMMARY_MAX_LENGTH ? flat : `${flat.slice(0, SUMMARY_MAX_LENGTH)}…`
}

export const dashboardHandlers = [
  // 단계별 현황 · 이달 성사 · 응답 대기 · 후속 필요 · 최근 활동 (DB-01~05)
  http.get('/api/v1/dashboard/summary', async ({ request }) => {
    await delay(200)
    const member = currentMember(request)
    const url = new URL(request.url)
    const month = url.searchParams.get('month') || thisMonth()
    if (!/^\d{4}-\d{2}$/.test(month)) return error('VALIDATION_FAILED', [{ field: 'month', reason: 'YYYY-MM 형식이어야 합니다.' }])
    const { from, to } = monthRange(month)

    const deals = visibleDeals(member)
    const dealIds = new Set(deals.map((d) => d.id))
    const dealTitle = (id: string) => deals.find((d) => d.id === id)?.title ?? ''
    const customerOf = (id: string) => deals.find((d) => d.id === id)?.customerName ?? ''

    // DB-01 — 진행 단계(리드~협상)만. WON은 이달 성사로, LOST는 제외 (v1.6.1)
    const pipeline = OPEN_DEAL_STAGES.map((stage) => {
      const rows = deals.filter((d) => d.stage === stage)
      return { stage, count: rows.length, expectedAmountSum: rows.reduce((sum, d) => sum + (d.expectedAmount ?? 0), 0) }
    })

    // DB-02 — 이달 성사 = 주문 합계 (DL-18). 주문 생성일이 그 달인 것
    const monthOrders = db.orders.filter((o) => dealIds.has(o.dealId) && inRange(o.createdAt, from, to))

    // DB-03 — 고객 응답 대기: 발송됨·열람됨 견적 (firstViewedAt null = 미열람, GAP-08)
    const waitingQuotes = db.quotes
      .filter((q) => dealIds.has(q.dealId) && (q.status === 'SENT' || q.status === 'VIEWED') && q.sentAt)
      .sort((a, b) => (b.sentAt ?? '').localeCompare(a.sentAt ?? ''))
      .map((q) => ({ quoteId: q.id, quoteNo: q.quoteNo, customerName: customerOf(q.dealId), sentAt: q.sentAt!, firstViewedAt: q.firstViewedAt, validUntil: q.validUntil }))

    // DB-05 — 후속 필요: 담당 Deal의 미완료 할 일 (Q-29)
    const followUps = db.tasks
      .filter((t) => dealIds.has(t.dealId) && t.doneAt === null)
      .sort((a, b) => a.dueDate.localeCompare(b.dueDate))
      .map((t) => ({ taskId: t.id, dealId: t.dealId, dealTitle: dealTitle(t.dealId), content: t.content, dueDate: t.dueDate }))

    // DB-04 — 최근 활동: activity 단일 원천 최근 10건 (ActivityQuery 계약 · 10 §5.1 v2.0.1).
    // 자동 기록(autoActivities)은 합치지 않는다 — 실 API가 안 주므로 목이 합치면 건수가 어긋난다 (PR #178).
    // summary는 계약대로 공백 정리 + 80자 절단 (ActivityQuery.SUMMARY_MAX_LENGTH)
    const recentActivities = db.activities
      .filter((a) => dealIds.has(a.dealId) && !a.deleted)
      .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
      .slice(0, 10)
      .map((a) => ({ dealId: a.dealId, dealTitle: dealTitle(a.dealId), summary: toSummary(a.content), occurredAt: a.occurredAt }))

    const body: DashboardSummaryResponse = {
      pipeline,
      monthWonAmount: monthOrders.reduce((sum, o) => sum + o.totalAmount, 0),
      monthWonCount: monthOrders.length,
      waitingQuotes,
      followUps,
      recentActivities,
    }
    return HttpResponse.json(body)
  }),

  // 담당자별 실적 · 단계별 전환율 — 기업 관리자 전용 (DB-06~08)
  http.get('/api/v1/dashboard/performance', async ({ request }) => {
    await delay(200)
    const member = currentMember(request)
    if (member.role !== 'COMPANY_ADMIN') return error('FORBIDDEN')
    const url = new URL(request.url)
    const range = monthRange(thisMonth())
    const from = url.searchParams.get('from') || range.from
    const to = url.searchParams.get('to') || range.to

    const deals = db.deals.filter((d) => !d.deleted)
    const members = db.members
      .filter((m) => memberActive(m.id) || deals.some((d) => d.assigneeMemberId === m.id))
      .map((m) => {
        const owned = deals.filter((d) => d.assigneeMemberId === m.id)
        const ownedIds = new Set(owned.map((d) => d.id))
        const won = db.orders.filter((o) => ownedIds.has(o.dealId) && inRange(o.createdAt, from, to))
        return { memberId: m.id, name: memberName(m.id), wonCount: won.length, wonAmount: won.reduce((sum, o) => sum + o.totalAmount, 0), activeDealCount: owned.filter((d) => isOpenStage(d.stage)).length }
      })

    /**
     * 전환율(DB-07) — 목의 단순 계산. 단계 이동 이력이 없으므로 "현재 단계 이상에 도달했다"를 도달로 본다:
     *   도달(S) = 현재 단계 순서가 S 이상인 Deal + 성사(WON) Deal + 실패(LOST)했지만 실패 직전 단계가 S 이상인 Deal
     *   rate(from→to) = 도달(to) / 도달(from)   (도달(from)=0이면 0)
     * 실제 서버는 audit_log의 STAGE_MOVED로 계산할 수 있어 값이 다를 수 있다.
     */
    const order = (stage: DealStage) => DEAL_STAGES.indexOf(stage)
    const reachedStage = (d: (typeof deals)[number]): DealStage | null => {
      if (d.stage === 'WON') return 'WON'
      if (d.stage === 'LOST') return d.lostFromStage ?? null
      return d.stage
    }
    const reached = (stage: DealStage) => deals.filter((d) => {
      const r = reachedStage(d)
      return r !== null && (r === 'WON' || order(r) >= order(stage))
    }).length
    const pairs: [DealStage, DealStage][] = [['LEAD', 'CONSULT'], ['CONSULT', 'QUOTE'], ['QUOTE', 'NEGOTIATION'], ['NEGOTIATION', 'WON']]
    const conversions = pairs.map(([fromStage, toStage]) => {
      const denominator = reached(fromStage)
      return { fromStage, toStage, rate: denominator === 0 ? 0 : Math.round((reached(toStage) / denominator) * 1000) / 1000 }
    })

    const body: DashboardPerformanceResponse = { members, conversions }
    return HttpResponse.json(body)
  }),
]
