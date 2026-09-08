import { describe, expect, it, vi } from 'vitest'
import type { RequestHandler } from 'msw'
import { customerHandlers } from './customer'
import { activityHandlers } from './activity'
import { db, session } from '../store'
import { demoAccounts } from '../fixtures'
import type { ContactResponse, CustomerResponse, PageResponse } from '../../shared/api/types'

/**
 * 고객사 목이 백엔드(CustomerController · CustomerService, #137)와 같은 규칙으로 답하는지.
 *
 * 실 API로 바꾸는 순간 화면이 갈라지는 지점만 고정한다: 목록 기본 정렬(createdAt DESC) ·
 * keyword는 대소문자 무시 부분 일치(lower(name) like) · 첫 담당자는 대표 · 대표 지정은 200 + ContactResponse ·
 * 고객사 이력(AC-10)은 customer가 아니라 activity 키가 답한다.
 */

vi.stubGlobal('location', { href: 'http://localhost/', origin: 'http://localhost' })

async function call(handlers: RequestHandler[], request: Request): Promise<Response | null> {
  for (const handler of handlers) {
    const result = await handler.run({ request, requestId: crypto.randomUUID() })
    if (result?.response) return result.response
  }
  return null
}

const [, salesRep] = demoAccounts
const headers = { Authorization: `Bearer ${salesRep.accessToken}`, 'Content-Type': 'application/json' }
const url = (path: string) => `http://localhost/api/v1${path}`

describe('GET /api/v1/customers — 목록·검색 (CU-03·04)', () => {
  it('기본 정렬은 createdAt DESC — 서버 CustomerController.DEFAULT_SORT와 같다', async () => {
    session.login(salesRep)
    const res = (await call(customerHandlers, new Request(url('/customers?size=100'), { headers })))!
    expect(res.status).toBe(200)
    const page: PageResponse<CustomerResponse> = await res.json()
    const dates = page.content.map((c) => c.createdAt)
    expect(dates).toEqual([...dates].sort().reverse())
  })

  it('keyword는 대소문자를 무시한 부분 일치 — 서버의 lower(name) like와 같다', async () => {
    session.login(salesRep)
    // 시드 고객사는 전부 한글이라 대소문자 구분이 드러나지 않는다 — 라틴 이름을 하나 넣고 다른 대소문자로 찾는다
    const created = (await call(customerHandlers, new Request(url('/customers'), {
      method: 'POST', headers, body: JSON.stringify({ name: 'E2E Alpha Corp' }),
    })))!
    const target: CustomerResponse = await created.json()
    const res = (await call(customerHandlers, new Request(url(`/customers?keyword=${encodeURIComponent('e2e alpha')}`), { headers })))!
    const page: PageResponse<CustomerResponse> = await res.json()
    expect(page.content.map((c) => c.id)).toContain(target.id)
    expect(page.totalElements).toBe(1)
  })
})

describe('담당자 (CU-09~11)', () => {
  it('첫 담당자는 대표가 되고, 대표 지정은 200 + ContactResponse로 대표가 옮겨간다', async () => {
    session.login(salesRep)
    const created = (await call(customerHandlers, new Request(url('/customers'), {
      method: 'POST', headers, body: JSON.stringify({ name: 'e2e-고객사' }),
    })))!
    expect(created.status).toBe(201)
    const customer: CustomerResponse = await created.json()

    const post = (body: unknown) => call(customerHandlers, new Request(url(`/customers/${customer.id}/contacts`), { method: 'POST', headers, body: JSON.stringify(body) }))
    const first: ContactResponse = await (await post({ name: '첫째', email: 'first@e2e.test' }))!.json()
    const second: ContactResponse = await (await post({ name: '둘째', email: 'second@e2e.test' }))!.json()
    expect(first.primary).toBe(true)
    expect(second.primary).toBe(false)

    const res = (await call(customerHandlers, new Request(url(`/customers/${customer.id}/contacts/${second.id}/set-primary`), { method: 'POST', headers })))!
    expect(res.status).toBe(200)
    const body: ContactResponse = await res.json()
    expect(body.id).toBe(second.id)
    expect(body.primary).toBe(true)
    expect(db.contacts.get(customer.id)!.find((c) => c.id === first.id)!.primary).toBe(false)
  })
})

describe('GET /api/v1/customers/{id}/activities — 고객사 이력 (AC-10)', () => {
  it('customer 핸들러는 답하지 않고 activity 핸들러가 답한다 — customer를 실 API로 돌려도 목으로 남는다', async () => {
    session.login(salesRep)
    const sample = db.customers.find((c) => !c.deleted)!
    const request = () => new Request(url(`/customers/${sample.id}/activities?size=50`), { headers })
    expect(await call(customerHandlers, request())).toBeNull()
    const res = await call(activityHandlers, request())
    expect(res?.status).toBe(200)
  })
})
