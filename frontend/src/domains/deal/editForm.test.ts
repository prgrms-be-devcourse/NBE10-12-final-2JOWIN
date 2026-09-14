import { describe, expect, it } from 'vitest'
import { rebaseEditFields, toEditFields } from './editForm'

/**
 * 딜 수정 폼 새로고침 (#356) — 손대지 않은 필드만 서버 값으로 바뀐다.
 * 로컬 확인에서 다른 사람이 API로 바꾼 예상 금액(9,000,000)이 옛 입력값(8,800,000)으로 되돌아갔다.
 */
describe('rebaseEditFields', () => {
  const base = { title: '성원산업 비품 정기납품', expectedAmount: '8800000', dueDate: '2026-08-30' }

  it('손대지 않은 필드는 서버의 새 값으로 바뀐다 — 다른 사람의 변경이 옛 값으로 덮이지 않는다', () => {
    const current = { ...base, title: '성원산업 비품 정기납품 (수정)' }
    const next = { ...base, expectedAmount: '9000000' }

    expect(rebaseEditFields(current, base, next)).toEqual({
      title: '성원산업 비품 정기납품 (수정)',
      expectedAmount: '9000000',
      dueDate: '2026-08-30',
    })
  })

  it('손댄 필드는 서버 값이 바뀌었어도 입력값을 지킨다', () => {
    const current = { ...base, expectedAmount: '9500000' }
    const next = { ...base, expectedAmount: '9000000', dueDate: '2026-09-15' }

    expect(rebaseEditFields(current, base, next)).toEqual({
      title: base.title,
      expectedAmount: '9500000',
      dueDate: '2026-09-15',
    })
  })

  it('고쳤다가 원래대로 되돌린 필드는 손대지 않은 것으로 친다', () => {
    const current = { ...base }
    const next = { ...base, title: '새 제목' }

    expect(rebaseEditFields(current, base, next).title).toBe('새 제목')
  })
})

describe('toEditFields', () => {
  it('미정(null)인 금액·마감일은 빈 입력칸이 된다', () => {
    expect(toEditFields({ title: '제목', expectedAmount: null, dueDate: null })).toEqual({ title: '제목', expectedAmount: '', dueDate: '' })
  })
})
