import { api } from '../../shared/api/client'
import type { CreateProductRequest, PageParams, PageResponse, ProductResponse, UpdateProductRequest } from '../../shared/api/types'
import type { ProductStatus } from '../../shared/ui/status'

/** 상품 카탈로그 API — product/controller/ProductController · 07 §B (PR). 호출은 이 파일 안에서만 한다 (12 §8). */

/** GET /api/v1/products?status=&page=&size= — 전사 공유 (PR-03·10). 정렬은 name ASC 고정 */
export async function fetchProducts(params: PageParams & { status?: ProductStatus } = {}) {
  const { data } = await api.get<PageResponse<ProductResponse>>('/products', { params })
  return data
}

/** POST /api/v1/products — 기업 관리자 · 회사 내 이름 유일 (PR-01·02·09) */
export async function createProduct(body: CreateProductRequest) {
  const { data } = await api.post<ProductResponse>('/products', body)
  return data
}

/** PATCH /api/v1/products/{id} — 기존 견적 무영향 (PR-04·08) */
export async function updateProduct(id: string, body: UpdateProductRequest) {
  const { data } = await api.patch<ProductResponse>(`/products/${id}`, body)
  return data
}

/** POST /api/v1/products/{id}/discontinue (PR-05·07) */
export async function discontinueProduct(id: string) {
  const { data } = await api.post<ProductResponse>(`/products/${id}/discontinue`)
  return data
}

/** POST /api/v1/products/{id}/reactivate */
export async function reactivateProduct(id: string) {
  const { data } = await api.post<ProductResponse>(`/products/${id}/reactivate`)
  return data
}
