import { describe, expect, it } from 'vitest'
import { isMonthParam, monthBounds, shiftMonth } from './period'

describe('monthBounds — 실적 기본 기간 (#357)', () => {
  it('이번 달은 1일부터 오늘까지 — 서버 기본값과 같다', () => {
    expect(monthBounds('2026-09', '2026-09-14')).toEqual({ from: '2026-09-01', to: '2026-09-14' })
  })

  it('지난 달은 1일부터 말일까지', () => {
    expect(monthBounds('2026-08', '2026-09-14')).toEqual({ from: '2026-08-01', to: '2026-08-31' })
  })

  it('윤년 2월 말일은 29일', () => {
    expect(monthBounds('2028-02', '2028-03-01')).toEqual({ from: '2028-02-01', to: '2028-02-29' })
  })
})

describe('shiftMonth · isMonthParam', () => {
  it('해를 넘겨 이동한다', () => {
    expect(shiftMonth('2026-01', -1)).toBe('2025-12')
    expect(shiftMonth('2026-12', 1)).toBe('2027-01')
  })

  it('YYYY-MM만 월 쿼리로 받는다', () => {
    expect(isMonthParam('2026-09')).toBe(true)
    expect(isMonthParam('2026-9')).toBe(false)
    expect(isMonthParam(null)).toBe(false)
  })
})
