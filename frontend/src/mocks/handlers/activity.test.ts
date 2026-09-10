import { describe, expect, it, vi } from 'vitest'
import { activityHandlers } from './activity'
import { db, session } from '../store'
import { demoAccounts } from '../fixtures'
import type { ActivityResponse, ErrorResponse, PageResponse, TaskResponse } from '../../shared/api/types'

/**
 * 활동 이력 목이 백엔드(ActivityService · TaskService)와 같은 규칙으로 답하는지.
 *
 * #129에서 실 API로 넘기며 서버와 어긋난 것만 고정한다 — 목으로 개발하면 통과하고
 * 실 API에서 깨지던 자리들이다: 모르는 type은 400(전체 조회로 흘리지 않는다) ·
 * `done=false`는 미변경(완료 취소가 아니다) · 고객사 이력은 수동 기록만이고 담당 범위를 탄다 ·
 * 자동 기록의 작성자는 없을 때 null이다(빈 문자열이 아니다).
 */

vi.stubGlobal('location', { href: 'http://localhost/', origin: 'http://localhost' })

async function call(request: Request): Promise<Response> {
  for (const handler of activityHandlers) {
    const result = await handler.run({ request, requestId: crypto.randomUUID() })
    if (result?.response) return result.response
  }
  throw new Error(`no handler for ${request.method} ${request.url}`)
}

const [admin, salesRep] = demoAccounts
const auth = (account: (typeof demoAccounts)[number]) => ({ Authorization: `Bearer ${account.accessToken}` })

const get = (path: string, account: (typeof demoAccounts)[number]) =>
  call(new Request(`http://localhost/api/v1${path}`, { headers: auth(account) }))

const patch = (path: string, account: (typeof demoAccounts)[number], body: unknown) =>
  call(new Request(`http://localhost/api/v1${path}`, {
    method: 'PATCH', headers: { ...auth(account), 'Content-Type': 'application/json' }, body: JSON.stringify(body),
  }))

const list = async (path: string, account: (typeof demoAccounts)[number]) => {
  const res = await get(path, account)
  expect(res.status).toBe(200)
  return (await res.json()) as PageResponse<ActivityResponse>
}

/** 도담건설 — 딜 3건(2·8·13) 중 8·13만 박지훈 담당. 상담 기록은 딜 8에 2건 */
const DODAM = db.customers[0].id
const DEAL_DODAM_QUOTE = db.deals.find((d) => d.title === '도담건설 사무가구 납품')!.id
/** 한울에너지 — 딜 12는 박지훈 담당이 아니다. 상담 기록 1건이 거기 달려 있다 */
const HANUL = db.customers.find((c) => c.name === '한울에너지')!.id

/** 자동 기록을 한 줄 얹고 되돌린다 — db는 모듈 수명이라 테스트가 흔적을 남기면 안 된다 */
async function withAutoActivity(row: (typeof db.autoActivities)[number], run: () => Promise<void>) {
  db.autoActivities.push(row)
  try {
    await run()
  } finally {
    db.autoActivities.splice(db.autoActivities.indexOf(row), 1)
  }
}

describe('GET /deals/{id}/activities — 딜 타임라인 (AC-06·07)', () => {
  it('07에 없는 type은 400이다 — 오타를 전체 조회로 흘리면 필터가 걸린 줄 알고 본다', async () => {
    session.login(admin)
    const res = await get(`/deals/${DEAL_DODAM_QUOTE}/activities?type=CALL`, admin)
    expect(res.status).toBe(400)
    const body = (await res.json()) as ErrorResponse
    expect(body.code).toBe('VALIDATION_FAILED')
    expect(body.fieldErrors.map((f) => f.field)).toContain('type')
  })

  it('type이 없으면 수동과 자동을 시각 역순으로 합친다', async () => {
    session.login(admin)
    await withAutoActivity(
      { id: 'auto-merge', dealId: DEAL_DODAM_QUOTE, content: '견적을 발송했습니다 — Q-2608-014', authorMemberId: null, occurredAt: '2026-08-24T01:00:00Z' },
      async () => {
        const page = await list(`/deals/${DEAL_DODAM_QUOTE}/activities?size=100`, admin)
        expect(page.content.map((a) => a.type)).toContain('AUTO')
        expect(page.content.map((a) => a.type)).toContain('MANUAL')
        const times = page.content.map((a) => a.occurredAt)
        expect(times).toEqual([...times].sort().reverse())
      },
    )
  })

  it('자동 기록의 작성자는 사람이 없으면 null이다 — 빈 문자열이 아니고, authorActive는 true다', async () => {
    session.login(admin)
    await withAutoActivity(
      { id: 'auto-system', dealId: DEAL_DODAM_QUOTE, content: '단계를 이동했습니다 — CONSULT → QUOTE', authorMemberId: null, occurredAt: '2026-08-24T01:00:00Z' },
      async () => {
        const page = await list(`/deals/${DEAL_DODAM_QUOTE}/activities?type=AUTO&size=100`, admin)
        const row = page.content.find((a) => a.id === 'auto-system')!
        expect(row.authorMemberId).toBeNull()
        expect(row.authorMemberName).toBeNull()
        // 사람이 없는 줄에 "(퇴사)"가 붙으면 안 된다
        expect(row.authorActive).toBe(true)
      },
    )
  })
})

describe('GET /customers/{id}/activities — 고객사 이력 (AC-10)', () => {
  it('수동 기록만 돌려준다 — 딜 타임라인과 달리 자동 기록을 섞지 않는다', async () => {
    session.login(admin)
    await withAutoActivity(
      { id: 'auto-not-here', dealId: DEAL_DODAM_QUOTE, content: '견적을 발송했습니다', authorMemberId: null, occurredAt: '2026-08-24T01:00:00Z' },
      async () => {
        const page = await list(`/customers/${DODAM}/activities?size=100`, admin)
        expect(page.content.every((a) => a.type === 'MANUAL')).toBe(true)
        expect(page.content.map((a) => a.id)).not.toContain('auto-not-here')
      },
    )
  })

  it('영업 담당자는 담당 Deal의 상담만 본다 — 같은 고객사라도 남의 Deal 것은 안 보인다 (09 §59)', async () => {
    session.login(admin)
    const seenByAdmin = await list(`/customers/${HANUL}/activities?size=100`, admin)
    expect(seenByAdmin.totalElements).toBeGreaterThan(0)

    session.login(salesRep)
    const seenBySales = await list(`/customers/${HANUL}/activities?size=100`, salesRep)
    expect(seenBySales.totalElements).toBe(0)
  })
})

describe('PATCH /tasks/{id} — 완료 처리 (AC-09)', () => {
  it('done=false는 미변경이다 — 완료 취소는 03·07 어디에도 없다', async () => {
    session.login(admin)
    const done = db.tasks.find((t) => t.doneAt !== null)!
    const before = done.doneAt

    const res = await patch(`/tasks/${done.id}`, admin, { done: false })
    expect(res.status).toBe(200)
    expect(((await res.json()) as TaskResponse).doneAt).toBe(before)
  })

  it('완료는 멱등이다 — 두 번 눌러도 최초 완료 시각이 유지된다', async () => {
    session.login(admin)
    const done = db.tasks.find((t) => t.doneAt !== null)!
    const before = done.doneAt

    const res = await patch(`/tasks/${done.id}`, admin, { done: true })
    expect(((await res.json()) as TaskResponse).doneAt).toBe(before)
  })
})
