import { delay, http, HttpResponse } from 'msw'
import { currentMember, db, error, noContent, notFound, paged, recordAudit } from '../store'
import type {
  ChangeRoleRequest, CreateInvitationRequest, DeactivateMemberRequest, InvitationResponse, MemberOptionResponse, MemberResponse,
} from '../../shared/api/types'
import { ROLES, isOpenStage } from '../../shared/ui/status'

/**
 * 구성원·초대 목 — 07 §A (MB) · 08 §A.
 * 실패 경로: 403 FORBIDDEN(영업 담당자의 관리 행위) · 422 LAST_ADMIN_PROTECTED · 422 MEMBER_INACTIVE_TRANSFER_REQUIRED ·
 * 422 EMAIL_ALREADY_MEMBER · 409 INVITATION_ALREADY_PENDING · 409 INVITATION_NOT_PENDING · 404.
 */

const adminOnly = (request: Request) => (currentMember(request).role === 'COMPANY_ADMIN' ? null : error('FORBIDDEN'))
const toMember = (m: (typeof db.members)[number]): MemberResponse => ({ id: m.id, name: m.name, email: m.email, phone: m.phone, role: m.role, status: m.status, createdAt: m.createdAt })
const toInvitation = ({ rawToken: _omit, ...i }: (typeof db.invitations)[number]): InvitationResponse => i
const activeAdmins = () => db.members.filter((m) => m.role === 'COMPANY_ADMIN' && m.status === 'ACTIVE')
const DAY = 86_400_000

