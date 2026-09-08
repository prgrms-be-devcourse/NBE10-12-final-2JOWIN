import { describe, expect, it, vi } from 'vitest'
import type { RequestHandler } from 'msw'
import { productHandlers } from './product'
import { db, session } from '../store'
import { demoAccounts } from '../fixtures'
import type { PageResponse, ProductResponse } from '../../shared/api/types'

/**
 * 상품 목이 백엔드(ProductController · ProductService)와 같은 규칙으로 답하는지.
 *
 * 실 API로 바꾸는 순간 화면이 갈라지는 지점만 고정한다: 목록 기본 정렬(name ASC) ·
 * 편집은 기업 관리자만(PR-09, 403) · 이름 중복은 정확히 같은 문자열만(DB UNIQUE(company_id, name)) ·
 * 판매 중지·재개는 이미 그 상태여도 200(멱등).
 */

vi.stubGlobal('location', { href: 'http://localhost/', origin: 'http://localhost' })

async function call(handlers: RequestHandler[], request: Request): Promise<Response> {
  for (const handler of handlers) {
    const result = await handler.run({ request, requestId: crypto.randomUUID() })
    if (result?.response) return result.response
  }
  throw new Error(`no handler for ${request.method} ${request.url}`)
}

const [admin, salesRep] = demoAccounts
const auth = (account: (typeof demoAccounts)[number]) => ({ Authorization: `Bearer ${account.accessToken}` })

const get = (path: string, account: (typeof demoAccounts)[number]) =>
  call(productHandlers, new Request(`http://localhost/api/v1${path}`, { headers: auth(account) }))

const post = (path: string, account: (typeof demoAccounts)[number], body?: unknown) =>
  call(productHandlers, new Request(`http://localhost/api/v1${path}`, {
    method: 'POST',
    headers: { ...auth(account), 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  }))

describe('GET /api/v1/products — 목록 (PR-03)', () => {
  it('기본 정렬은 name ASC — 서버 ProductController.DEFAULT_SORT와 같다', async () => {
    session.login(salesRep)
    const res = await get('/products?size=100', salesRep)
    expect(res.status).toBe(200)
    const page: PageResponse<ProductResponse> = await res.json()
    const names = page.content.map((p) => p.name)
    expect(names).toEqual([...names].sort((a, b) => a.localeCompare(b, 'ko')))
  })
})

describe('POST /api/v1/products — 등록 (PR-01·02·09)', () => {
  it('영업 담당자는 403 FORBIDDEN', async () => {
    session.login(salesRep)
    const res = await post('/products', salesRep, { name: '영업이 만든 상품', unit: '개', unitPrice: 1000 })
    expect(res.status).toBe(403)
  })

  it('이름 중복은 정확히 같은 문자열만 409 — 대소문자가 다르면 서버처럼 201', async () => {
    session.login(admin)
    const first = await post('/products', admin, { name: 'e2e-case', unit: '개', unitPrice: 1 })
    expect(first.status).toBe(201)
    const same = await post('/products', admin, { name: 'e2e-case', unit: '개', unitPrice: 1 })
    expect(same.status).toBe(409)
    expect((await same.json()).code).toBe('PRODUCT_NAME_DUPLICATED')
    const upper = await post('/products', admin, { name: 'E2E-CASE', unit: '개', unitPrice: 1 })
    expect(upper.status).toBe(201)
  })
})

describe('판매 중지·재개 (PR-05)', () => {
  it('이미 그 상태여도 200 — 서버 엔티티가 상태를 덮어쓸 뿐 예외를 내지 않는다', async () => {
    session.login(admin)
    const product = db.products.find((p) => p.status === 'ACTIVE')!
    expect((await post(`/products/${product.id}/discontinue`, admin)).status).toBe(200)
    expect((await post(`/products/${product.id}/discontinue`, admin)).status).toBe(200)
    expect((await post(`/products/${product.id}/reactivate`, admin)).status).toBe(200)
    expect(product.status).toBe('ACTIVE')
  })
})

describe('PATCH /api/v1/products/{id} — 수정 (PR-04)', () => {
  it('null·미전송 필드는 미변경이고 설명은 빈 문자열로 지운다 (Product.update)', async () => {
    session.login(admin)
    const product = db.products.find((p) => p.status === 'ACTIVE' && p.description)!
    const before = { name: product.name, unit: product.unit, unitPrice: product.unitPrice }
    const patch = (body: unknown) => call(productHandlers, new Request(`http://localhost/api/v1/products/${product.id}`, {
      method: 'PATCH', headers: { ...auth(admin), 'Content-Type': 'application/json' }, body: JSON.stringify(body),
    }))
    expect((await patch({ name: null, unit: null, unitPrice: null, description: null })).status).toBe(200)
    expect({ name: product.name, unit: product.unit, unitPrice: product.unitPrice }).toEqual(before)
    expect(product.description).toBeTruthy()
    // 서버 Product.update는 ''를 그대로 저장한다 — null로 바꾸지 않는다. 화면이 빈 값을 '—'로 거른다
    expect((await patch({ description: '' })).status).toBe(200)
    expect(product.description).toBe('')
  })
})
