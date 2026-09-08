import { delay, http, HttpResponse } from 'msw'
import { canSee, currentMember, db, error, findDeal, memberActive, memberName, noContent, notFound, paged } from '../store'
import type {
  ActivityResponse, CreateActivityRequest, CreateTaskRequest, TaskResponse, UpdateActivityRequest, UpdateTaskRequest,
} from '../../shared/api/types'
import { ACTIVITY_CHANNELS, type ActivityChannel } from '../../shared/ui/status'

/**
 * 상담 기록 · 할 일 목 — 07 §B (AC-01~09) · activity/dto.
 * 실패 경로: 404(담당 범위 밖 Deal · 타인 기록 = ACTIVITY_NOT_AUTHOR, SC-09) · 400.
 * 자동 기록(AC-07)은 store.autoActivities에서 온다 — 도메인 이벤트가 쌓아 둔 것.
 * 할 일 목록 GET은 v1에 없다(GAP-04) — 등록·수정만.
 */

const isChannel = (v: unknown): v is ActivityChannel => ACTIVITY_CHANNELS.includes(v as ActivityChannel)

const toManual = (a: (typeof db.activities)[number]): ActivityResponse => ({
  id: a.id, type: 'MANUAL', channel: a.channel, content: a.content,
  authorMemberId: a.authorMemberId, authorMemberName: memberName(a.authorMemberId), authorActive: memberActive(a.authorMemberId),
  occurredAt: a.occurredAt,
})
const toAuto = (a: (typeof db.autoActivities)[number]): ActivityResponse => ({
  id: a.id, type: 'AUTO', channel: null, content: a.content,
  authorMemberId: a.authorMemberId ?? '', authorMemberName: memberName(a.authorMemberId),
  authorActive: a.authorMemberId ? memberActive(a.authorMemberId) : true,
  occurredAt: a.occurredAt,
})
const toTask = (t: (typeof db.tasks)[number]): TaskResponse => ({ id: t.id, dealId: t.dealId, content: t.content, dueDate: t.dueDate, doneAt: t.doneAt })

/** 담당 스코프 안의 Deal (🔶) — 범위 밖은 404 */
function scopedDeal(request: Request, dealId: string) {
  const member = currentMember(request)
  const deal = findDeal(dealId)
  return deal && canSee(member, deal) ? { member, deal } : null
}

