import { describe, expect, it } from 'vitest'
import { db, nextDocNo } from './store'

/**
 * 목 채번이 backend `DocumentNumberService`(PR #80)와 같은 규칙으로 번호를 내는지 —
 * 백엔드 `DocumentNumberServiceTest`의 케이스를 프론트 목에 그대로 옮겼다.
 * 목이 다른 번호를 내면 시연 데이터와 실 API가 갈라진다.
 */
describe('nextDocNo — document_sequence 채번 (PR #80)', () => {
  it('시드의 last_seq를 이어받는다 — 2608 카운터가 16이면 다음은 017', () => {
    expect(nextDocNo('QUOTE', '2026-08-30')).toBe('Q-2608-017')
    expect(nextDocNo('ORDER', '2026-08-30')).toBe('O-2608-004')
  })

  it('견적은 Q, 주문은 O — 달이 바뀌면 그 달의 카운터를 새로 심는다', () => {
    expect(nextDocNo('QUOTE', '2026-09-06')).toBe('Q-2609-001')
    expect(nextDocNo('QUOTE', '2026-09-06')).toBe('Q-2609-002')
    expect(nextDocNo('ORDER', '2026-09-06')).toBe('O-2609-001')
    // 8월 카운터는 9월 번호에 쓰이지 않았다
    expect(db.documentSequences.find((r) => r.docType === 'QUOTE' && r.yearMonth === '2608')?.lastSeq).toBe(17)
  })

  it('순번은 세 자리로 채우되 1000번째부터는 자리가 늘어난다', () => {
    const row = db.documentSequences.find((r) => r.docType === 'QUOTE' && r.yearMonth === '2609')!
    row.lastSeq = 999
    expect(nextDocNo('QUOTE', '2026-09-06')).toBe('Q-2609-1000')
  })

  it('회사가 다르면 다른 카운터를 본다 — 카운터 행은 회사·문서종류·연월로 유일하다', () => {
    const keys = db.documentSequences.map((r) => `${r.companyId}/${r.docType}/${r.yearMonth}`)
    expect(new Set(keys).size).toBe(keys.length)
  })
})
