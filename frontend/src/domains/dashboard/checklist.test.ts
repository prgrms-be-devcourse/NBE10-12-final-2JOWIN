import { describe, expect, it } from 'vitest'
import type { DashboardSummaryResponse } from '../../shared/api/types'
import { looksEmpty, showStartChecklist } from './checklist'

const emptySummary: DashboardSummaryResponse = {
  pipeline: [
    { stage: 'LEAD', count: 0, expectedAmountSum: 0 },
    { stage: 'CONSULT', count: 0, expectedAmountSum: 0 },
    { stage: 'QUOTE', count: 0, expectedAmountSum: 0 },
    { stage: 'NEGOTIATION', count: 0, expectedAmountSum: 0 },
  ],
  monthWonAmount: 0,
  monthWonCount: 0,
  waitingQuotes: [],
  followUps: [],
  recentActivities: [],
}

describe('시작하기 체크리스트 (#357)', () => {
  it('딜이 한 건도 없으면 띄운다', () => {
    expect(showStartChecklist(emptySummary, 0)).toBe(true)
  })

  it('요약이 비어도 지난달 성사·실패 딜이 남아 있으면 띄우지 않는다', () => {
    expect(looksEmpty(emptySummary)).toBe(true)
    expect(showStartChecklist(emptySummary, 2)).toBe(false)
  })

  it('딜 수를 아직 모르면 띄우지 않는다', () => {
    expect(showStartChecklist(emptySummary, undefined)).toBe(false)
  })

  it('이달 성사나 진행 중 딜이 있으면 비어 보이지 않는다', () => {
    expect(looksEmpty({ ...emptySummary, monthWonCount: 1 })).toBe(false)
    expect(looksEmpty({ ...emptySummary, pipeline: [{ stage: 'LEAD', count: 1, expectedAmountSum: 0 }] })).toBe(false)
  })
})
