import { describe, expect, it } from 'vitest'
import { isValidElement } from 'react'
import { KeyedQuoteDetail } from './QuoteDetailPage'

/**
 * 견적 id가 바뀌면 상세를 새로 마운트한다 (#366).
 *
 * `quotes/:id` 안에서 id만 바뀌면 라우터가 같은 컴포넌트를 재사용해, 발송 → 복제로 넘어간 새 견적(작성 중)에
 * 앞 견적의 발송 완료 안내가 그대로 떴다. 마운트를 흉내 낼 DOM 도구가 없어 **key 규칙**으로 고정한다 —
 * React는 key가 다르면 이전 상태를 버리고 새로 마운트한다.
 * KeyedQuoteDetail은 훅이 없어 함수로 불러 돌려준 엘리먼트를 볼 수 있다.
 */
describe('KeyedQuoteDetail', () => {
  const keyOf = (id: string) => {
    const element = KeyedQuoteDetail({ id })
    return isValidElement(element) ? element.key : undefined
  }

  it('견적 id를 key로 건다', () => {
    expect(keyOf('3a65a46a-ed4a-416a-9e83-7b9c05a6b225')).toBe('3a65a46a-ed4a-416a-9e83-7b9c05a6b225')
  })

  it('다른 견적이면 key가 달라 앞 견적의 상태가 이어지지 않는다', () => {
    expect(keyOf('original')).not.toBe(keyOf('cloned'))
  })
})
