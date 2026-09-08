import { delay, http, HttpResponse } from 'msw'
import {
  bumpVersion, canSee, currentMember, customerName, db, error, expireQuotesOfDeal, findDeal, memberName, noContent, notFound, paged,
  recordAudit, recordAuto, visibleDeals, wonAmountOf,
} from '../store'
import type {
  ChangeAssigneeRequest, CreateDealRequest, DealDetailResponse, DealResponse, LoseDealRequest, StageMoveRequest, UpdateDealRequest,
} from '../../shared/api/types'
import { DEAL_STAGES, NEXT_STAGE, PREVIOUS_STAGE, isOpenStage, type DealStage } from '../../shared/ui/status'

/**
 * Deal 목 — deal/controller/DealController · deal/dto · 전이표 §5.
 * 실패 경로: 404(범위 밖 = 존재 여부 비노출, SC-09) · 409 STALE_VERSION · DEAL_WON_REQUIRES_ORDER ·
 * DEAL_NOT_OPEN · DEAL_NOT_LOST · DEAL_NO_PREVIOUS_STAGE · DEAL_HAS_QUOTES · 403 FORBIDDEN(영업의 담당자 변경).
 * 영업 담당자는 본인 담당 Deal만 (OWNED_ONLY) — 목록에서 걸러지고 단건은 404.
 */

const BASE = '/api/v1/deals'
type Row = (typeof db.deals)[number]

const toItem = (d: Row): DealResponse => ({
  id: d.id, title: d.title, stage: d.stage, expectedAmount: d.expectedAmount, wonAmount: wonAmountOf(d.id),
  customerId: d.customerId, customerName: customerName(d.customerId),
  assigneeMemberId: d.assigneeMemberId, assigneeMemberName: memberName(d.assigneeMemberId),
  dueDate: d.dueDate, version: d.version, createdAt: d.createdAt,
})

const toDetail = (d: Row): DealDetailResponse => ({
  ...toItem(d),
  lostReason: d.lostReason,
  quotes: db.quotes
    .filter((q) => q.dealId === d.id)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .map(({ id, quoteNo, status, totalAmount, sentAt }) => ({ id, quoteNo, status, totalAmount, sentAt })),
  orders: db.orders
    .filter((o) => o.dealId === d.id)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .map(({ id, orderNo, totalAmount, createdAt }) => ({ id, orderNo, totalAmount, createdAt })),
})

/** 담당 스코프 안의 Deal — 범위 밖은 없는 것으로 (SC-09) */
function scoped(request: Request, id: string) {
  const member = currentMember(request)
  const deal = findDeal(id)
  return deal && canSee(member, deal) ? { member, deal } : null
}

const stale = () => error('STALE_VERSION')

