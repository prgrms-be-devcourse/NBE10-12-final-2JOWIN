import { delay, http, HttpResponse } from 'msw'
import { currentMember, db, error, notFound, paged, recordAudit } from '../store'
import type { CreateProductRequest, ProductResponse, UpdateProductRequest } from '../../shared/api/types'

/**
 * 상품 카탈로그 목 — product/controller/ProductController · product/dto.
 * 실패 경로: 403 FORBIDDEN(영업 담당자 편집, PR-09) · 409 PRODUCT_NAME_DUPLICATED(판매 중지 포함) · 400 · 404.
 * 정렬은 컨트롤러 기본값 name ASC 고정 (Q-39).
 */

const adminOnly = (request: Request) => (currentMember(request).role === 'COMPANY_ADMIN' ? null : error('FORBIDDEN'))
const find = (id: string) => db.products.find((p) => p.id === id)
// 서버(ProductService)와 DB UNIQUE(company_id, name)는 정확히 같은 문자열만 중복으로 본다 — 대소문자·앞뒤 공백을 무시하지 않는다.
// 화면(ProductFormDialog)이 trim해서 보내므로 여기서도 trim만 하고 대소문자는 그대로 비교한다.
const duplicated = (name: string, exceptId?: string) => db.products.some((p) => p.id !== exceptId && p.name === name.trim())

export const productHandlers = [
  http.get('/api/v1/products', async ({ request }) => {
    await delay(120)
    const url = new URL(request.url)
    const status = url.searchParams.get('status')
    const list: ProductResponse[] = db.products
      .filter((p) => !status || p.status === status)
      .sort((a, b) => a.name.localeCompare(b.name, 'ko'))
    return HttpResponse.json(paged(list, url))
  }),

  http.post('/api/v1/products', async ({ request }) => {
    const forbidden = adminOnly(request)
    if (forbidden) return forbidden
    const body = (await request.json()) as CreateProductRequest
    const fieldErrors = [
      ...(!body.name?.trim() ? [{ field: 'name', reason: '상품명을 입력해 주세요.' }] : []),
      ...(!body.unit?.trim() ? [{ field: 'unit', reason: '단위를 입력해 주세요.' }] : []),
      ...(body.unitPrice === undefined || body.unitPrice === null || body.unitPrice < 0 ? [{ field: 'unitPrice', reason: '0원 이상이어야 합니다.' }] : []),
    ]
    if (fieldErrors.length) return error('VALIDATION_FAILED', fieldErrors)
    if (duplicated(body.name)) return error('PRODUCT_NAME_DUPLICATED')
    const created: ProductResponse = { id: crypto.randomUUID(), name: body.name.trim(), unit: body.unit.trim(), unitPrice: body.unitPrice, description: body.description?.trim() || null, status: 'ACTIVE' }
    db.products.push(created)
    recordAudit({ entityType: 'PRODUCT', entityId: created.id, eventType: 'CREATED', actorType: 'MEMBER', actorId: currentMember(request).id, changes: { name: { before: null, after: created.name } } })
    return HttpResponse.json(created, { status: 201 })
  }),

  // PATCH — 보내지 않은 필드·null은 미변경 (Product.update) · 설명은 ''로 지운다 · 이름 변경 시 중복 검사 (PR-04)
  http.patch('/api/v1/products/:id', async ({ params, request }) => {
    const forbidden = adminOnly(request)
    if (forbidden) return forbidden
    const product = find(String(params.id))
    if (!product) return notFound()
    const body = (await request.json()) as UpdateProductRequest
    if (body.name != null && !body.name.trim()) return error('VALIDATION_FAILED', [{ field: 'name', reason: '공백일 수 없습니다' }])
    if (body.unit != null && !body.unit.trim()) return error('VALIDATION_FAILED', [{ field: 'unit', reason: '공백일 수 없습니다' }])
    if (body.unitPrice != null && body.unitPrice < 0) return error('VALIDATION_FAILED', [{ field: 'unitPrice', reason: '0원 이상이어야 합니다.' }])
    if (body.name != null && duplicated(body.name, product.id)) return error('PRODUCT_NAME_DUPLICATED')
    const changes: Record<string, { before: unknown; after: unknown }> = {}
    if (body.name != null && body.name.trim() !== product.name) changes.name = { before: product.name, after: body.name.trim() }
    if (body.unitPrice != null && body.unitPrice !== product.unitPrice) changes.unitPrice = { before: product.unitPrice, after: body.unitPrice }
    if (body.name != null) product.name = body.name.trim()
    if (body.unit != null) product.unit = body.unit.trim()
    if (body.unitPrice != null) product.unitPrice = body.unitPrice
    if (body.description != null) product.description = body.description.trim() || null
    if (Object.keys(changes).length) recordAudit({ entityType: 'PRODUCT', entityId: product.id, eventType: 'UPDATED', actorType: 'MEMBER', actorId: currentMember(request).id, changes })
    return HttpResponse.json(product)
  }),

  http.post('/api/v1/products/:id/discontinue', ({ params, request }) => {
    const forbidden = adminOnly(request)
    if (forbidden) return forbidden
    const product = find(String(params.id))
    if (!product) return notFound()
    product.status = 'DISCONTINUED'
    recordAudit({ entityType: 'PRODUCT', entityId: product.id, eventType: 'DISCONTINUED', actorType: 'MEMBER', actorId: currentMember(request).id, changes: { status: { before: 'ACTIVE', after: 'DISCONTINUED' } } })
    return HttpResponse.json(product)
  }),

  http.post('/api/v1/products/:id/reactivate', ({ params, request }) => {
    const forbidden = adminOnly(request)
    if (forbidden) return forbidden
    const product = find(String(params.id))
    if (!product) return notFound()
    product.status = 'ACTIVE'
    recordAudit({ entityType: 'PRODUCT', entityId: product.id, eventType: 'REACTIVATED', actorType: 'MEMBER', actorId: currentMember(request).id, changes: { status: { before: 'DISCONTINUED', after: 'ACTIVE' } } })
    return HttpResponse.json(product)
  }),
]
