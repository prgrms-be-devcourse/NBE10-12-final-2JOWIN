import { delay, http, HttpResponse } from 'msw'
import { contacts, customers, deals, demoAccounts, members, quotes } from '../fixtures'
import type {
  ActivityResponse, ContactResponse, CreateContactRequest, CreateCustomerRequest, CustomerDetailResponse,
  CustomerResponse, PageResponse, UpdateContactRequest, UpdateCustomerRequest,
} from '../../shared/api/types'

/**
 * 고객사 목 — 07-api-spec.md §B · 08-dto.md §B.
 * 실패 경로 포함: 400 VALIDATION_FAILED · 404 RESOURCE_NOT_FOUND · 409 CUSTOMER_HAS_ACTIVE_DEALS ·
 * 422 PRIMARY_CONTACT_REQUIRED · 409 CONTACT_HAS_QUOTES.
 * 상태는 메모리에만 유지된다 (새로고침 시 픽스처로 초기화).
 */

const BASE = '/api/v1/customers'
const ACTIVE_STAGES = new Set(['LEAD', 'CONSULT', 'QUOTE', 'NEGOTIATION'])

/** 목의 현재 사용자 — auth 목과 같은 계정 */
const me = () => demoAccounts[0].login

const error = (code: string, message: string, status: number, fieldErrors: { field: string; reason: string }[] = []) =>
  HttpResponse.json({ code, message, fieldErrors }, { status })

// ── 메모리 상태 — 픽스처를 복사해 시작한다 ────────────────────────────────────
let rows: (CustomerResponse & { deleted: boolean })[] = customers.map((c) => ({ ...c, deleted: false }))
const contactRows = new Map<string, ContactResponse[]>(Object.entries(contacts).map(([id, list]) => [id, list.map((c) => ({ ...c }))]))

const alive = () => rows.filter((r) => !r.deleted)
const findCustomer = (id: string) => alive().find((r) => r.id === id)
const contactsOf = (customerId: string) => contactRows.get(customerId) ?? []
const memberName = (id: string) => members.find((m) => m.id === id)?.name ?? ''

/** 견적 픽스처에 수신인이 없어 대표 담당자를 수신인으로 간주한다 (Q-07) */
function hasSentQuotes(customerId: string, contact: ContactResponse): boolean {
  if (!contact.primary) return false
  const dealIds = new Set(deals.filter((d) => d.customerId === customerId).map((d) => d.id))
  return quotes.some((q) => dealIds.has(q.dealId) && q.sentAt !== null)
}

/** 고객사 단위 이력 (AC-10) — 딜·견적 픽스처에서 파생 */
function activitiesOf(customer: CustomerResponse): ActivityResponse[] {
  const list: ActivityResponse[] = []
  const auto = (id: string, content: string, authorId: string, occurredAt: string) =>
    list.push({ id, type: 'AUTO', channel: null, content, authorMemberId: authorId, authorMemberName: memberName(authorId), authorActive: true, occurredAt })

  for (const deal of deals.filter((d) => d.customerId === customer.id)) {
    auto(`act-deal-${deal.id}`, `딜 「${deal.title}」을 만들었습니다`, deal.assigneeMemberId, deal.createdAt)
    for (const quote of quotes.filter((q) => q.dealId === deal.id)) {
      if (quote.sentAt) auto(`act-sent-${quote.id}`, `견적 ${quote.quoteNo}을 발송했습니다`, deal.assigneeMemberId, quote.sentAt)
      if (quote.firstViewedAt) auto(`act-view-${quote.id}`, `고객이 견적 ${quote.quoteNo}을 열람했습니다`, deal.assigneeMemberId, quote.firstViewedAt)
    }
  }
  if (customer.note) {
    list.push({
      id: `act-note-${customer.id}`, type: 'MANUAL', channel: 'MEMO', content: customer.note,
      authorMemberId: customer.createdByMemberId, authorMemberName: memberName(customer.createdByMemberId), authorActive: true,
      occurredAt: customer.createdAt,
    })
  }
  return list.sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
}