export const memberHandlers = [
  // 목록 (MB-07) — 기업 관리자
  http.get('/api/v1/members', async ({ request }) => {
    await delay(120)
    const forbidden = adminOnly(request)
    if (forbidden) return forbidden
    // 서버 기본 정렬 name ASC (MemberController.DEFAULT_SORT) — 목도 같은 순서로 보여준다
    const list = db.members.slice().sort((a, b) => a.name.localeCompare(b.name, 'ko')).map(toMember)
    return HttpResponse.json(paged(list, new URL(request.url)))
  }),

  // 담당자 선택지 (DL-04) — 전 구성원 · 활성만 · 이름·id만
  http.get('/api/v1/members/options', () => {
    const body: MemberOptionResponse[] = db.members.filter((m) => m.status === 'ACTIVE').map(({ id, name }) => ({ id, name }))
    return HttpResponse.json(body)
  }),

  // 역할 변경 (MB-08·11)
  http.patch('/api/v1/members/:id/role', async ({ params, request }) => {
    const forbidden = adminOnly(request)
    if (forbidden) return forbidden
    const member = db.members.find((m) => m.id === params.id)
    if (!member) return notFound()
    const body = (await request.json()) as ChangeRoleRequest
    if (!ROLES.includes(body.role)) return error('VALIDATION_FAILED', [{ field: 'role', reason: '역할을 선택해 주세요.' }])
    if (member.role === 'COMPANY_ADMIN' && body.role !== 'COMPANY_ADMIN' && activeAdmins().length <= 1) return error('LAST_ADMIN_PROTECTED')
    const before = member.role
    member.role = body.role
    recordAudit({ entityType: 'MEMBER', entityId: member.id, eventType: 'ROLE_CHANGED', actorType: 'MEMBER', actorId: currentMember(request).id, changes: { role: { before, after: body.role } } })
    return HttpResponse.json(toMember(member))
  }),

  // 비활성화 (MB-09·10·12·14) — 담당 Deal 이관 필수, 할 일은 Deal을 따라 이동 (Q-29)
  http.post('/api/v1/members/:id/deactivate', async ({ params, request }) => {
    const forbidden = adminOnly(request)
    if (forbidden) return forbidden
    const member = db.members.find((m) => m.id === params.id)
    if (!member) return notFound()
    const body = (await request.json().catch(() => ({}))) as DeactivateMemberRequest
    if (member.role === 'COMPANY_ADMIN' && activeAdmins().length <= 1) return error('LAST_ADMIN_PROTECTED')
    const owned = db.deals.filter((d) => d.assigneeMemberId === member.id && !d.deleted && isOpenStage(d.stage))
    if (owned.length > 0) {
      if (!body.transferToMemberId) return error('MEMBER_INACTIVE_TRANSFER_REQUIRED')
      const target = db.members.find((m) => m.id === body.transferToMemberId && m.status === 'ACTIVE' && m.id !== member.id)
      if (!target) return notFound() // 타사·비활성 대상은 SC-09에 따라 404
      for (const deal of owned) {
        deal.assigneeMemberId = target.id
        deal.assigneeMemberName = target.name
        deal.version += 1
      }
    }
    member.status = 'INACTIVE'
    recordAudit({ entityType: 'MEMBER', entityId: member.id, eventType: 'DEACTIVATED', actorType: 'MEMBER', actorId: currentMember(request).id, changes: { status: { before: 'ACTIVE', after: 'INACTIVE' }, ...(body.transferToMemberId ? { transferTo: { before: null, after: body.transferToMemberId } } : {}) } })
    return HttpResponse.json(toMember(member))
  }),

  http.post('/api/v1/members/:id/reactivate', ({ params, request }) => {
    const forbidden = adminOnly(request)
    if (forbidden) return forbidden
    const member = db.members.find((m) => m.id === params.id)
    if (!member) return notFound()
    member.status = 'ACTIVE'
    recordAudit({ entityType: 'MEMBER', entityId: member.id, eventType: 'REACTIVATED', actorType: 'MEMBER', actorId: currentMember(request).id, changes: { status: { before: 'INACTIVE', after: 'ACTIVE' } } })
    return HttpResponse.json(toMember(member))
  }),

  // ── 초대 (MB-01~06)
  http.post('/api/v1/invitations', async ({ request }) => {
    const forbidden = adminOnly(request)
    if (forbidden) return forbidden
    const body = (await request.json()) as CreateInvitationRequest
    const fieldErrors = [
      ...(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email ?? '') ? [{ field: 'email', reason: '올바른 이메일 형식이 아닙니다.' }] : []),
      ...(!ROLES.includes(body.role) ? [{ field: 'role', reason: '역할을 선택해 주세요.' }] : []),
    ]
    if (fieldErrors.length) return error('VALIDATION_FAILED', fieldErrors)
    if (db.members.some((m) => m.email === body.email)) return error('EMAIL_ALREADY_MEMBER')
    const now = Date.now()
    // 회사·이메일당 대기 초대는 하나다 (uk_invitation_pending). 계정이 있는 것과 다른 코드로 답한다 —
    // 이쪽은 취소 후 재발송으로 풀린다. 기한이 지난 행은 서버가 그 자리에서 만료시키고 자리를 비우므로
    // 목도 같게 둔다 (InvitationService.requirePendingSlotFree) — 안 그러면 목에서만 통과하는 경로가 생긴다
    const pending = db.invitations.find((i) => i.email === body.email && i.status === 'PENDING')
    if (pending) {
      if (Date.parse(pending.expiresAt) > now) return error('INVITATION_ALREADY_PENDING')
      pending.status = 'EXPIRED'
    }
    const created = { id: crypto.randomUUID(), email: body.email.trim(), role: body.role, status: 'PENDING' as const, expiresAt: new Date(now + 7 * DAY).toISOString(), createdAt: new Date(now).toISOString(), rawToken: `invite-${now}` }
    db.invitations.unshift(created)
    return HttpResponse.json(toInvitation(created), { status: 201 })
  }),

  http.get('/api/v1/invitations', async ({ request }) => {
    await delay(120)
    const forbidden = adminOnly(request)
    if (forbidden) return forbidden
    const url = new URL(request.url)
    const status = url.searchParams.get('status')
    const list = db.invitations.filter((i) => !status || i.status === status).sort((a, b) => b.createdAt.localeCompare(a.createdAt)).map(toInvitation)
    return HttpResponse.json(paged(list, url))
  }),

  // 재발송 — 기존 초대 EXPIRED(RESENT) 종결 + 새 토큰(새 행) (MB-06, Q-31)
  http.post('/api/v1/invitations/:id/resend', ({ params, request }) => {
    const forbidden = adminOnly(request)
    if (forbidden) return forbidden
    const invitation = db.invitations.find((i) => i.id === params.id)
    if (!invitation) return notFound()
    if (invitation.status !== 'PENDING') return error('INVITATION_NOT_PENDING')
    invitation.status = 'EXPIRED'
    const now = Date.now()
    const created = { ...invitation, id: crypto.randomUUID(), status: 'PENDING' as const, expiresAt: new Date(now + 7 * DAY).toISOString(), createdAt: new Date(now).toISOString(), rawToken: `invite-${now}` }
    db.invitations.unshift(created)
    return HttpResponse.json(toInvitation(created), { status: 201 })
  }),

  http.post('/api/v1/invitations/:id/cancel', ({ params, request }) => {
    const forbidden = adminOnly(request)
    if (forbidden) return forbidden
    const invitation = db.invitations.find((i) => i.id === params.id)
    if (!invitation) return notFound()
    if (invitation.status !== 'PENDING') return error('INVITATION_NOT_PENDING')
    invitation.status = 'CANCELED'
    return HttpResponse.json(toInvitation(invitation))
  }),
]

export { noContent }