export const activityHandlers = [
  // Deal 타임라인 (AC-06·07) — 수동 + 자동, occurredAt DESC, ?type=MANUAL|AUTO
  http.get('/api/v1/deals/:dealId/activities', async ({ params, request }) => {
    await delay(150)
    const hit = scopedDeal(request, String(params.dealId))
    if (!hit) return notFound()
    const url = new URL(request.url)
    const type = url.searchParams.get('type')
    const manual = type === 'AUTO' ? [] : db.activities.filter((a) => a.dealId === hit.deal.id && !a.deleted).map(toManual)
    const auto = type === 'MANUAL' ? [] : db.autoActivities.filter((a) => a.dealId === hit.deal.id).map(toAuto)
    const list = [...manual, ...auto].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
    return HttpResponse.json(paged(list, url))
  }),

  // 상담 기록 (AC-01~03)
  http.post('/api/v1/deals/:dealId/activities', async ({ params, request }) => {
    const hit = scopedDeal(request, String(params.dealId))
    if (!hit) return notFound()
    const body = (await request.json()) as CreateActivityRequest
    const fieldErrors = [
      ...(!isChannel(body.channel) ? [{ field: 'channel', reason: '수단은 CALL·MEETING·EMAIL 중 하나입니다.' }] : []),
      ...(!body.content?.trim() ? [{ field: 'content', reason: '내용을 입력해 주세요.' }] : []),
      ...(!body.occurredAt || Number.isNaN(Date.parse(body.occurredAt)) ? [{ field: 'occurredAt', reason: '발생 시각을 입력해 주세요.' }] : []),
    ]
    if (fieldErrors.length) return error('VALIDATION_FAILED', fieldErrors)
    const created = { id: crypto.randomUUID(), dealId: hit.deal.id, authorMemberId: hit.member.id, channel: body.channel, content: body.content.trim(), occurredAt: new Date(body.occurredAt).toISOString(), deleted: false }
    db.activities.push(created)
    return HttpResponse.json(toManual(created), { status: 201 })
  }),

  // 수정 (AC-04) — 작성자 본인만, 타인 것은 404 ACTIVITY_NOT_AUTHOR
  http.patch('/api/v1/activities/:id', async ({ params, request }) => {
    const member = currentMember(request)
    const activity = db.activities.find((a) => a.id === params.id && !a.deleted)
    if (!activity) return notFound()
    if (activity.authorMemberId !== member.id) return error('ACTIVITY_NOT_AUTHOR')
    const body = (await request.json()) as UpdateActivityRequest
    if (body.channel !== undefined && !isChannel(body.channel)) return error('VALIDATION_FAILED', [{ field: 'channel', reason: '수단은 CALL·MEETING·EMAIL 중 하나입니다.' }])
    if (body.content !== undefined && !body.content.trim()) return error('VALIDATION_FAILED', [{ field: 'content', reason: '공백일 수 없습니다' }])
    if (body.channel !== undefined) activity.channel = body.channel
    if (body.content !== undefined) activity.content = body.content.trim()
    if (body.occurredAt !== undefined) activity.occurredAt = new Date(body.occurredAt).toISOString()
    return HttpResponse.json(toManual(activity))
  }),

  // 삭제 (AC-05) — 작성자 본인만
  http.delete('/api/v1/activities/:id', ({ params, request }) => {
    const member = currentMember(request)
    const activity = db.activities.find((a) => a.id === params.id && !a.deleted)
    if (!activity) return notFound()
    if (activity.authorMemberId !== member.id) return error('ACTIVITY_NOT_AUTHOR')
    activity.deleted = true
    return noContent()
  }),

  // 다음 할 일 (AC-09) — 배정 없음, Deal이 곧 소유 (Q-29)
  http.post('/api/v1/deals/:dealId/tasks', async ({ params, request }) => {
    const hit = scopedDeal(request, String(params.dealId))
    if (!hit) return notFound()
    const body = (await request.json()) as CreateTaskRequest
    const fieldErrors = [
      ...(!body.content?.trim() ? [{ field: 'content', reason: '내용을 입력해 주세요.' }] : []),
      ...(body.content && body.content.length > 500 ? [{ field: 'content', reason: '500자 이하로 입력해 주세요.' }] : []),
      ...(!body.dueDate ? [{ field: 'dueDate', reason: '마감일을 입력해 주세요.' }] : []),
    ]
    if (fieldErrors.length) return error('VALIDATION_FAILED', fieldErrors)
    const created = { id: crypto.randomUUID(), dealId: hit.deal.id, content: body.content.trim(), dueDate: body.dueDate, doneAt: null }
    db.tasks.push(created)
    return HttpResponse.json(toTask(created), { status: 201 })
  }),

  // 완료 처리·수정 — 담당 Deal의 할 일만 (🔶)
  http.patch('/api/v1/tasks/:id', async ({ params, request }) => {
    const task = db.tasks.find((t) => t.id === params.id)
    if (!task) return notFound()
    if (!scopedDeal(request, task.dealId)) return notFound()
    const body = (await request.json()) as UpdateTaskRequest
    if (body.content !== undefined && !body.content.trim()) return error('VALIDATION_FAILED', [{ field: 'content', reason: '공백일 수 없습니다' }])
    if (body.content !== undefined) task.content = body.content.trim()
    if (body.dueDate !== undefined) task.dueDate = body.dueDate
    if (body.done !== undefined) task.doneAt = body.done ? new Date().toISOString() : null
    return HttpResponse.json(toTask(task))
  }),

  // ── 고객사 단위 이력 (AC-10) — 경로는 /customers지만 activity 조회라 여기 둔다. 백엔드는 활동이력 이슈에서 (#107 「제외」).
  //    07에 응답 형태 미명시 — 목록 공통 규칙(Q-39)대로 PageResponse<ActivityResponse>. customer를 실 API로 돌려도 이건 목이다.
  http.get('/api/v1/customers/:id/activities', async ({ params, request }) => {
    await delay(200)
    const customer = db.customers.find((c) => c.id === params.id && !c.deleted)
    if (!customer) return notFound()
    return HttpResponse.json(paged(customerActivities(customer.id), new URL(request.url)))
  }),
]

/** 고객사의 모든 Deal 타임라인(수동 + 자동)을 합친다 — 삭제된 Deal은 제외 */
function customerActivities(customerId: string): ActivityResponse[] {
  const dealIds = new Set(db.deals.filter((d) => d.customerId === customerId && !d.deleted).map((d) => d.id))
  const manual: ActivityResponse[] = db.activities
    .filter((a) => dealIds.has(a.dealId) && !a.deleted)
    .map((a) => ({ id: a.id, type: 'MANUAL', channel: a.channel, content: a.content, authorMemberId: a.authorMemberId, authorMemberName: memberName(a.authorMemberId), authorActive: memberActive(a.authorMemberId), occurredAt: a.occurredAt }))
  const auto: ActivityResponse[] = db.autoActivities
    .filter((a) => dealIds.has(a.dealId))
    .map((a) => ({ id: a.id, type: 'AUTO', channel: null, content: a.content, authorMemberId: a.authorMemberId ?? '', authorMemberName: memberName(a.authorMemberId), authorActive: a.authorMemberId ? memberActive(a.authorMemberId) : true, occurredAt: a.occurredAt }))
  return [...manual, ...auto].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
}