export const dealHandlers = [
  // 목록 · 보드 (DL-06·13·14) — stage · assigneeId · customerId 필터, createdAt DESC
  http.get(BASE, async ({ request }) => {
    await delay(150)
    const url = new URL(request.url)
    const stage = url.searchParams.get('stage')
    const assigneeId = url.searchParams.get('assigneeId')
    const customerId = url.searchParams.get('customerId')
    if (stage && !DEAL_STAGES.includes(stage as DealStage)) return error('VALIDATION_FAILED', [{ field: 'stage', reason: '알 수 없는 단계입니다.' }])
    const member = currentMember(request)
    // 영업 담당자(OWNED_ONLY)는 assigneeId를 무엇으로 보내든 본인으로 고정된다 — 서버 DealService.list와 같다 (SC-02).
    // 남의 id를 넣으면 0건이 아니라 "내 것"이 나온다.
    const scopedAssigneeId = member.role === 'COMPANY_ADMIN' ? assigneeId : member.id
    const list = visibleDeals(member)
      .filter((d) => !stage || d.stage === stage)
      .filter((d) => !scopedAssigneeId || d.assigneeMemberId === scopedAssigneeId)
      .filter((d) => !customerId || d.customerId === customerId)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(toItem)
    return HttpResponse.json(paged(list, url))
  }),

  // 상세 (DL-15·18)
  http.get(`${BASE}/:id`, async ({ params, request }) => {
    await delay(150)
    const hit = scoped(request, String(params.id))
    if (!hit) return notFound()
    return HttpResponse.json(toDetail(hit.deal))
  }),

  // 생성 (DL-01~04) — 배정 대상은 활성 구성원, 없으면 본인
  http.post(BASE, async ({ request }) => {
    const member = currentMember(request)
    const body = (await request.json()) as CreateDealRequest
    const fieldErrors = [
      ...(!body.customerId ? [{ field: 'customerId', reason: '고객사를 선택해 주세요.' }] : []),
      ...(!body.title?.trim() ? [{ field: 'title', reason: '제목을 입력해 주세요.' }] : []),
      ...(body.expectedAmount != null && body.expectedAmount < 0 ? [{ field: 'expectedAmount', reason: '0 이상이어야 합니다.' }] : []),
    ]
    if (fieldErrors.length) return error('VALIDATION_FAILED', fieldErrors)
    if (!db.customers.some((c) => c.id === body.customerId && !c.deleted)) return notFound()
    const assigneeId = body.assigneeMemberId ?? member.id
    const assignee = db.members.find((m) => m.id === assigneeId && m.status === 'ACTIVE')
    if (!assignee) return notFound() // 타사·비활성 구성원 배정은 SC-09에 따라 404
    const now = new Date().toISOString()
    const created: Row = {
      id: crypto.randomUUID(), title: body.title.trim(), stage: 'LEAD', expectedAmount: body.expectedAmount ?? null as unknown as number,
      wonAmount: null, customerId: body.customerId, customerName: customerName(body.customerId),
      assigneeMemberId: assignee.id, assigneeMemberName: assignee.name, dueDate: body.dueDate ?? null as unknown as string,
      version: 0, createdAt: now, lostReason: null, lostFromStage: null, deleted: false,
    }
    db.deals.unshift(created)
    recordAudit({ entityType: 'DEAL', entityId: created.id, eventType: 'CREATED', actorType: 'MEMBER', actorId: member.id, changes: { title: { before: null, after: created.title } } })
    recordAuto(created.id, '딜이 생성되었습니다', member.id, now)
    return HttpResponse.json(toItem(created), { status: 201 })
  }),

  // 수정 (DL-02·03) — PATCH: null·미전송 = 미변경 (Deal.update — 미정으로 되돌리는 경로는 v1에 없다), version 필수
  http.patch(`${BASE}/:id`, async ({ params, request }) => {
    const hit = scoped(request, String(params.id))
    if (!hit) return notFound()
    const body = (await request.json()) as UpdateDealRequest
    if (body.expectedAmount != null && body.expectedAmount < 0) return error('VALIDATION_FAILED', [{ field: 'expectedAmount', reason: '0 이상이어야 합니다.' }])
    if (body.title != null && !body.title.trim()) return error('VALIDATION_FAILED', [{ field: 'title', reason: '공백일 수 없습니다' }])
    if (!bumpVersion(hit.deal, body.version)) return stale()
    const changes: Record<string, { before: unknown; after: unknown }> = {}
    if (body.title != null && body.title.trim() !== hit.deal.title) changes.title = { before: hit.deal.title, after: body.title.trim() }
    if (body.expectedAmount != null && body.expectedAmount !== hit.deal.expectedAmount) changes.expectedAmount = { before: hit.deal.expectedAmount, after: body.expectedAmount }
    if (body.dueDate != null && body.dueDate !== hit.deal.dueDate) changes.dueDate = { before: hit.deal.dueDate, after: body.dueDate }
    if (body.title != null) hit.deal.title = body.title.trim()
    if (body.expectedAmount != null) hit.deal.expectedAmount = body.expectedAmount
    if (body.dueDate != null) hit.deal.dueDate = body.dueDate
    if (Object.keys(changes).length) recordAudit({ entityType: 'DEAL', entityId: hit.deal.id, eventType: 'UPDATED', actorType: 'MEMBER', actorId: hit.member.id, changes })
    return HttpResponse.json(toItem(hit.deal))
  }),

  // 다음 단계 (DL-07) — 인접만. 협상에서는 성사 수동 이동 불가 (DL-09)
  http.post(`${BASE}/:id/advance`, async ({ params, request }) => {
    const hit = scoped(request, String(params.id))
    if (!hit) return notFound()
    const body = (await request.json()) as StageMoveRequest
    const { deal } = hit
    if (deal.stage === 'WON') return error('DEAL_ALREADY_WON')
    if (!isOpenStage(deal.stage)) return error('DEAL_NOT_OPEN')
    if (deal.stage === 'NEGOTIATION') return error('DEAL_WON_REQUIRES_ORDER')
    if (!bumpVersion(deal, body.version)) return stale()
    return HttpResponse.json(move(hit, NEXT_STAGE[deal.stage]!))
  }),

  // 이전 단계 (DL-08) — 리드에서는 불가
  http.post(`${BASE}/:id/revert`, async ({ params, request }) => {
    const hit = scoped(request, String(params.id))
    if (!hit) return notFound()
    const body = (await request.json()) as StageMoveRequest
    const { deal } = hit
    if (deal.stage === 'WON') return error('DEAL_ALREADY_WON')
    if (!isOpenStage(deal.stage)) return error('DEAL_NOT_OPEN')
    if (deal.stage === 'LEAD') return error('DEAL_NO_PREVIOUS_STAGE')
    if (!bumpVersion(deal, body.version)) return stale()
    return HttpResponse.json(move(hit, PREVIOUS_STAGE[deal.stage]!))
  }),

  // 실패 처리 (DL-10·11) — 효과: 진행 중 견적 EXPIRED + 링크 만료(DEAL_LOST)
  http.post(`${BASE}/:id/lose`, async ({ params, request }) => {
    const hit = scoped(request, String(params.id))
    if (!hit) return notFound()
    const body = (await request.json()) as LoseDealRequest
    const { deal, member } = hit
    if (!body.reason?.trim()) return error('VALIDATION_FAILED', [{ field: 'reason', reason: '사유를 입력해 주세요.' }])
    if (deal.stage === 'WON') return error('DEAL_ALREADY_WON')
    if (!isOpenStage(deal.stage)) return error('DEAL_NOT_OPEN')
    if (!bumpVersion(deal, body.version)) return stale()
    const before = deal.stage
    deal.lostFromStage = before
    deal.lostReason = body.reason.trim()
    deal.stage = 'LOST'
    expireQuotesOfDeal(deal.id)
    recordAudit({ entityType: 'DEAL', entityId: deal.id, eventType: 'LOST', actorType: 'MEMBER', actorId: member.id, changes: { stage: { before, after: 'LOST' }, lostReason: { before: null, after: deal.lostReason } } })
    recordAuto(deal.id, `딜을 실패 처리했습니다 — ${deal.lostReason}`, member.id)
    return HttpResponse.json(toItem(deal))
  }),

  // 재개 (DL-12) — 실패 직전 단계로. 만료된 견적·링크는 복원되지 않는다
  http.post(`${BASE}/:id/reopen`, async ({ params, request }) => {
    const hit = scoped(request, String(params.id))
    if (!hit) return notFound()
    const body = (await request.json()) as StageMoveRequest
    const { deal } = hit
    if (deal.stage === 'WON') return error('DEAL_NOT_OPEN')
    if (deal.stage !== 'LOST') return error('DEAL_NOT_LOST')
    if (!bumpVersion(deal, body.version)) return stale()
    const to = (deal.lostFromStage ?? 'LEAD') as DealStage
    deal.lostFromStage = null
    return HttpResponse.json(move(hit, to, 'REOPENED'))
  }),

  // 담당자 변경 (DL-05, SC-06) — 기업 관리자만 · 같은 회사 활성 구성원
  http.patch(`${BASE}/:id/assignee`, async ({ params, request }) => {
    const member = currentMember(request)
    if (member.role !== 'COMPANY_ADMIN') return error('FORBIDDEN')
    const deal = findDeal(String(params.id))
    if (!deal) return notFound()
    const body = (await request.json()) as ChangeAssigneeRequest
    const target = db.members.find((m) => m.id === body.assigneeMemberId && m.status === 'ACTIVE')
    if (!target) return notFound()
    if (!bumpVersion(deal, body.version)) return stale()
    const before = deal.assigneeMemberId
    deal.assigneeMemberId = target.id
    deal.assigneeMemberName = target.name
    recordAudit({ entityType: 'DEAL', entityId: deal.id, eventType: 'ASSIGNEE_CHANGED', actorType: 'MEMBER', actorId: member.id, changes: { assigneeMemberId: { before, after: target.id } } })
    recordAuto(deal.id, `담당자가 ${memberName(before)} → ${target.name}(으)로 바뀌었습니다`, member.id)
    return HttpResponse.json(toItem(deal))
  }),

  // 소프트 삭제 (DL-16·17) — 07에만 있고 DealController에는 아직 없는 엔드포인트
  http.delete(`${BASE}/:id`, ({ params, request }) => {
    const hit = scoped(request, String(params.id))
    if (!hit) return notFound()
    if (db.quotes.some((q) => q.dealId === hit.deal.id)) return error('DEAL_HAS_QUOTES')
    hit.deal.deleted = true
    recordAudit({ entityType: 'DEAL', entityId: hit.deal.id, eventType: 'DELETED', actorType: 'MEMBER', actorId: hit.member.id, changes: { deleted: { before: false, after: true } } })
    return noContent()
  }),
]

/** 수동 단계 이동 — 감사·자동 기록을 남기고 DealItem을 돌려준다 (version은 호출자가 이미 올렸다) */
function move({ deal, member }: { deal: Row; member: { id: string } }, to: DealStage, eventType = 'STAGE_MOVED'): DealResponse {
  const before = deal.stage
  deal.stage = to
  recordAudit({ entityType: 'DEAL', entityId: deal.id, eventType, actorType: 'MEMBER', actorId: member.id, changes: { stage: { before, after: to } } })
  recordAuto(deal.id, `단계를 ${label(before)} → ${label(to)}(으)로 옮겼습니다`, member.id)
  return toItem(deal)
}

const label = (stage: DealStage) => ({ LEAD: '리드', CONSULT: '상담', QUOTE: '견적', NEGOTIATION: '협상', WON: '성사', LOST: '실패' })[stage]
