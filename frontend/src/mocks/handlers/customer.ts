import { delay, http, HttpResponse } from 'msw'
import { contactsOf, currentMember, db, error, memberName, noContent, notFound, paged } from '../store'
import type {
  ContactResponse, CreateContactRequest, CreateCustomerRequest, CustomerDetailResponse,
  CustomerResponse, UpdateContactRequest, UpdateCustomerRequest,
} from '../../shared/api/types'
import { isOpenStage } from '../../shared/ui/status'

/**
 * 고객사 목 — 07-api-spec.md §B · customer/dto.
 * 실패 경로 포함: 400 VALIDATION_FAILED · 404 RESOURCE_NOT_FOUND · 409 CUSTOMER_HAS_ACTIVE_DEALS ·
 * 422 PRIMARY_CONTACT_REQUIRED · 409 CONTACT_HAS_QUOTES.
 * 고객사는 회사 공유 자원(SC-03) — 담당 스코프를 적용하지 않는다.
 */

const BASE = '/api/v1/customers'

const alive = () => db.customers.filter((c) => !c.deleted)
const findCustomer = (id: string) => alive().find((c) => c.id === id)
const toResponse = ({ deleted: _omit, ...customer }: (typeof db.customers)[number]): CustomerResponse => customer

/** 담당자가 발송된 견적의 수신인이면 삭제 불가 (CU-14) — quote_view_token.recipient_contact_id 기준 */
const hasSentQuotes = (contact: ContactResponse) => db.viewTokens.some((t) => t.recipientContactId === contact.id)

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export const customerHandlers = [
  // 목록 · 검색 (CU-03·04) — 회사 전체
  http.get(BASE, async ({ request }) => {
    await delay(150)
    const url = new URL(request.url)
    const keyword = (url.searchParams.get('keyword') ?? '').trim().toLowerCase()
    const industry = url.searchParams.get('industry') ?? ''
    const list = alive()
      .filter((c) => !keyword || c.name.toLowerCase().includes(keyword))
      .filter((c) => !industry || c.industry === industry)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map(toResponse)
    return HttpResponse.json(paged(list, url))
  }),

  // 상세 (CU-05·12) — 담당자·딜 이력 포함 (성사는 주문 합계, DL-18)
  http.get(`${BASE}/:id`, async ({ params }) => {
    await delay(150)
    const customer = findCustomer(String(params.id))
    if (!customer) return notFound()
    const body: CustomerDetailResponse = {
      ...toResponse(customer),
      createdByMemberName: memberName(customer.createdByMemberId),
      contacts: contactsOf(customer.id),
      deals: db.deals
        .filter((d) => d.customerId === customer.id && !d.deleted)
        .map(({ id, title, stage, expectedAmount, wonAmount, createdAt }) => ({ id, title, stage, expectedAmount, wonAmount, createdAt }))
        .sort((a, b) => b.createdAt.localeCompare(a.createdAt)),
    }
    return HttpResponse.json(body)
  }),

  // 등록 (CU-01·02) — 등록자 = 생성자 기록
  http.post(BASE, async ({ request }) => {
    const body = (await request.json()) as CreateCustomerRequest
    if (!body.name?.trim()) return error('VALIDATION_FAILED', [{ field: 'name', reason: '고객사명을 입력해 주세요.' }])
    if (body.name.length > 255) return error('VALIDATION_FAILED', [{ field: 'name', reason: '255자 이하로 입력해 주세요.' }])
    const created = {
      id: crypto.randomUUID(), name: body.name.trim(), industry: body.industry ?? null, size: body.size ?? null,
      note: body.note?.trim() || null, createdByMemberId: currentMember(request).id, createdAt: new Date().toISOString(), deleted: false,
    }
    db.customers.unshift(created)
    db.contacts.set(created.id, [])
    return HttpResponse.json(toResponse(created), { status: 201 })
  }),

  // 수정 (CU-06) — PATCH: 보내지 않은 필드는 미변경, 보냈으면 공백 불가 (08 v1.6.7)
  http.patch(`${BASE}/:id`, async ({ params, request }) => {
    const customer = findCustomer(String(params.id))
    if (!customer) return notFound()
    const body = (await request.json()) as UpdateCustomerRequest
    if (body.name !== undefined && !body.name.trim()) return error('VALIDATION_FAILED', [{ field: 'name', reason: '공백일 수 없습니다' }])
    if (body.name !== undefined) customer.name = body.name.trim()
    if (body.industry !== undefined) customer.industry = body.industry
    if (body.size !== undefined) customer.size = body.size
    if (body.note !== undefined) customer.note = body.note?.trim() || null
    return HttpResponse.json(toResponse(customer))
  }),

  // 소프트 삭제 (CU-07·08)
  http.delete(`${BASE}/:id`, ({ params }) => {
    const customer = findCustomer(String(params.id))
    if (!customer) return notFound()
    if (db.deals.some((d) => d.customerId === customer.id && !d.deleted && isOpenStage(d.stage))) return error('CUSTOMER_HAS_ACTIVE_DEALS')
    customer.deleted = true
    return noContent()
  }),

  // 담당자 추가 (CU-09·10) — 첫 담당자는 대표
  http.post(`${BASE}/:id/contacts`, async ({ params, request }) => {
    const customer = findCustomer(String(params.id))
    if (!customer) return notFound()
    const body = (await request.json()) as CreateContactRequest
    const fieldErrors = [
      ...(!body.name?.trim() ? [{ field: 'name', reason: '이름을 입력해 주세요.' }] : []),
      ...(!EMAIL.test(body.email ?? '') ? [{ field: 'email', reason: '올바른 이메일 형식이 아닙니다.' }] : []),
    ]
    if (fieldErrors.length) return error('VALIDATION_FAILED', fieldErrors)
    const list = contactsOf(customer.id)
    const created: ContactResponse = {
      id: crypto.randomUUID(), name: body.name.trim(), title: body.title?.trim() || null,
      phone: body.phone?.trim() || null, email: body.email.trim(), primary: list.length === 0,
    }
    db.contacts.set(customer.id, [...list, created])
    return HttpResponse.json(created, { status: 201 })
  }),

  // 담당자 수정 (PATCH — 미전송 필드는 유지)
  http.patch(`${BASE}/:id/contacts/:cid`, async ({ params, request }) => {
    const contact = contactsOf(String(params.id)).find((c) => c.id === params.cid)
    if (!contact) return notFound()
    const body = (await request.json()) as UpdateContactRequest
    if (body.email !== undefined && !EMAIL.test(body.email)) return error('VALIDATION_FAILED', [{ field: 'email', reason: '올바른 이메일 형식이 아닙니다.' }])
    if (body.name !== undefined && !body.name.trim()) return error('VALIDATION_FAILED', [{ field: 'name', reason: '공백일 수 없습니다' }])
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
    if (!contact) return notFound()
    if (contact.primary) return error('PRIMARY_CONTACT_REQUIRED')
    if (hasSentQuotes(contact)) return error('CONTACT_HAS_QUOTES')
    db.contacts.set(customerId, list.filter((c) => c.id !== contact.id))
    return noContent()
  }),

  // 대표 지정 (CU-11) — 지정 시 기존 대표 자동 해제. 서버(CustomerController)는 204가 아니라 200 + ContactResponse를 돌려준다
  http.post(`${BASE}/:id/contacts/:cid/set-primary`, ({ params }) => {
    const list = contactsOf(String(params.id))
    const target = list.find((c) => c.id === params.cid)
    if (!target) return notFound()
    for (const c of list) c.primary = c.id === params.cid
    return HttpResponse.json(target)
  }),

  // 고객사 단위 이력(GET /customers/{id}/activities, AC-10)은 여기 없다 — 경로는 고객사지만 activity 조회라
  // 백엔드도 활동이력 이슈에서 만든다(#107 「제외」). 목은 `activity` 키(handlers/activity.ts)에 둔다.
]
