import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CreateProductRequest, PageParams, UpdateProductRequest } from '../../shared/api/types'
import type { ProductStatus } from '../../shared/ui/status'
import { createProduct, discontinueProduct, fetchProducts, reactivateProduct, updateProduct } from './api'

/** 상품 Query 훅 — queryKey 규약 `[도메인, 리소스, 파라미터]` (12 §6.4) */

export type ProductListParams = PageParams & { status?: ProductStatus }

export const productKeys = {
  all: ['product'] as const,
  list: (params: ProductListParams) => ['product', 'list', params] as const,
}

export function useProductList(params: ProductListParams) {
  return useQuery({
    queryKey: productKeys.list(params),
    queryFn: () => fetchProducts(params),
    placeholderData: (previous) => previous,
  })
}

/** 등록·수정·중지·재개 — 어느 것이든 목록 전체를 무효화한다 (정렬이 이름순이라 위치가 바뀔 수 있다) */
export function useProductMutations() {
  const queryClient = useQueryClient()
  const refresh = () => queryClient.invalidateQueries({ queryKey: productKeys.all })

  const create = useMutation({ mutationFn: (body: CreateProductRequest) => createProduct(body), onSuccess: refresh })
  const update = useMutation({
    mutationFn: ({ id, body }: { id: string; body: UpdateProductRequest }) => updateProduct(id, body),
    onSuccess: refresh,
  })
  const discontinue = useMutation({ mutationFn: (id: string) => discontinueProduct(id), onSuccess: refresh })
  const reactivate = useMutation({ mutationFn: (id: string) => reactivateProduct(id), onSuccess: refresh })

  return { create, update, discontinue, reactivate }
}
