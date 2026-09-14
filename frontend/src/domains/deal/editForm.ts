import type { DealDetailResponse } from '../../shared/api/types'

/** 딜 수정 폼이 다루는 필드 — 입력칸의 문자열 그대로 둔다 */
export interface EditFields {
  title: string
  expectedAmount: string
  dueDate: string
}

export const toEditFields = (deal: Pick<DealDetailResponse, 'title' | 'expectedAmount' | 'dueDate'>): EditFields => ({
  title: deal.title,
  expectedAmount: deal.expectedAmount === null ? '' : String(deal.expectedAmount),
  dueDate: deal.dueDate ?? '',
})

/**
 * STALE_VERSION [새로고침] 뒤 폼을 맞춘다 (#356) — **사용자가 손대지 않은 필드만** 서버의 새 값으로 바꾼다.
 *
 * 새로고침이 폼을 통째로 두면, 다른 사람이 바꾼 필드가 옛 입력값 그대로 다시 저장돼 조용히 되돌아간다.
 * 반대로 통째로 덮으면 방금 입력한 내용이 사라진다. "손댔다"는 폼을 연 때(또는 직전 새로고침 때)의 값과 다른지로 본다 —
 * 고쳤다가 원래대로 되돌린 필드는 손대지 않은 것으로 친다.
 */
export function rebaseEditFields(current: EditFields, base: EditFields, next: EditFields): EditFields {
  const pick = (key: keyof EditFields) => (current[key] === base[key] ? next[key] : current[key])
  return { title: pick('title'), expectedAmount: pick('expectedAmount'), dueDate: pick('dueDate') }
}
