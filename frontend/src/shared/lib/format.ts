/**
 * 표시 포맷 — 금액·날짜의 단일 지점.
 *
 * 저장·전송은 UTC(ISO-8601), 표시 변환은 프론트 책임이다 (backend application.yml).
 * 화면에서 toLocaleString을 직접 부르지 않는다 — 자릿수·구분자가 화면마다 달라진다.
 */

const KST = 'Asia/Seoul'

// ── 금액 (08-dto.md §0: 원 단위 정수) ────────────────────────────────────────

/** 3,355,000 */
export const money = (won: number): string => won.toLocaleString('ko-KR')

/** 3,355,000원 */
export const wonText = (won: number): string => `${money(won)}원`

/**
 * 4,840만 — 대시보드 요약처럼 자리가 좁을 때만.
 * 견적서·주문 등 금액이 근거가 되는 곳에는 쓰지 않는다 (반올림이 숨는다).
 */
export function moneyShort(won: number): string {
  if (won >= 100_000_000) return `${trimZero(won / 100_000_000)}억`
  if (won >= 10_000) return `${money(Math.round(won / 10_000))}만`
  return money(won)
}

const trimZero = (n: number) => Number(n.toFixed(1)).toLocaleString('ko-KR')

// ── 날짜 ────────────────────────────────────────────────────────────────────

/** 2026-09-09 */
export const date = (iso: string): string => parts(iso).join('-')

/** 09-09 — 같은 해가 자명한 자리 */
export const dateShort = (iso: string): string => parts(iso).slice(1).join('-')

/** 2026년 9월 9일 — 고객 화면처럼 문장으로 읽히는 자리 */
export function dateLong(iso: string): string {
  const [y, m, d] = parts(iso)
  return `${y}년 ${Number(m)}월 ${Number(d)}일`
}

/** 2026-09-09 14:30 */
export function dateTime(iso: string): string {
  const t = new Intl.DateTimeFormat('ko-KR', {
    timeZone: KST, hour: '2-digit', minute: '2-digit', hour12: false,
  }).format(new Date(iso))
  return `${date(iso)} ${t}`
}

function parts(iso: string): [string, string, string] {
  const f = new Intl.DateTimeFormat('en-CA', {
    timeZone: KST, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date(iso))
  return f.split('-') as [string, string, string]
}

// ── 경과·잔여 일수 ──────────────────────────────────────────────────────────

/** 오늘(KST) 자정 기준 일수 차 — 미래가 양수 */
export function daysUntil(iso: string, now: Date = new Date()): number {
  const day = 86_400_000
  return Math.round((midnight(new Date(iso)) - midnight(now)) / day)
}

/** daysUntil의 반대 — 과거가 양수 (열람 후 며칠, 발송 후 며칠) */
export const daysSince = (iso: string, now?: Date): number => -daysUntil(iso, now)

const midnight = (d: Date) => Date.parse(`${date(d.toISOString())}T00:00:00Z`)

/**
 * 12일 남음 · 오늘 마감 · 3일 지남 — 유효기간·마감 표시.
 * 남은 기간은 amber, 지난 것은 red로 감싼다 (10-screen-design.md §2.3).
 */
export function remainingText(iso: string, now?: Date): string {
  const d = daysUntil(iso, now)
  if (d > 0) return `${d}일 남음`
  if (d === 0) return '오늘 마감'
  return `${-d}일 지남`
}