function paged<T>(items: T[], url: URL): PageResponse<T> {
  const page = Math.max(0, Number(url.searchParams.get('page') ?? 0))
  const size = Math.min(100, Math.max(1, Number(url.searchParams.get('size') ?? 20)))
  return { content: items.slice(page * size, page * size + size), page, size, totalElements: items.length, totalPages: Math.max(1, Math.ceil(items.length / size)) }
}

export const customerHandlers = [
  // 목록 · 검색 (CU-03·04)
  http.get(BASE, async ({ request }) => {
    await delay(150)
    const url = new URL(request.url)
    const keyword = (url.searchParams.get('keyword') ?? '').trim().toLowerCase()
    const industry = url.searchParams.get('industry') ?? ''
    const list = alive()
      .filter((c) => !keyword || c.name.toLowerCase().includes(keyword))
      .filter((c) => !industry || c.industry === industry)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    return HttpResponse.json(paged(list, url))
  }),

  // 상세 (CU-05·12) — 담당자·딜 이력 포함
  http.get(`${BASE}/:id`, async ({ params }) => {
    await delay(150)
    const customer = findCustomer(String(params.id))
    if (!customer) return error('RESOURCE_NOT_FOUND', '요청한 대상을 찾을 수 없습니다.', 404)
    const body: CustomerDetailResponse = {
      ...customer,
      createdByMemberName: memberName(customer.createdByMemberId),
      contacts: contactsOf(customer.id),
      deals: deals
        .filter((d) => d.customerId === customer.id)
        .map(({ id, title, stage, expectedAmount, wonAmount, createdAt }) => ({ id, title, stage, expectedAmount, wonAmount, createdAt }))
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    }
    return HttpResponse.json(body)
  }),

  // 등록 (CU-01·02)
  http.post(BASE, async ({ request }) => {
    const body = (await request.json()) as CreateCustomerRequest
    if (!body.name?.trim()) {
      return error('VALIDATION_FAILED', '입력값을 확인해 주세요.', 400, [{ field: 'name', reason: '고객사명을 입력해 주세요.' }])
    }
    const created: CustomerResponse & { deleted: boolean } = {
      id: crypto.randomUUID(), name: body.name.trim(), industry: body.industry ?? null, size: body.size ?? null,
      note: body.note?.trim() || null, createdByMemberId: me().memberId, createdAt: new Date().toISOString(), deleted: false,
    }
    rows = [created, ...rows]
    contactRows.set(created.id, [])
    const { deleted: _omit, ...response } = created
    return HttpResponse.json(response, { status: 201 })
  }),

  // 수정 (CU-06)
  http.patch(`${BASE}/:id`, async ({ params, request }) => {
    const customer = findCustomer(String(params.id))
    if (!customer) return error('RESOURCE_NOT_FOUND', '요청한 대상을 찾을 수 없습니다.', 404)
    const body = (await request.json()) as UpdateCustomerRequest
    if (!body.name?.trim()) {
      return error('VALIDATION_FAILED', '입력값을 확인해 주세요.', 400, [{ field: 'name', reason: '고객사명을 입력해 주세요.' }])
    }
    Object.assign(customer, { name: body.name.trim(), industry: body.industry ?? null, size: body.size ?? null, note: body.note?.trim() || null })
    const { deleted: _omit, ...response } = customer
    return HttpResponse.json(response)
  }),

  // 소프트 삭제 (CU-07·08)
  http.delete(`${BASE}/:id`, ({ params }) => {
    const customer = findCustomer(String(params.id))
    if (!customer) return error('RESOURCE_NOT_FOUND', '요청한 대상을 찾을 수 없습니다.', 404)
    if (deals.some((d) => d.customerId === customer.id && ACTIVE_STAGES.has(d.stage))) {
      return error('CUSTOMER_HAS_ACTIVE_DEALS', '진행 중인 Deal이 있어 삭제할 수 없습니다.', 409)
    }
    customer.deleted = true
    return new HttpResponse(null, { status: 204 })
  }),

  // 담당자 추가 (CU-09·10) — 첫 담당자는 대표
  http.post(`${BASE}/:id/contacts`, async ({ params, request }) => {
    const customer = findCustomer(String(params.id))
    if (!customer) return error('RESOURCE_NOT_FOUND', '요청한 대상을 찾을 수 없습니다.', 404)
    const body = (await request.json()) as CreateContactRequest
    const fieldErrors = [
      ...(!body.name?.trim() ? [{ field: 'name', reason: '이름을 입력해 주세요.' }] : []),
      ...(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email ?? '') ? [{ field: 'email', reason: '올바른 이메일 형식이 아닙니다.' }] : []),
    ]
    if (fieldErrors.length) return error('VALIDATION_FAILED', '입력값을 확인해 주세요.', 400, fieldErrors)
    const list = contactsOf(customer.id)
    const created: ContactResponse = {
      id: crypto.randomUUID(), name: body.name.trim(), title: body.title?.trim() || null,
      phone: body.phone?.trim() || null, email: body.email.trim(), primary: list.length === 0,
    }
    contactRows.set(customer.id, [...list, created])
    return HttpResponse.json(created, { status: 201 })
  }),

  // 담당자 수정 (PATCH — 미전송 필드는 유지)
  http.patch(`${BASE}/:id/contacts/:cid`, async ({ params, request }) => {
    const contact = contactsOf(String(params.id)).find((c) => c.id === params.cid)
    if (!contact) return error('RESOURCE_NOT_FOUND', '요청한 대상을 찾을 수 없습니다.', 404)
    const body = (await request.json()) as UpdateContactRequest
    if (body.email !== undefined && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(body.email)) {
      return error('VALIDATION_FAILED', '입력값을 확인해 주세요.', 400, [{ field: 'email', reason: '올바른 이메일 형식이 아닙니다.' }])
    }
    if (body.name !== undefined) contact.name = body.name.trim()
    if (body.title !== undefined) contact.title = body.title?.trim() || null
    if (body.phone !== undefined) contact.phone = body.phone?.trim() || null
    if (body.email !== undefined) contact.email = body.email.trim()
    return HttpResponse.json(contact)
  }),

  // 담당자 삭제 (CU-11·14)
  http.delete(`${BASE}/:id/contacts/:cid`, ({ params }) => {
    const customerId = String(params.id)
    const list = contactsOf(customerId)
    const contact = list.find((c) => c.id === params.cid)
    if (!contact) return error('RESOURCE_NOT_FOUND', '요청한 대상을 찾을 수 없습니다.', 404)
    if (contact.primary) {
      return error('PRIMARY_CONTACT_REQUIRED', '대표 담당자는 삭제할 수 없습니다. 먼저 다른 담당자를 대표로 지정해 주세요.', 422)
    }
    if (hasSentQuotes(customerId, contact)) {
      return error('CONTACT_HAS_QUOTES', '견적 발송 이력이 있어 삭제할 수 없습니다.', 409)
    }
    contactRows.set(customerId, list.filter((c) => c.id !== contact.id))
    return new HttpResponse(null, { status: 204 })
  }),

  // 대표 지정 (CU-11)
  http.post(`${BASE}/:id/contacts/:cid/set-primary`, ({ params }) => {
    const list = contactsOf(String(params.id))
    if (!list.some((c) => c.id === params.cid)) return error('RESOURCE_NOT_FOUND', '요청한 대상을 찾을 수 없습니다.', 404)
    for (const c of list) c.primary = c.id === params.cid
    return new HttpResponse(null, { status: 204 })
  }),

  // 고객사 단위 이력 (AC-10)
  http.get(`${BASE}/:id/activities`, async ({ params, request }) => {
    await delay(200)
    const customer = findCustomer(String(params.id))
    if (!customer) return error('RESOURCE_NOT_FOUND', '요청한 대상을 찾을 수 없습니다.', 404)
    return HttpResponse.json(paged(activitiesOf(customer), new URL(request.url)))
  }),
]
