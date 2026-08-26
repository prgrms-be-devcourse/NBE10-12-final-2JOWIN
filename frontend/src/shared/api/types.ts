/**
 * DTO 미러 — docs/08-dto.md의 record와 1:1로 유지한다 (12-frontend-plan.md §5.1).
 * 규칙: UUID=string · 금액=number(원 단위 정수) · 날짜/시각=ISO-8601 string.
 * 화면에서 임의 타입 정의 금지 — 목과 화면이 같은 타입을 쓴다.
 */

// ── 공통 (08-dto.md §0)
export interface ErrorResponse {
  code: string
  message: string
  fieldErrors: { field: string; reason: string }[]
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// TODO: 각 도메인 담당이 자기 도메인 DTO를 docs/08-dto.md에서 옮겨 적는다.
