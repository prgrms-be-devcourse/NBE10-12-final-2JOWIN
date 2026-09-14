/**
 * 대시보드 월·기간 계산 — 날짜는 전부 KST `YYYY-MM-DD` 문자열이다.
 * 오늘을 인자로 받는다 — 화면은 `date(new Date().toISOString())`를 넘기고, 테스트는 날짜를 고정한다.
 */

export const monthOf = (day: string) => day.slice(0, 7)

export const isMonthParam = (value: string | null): value is string => /^\d{4}-\d{2}$/.test(value ?? '')

export function shiftMonth(month: string, delta: number) {
  const [y, m] = month.split('-').map(Number)
  return new Date(Date.UTC(y, m - 1 + delta, 1)).toISOString().slice(0, 7)
}

export const monthLabel = (month: string) => `${Number(month.slice(5, 7))}월`

/**
 * 실적 기본 기간 — **이번 달이면 1일~오늘, 지난 달이면 1일~말일** (#357).
 *
 * 서버의 기본값(`from`·`to` 생략 시 이달 1일~오늘, `DashboardController`)과 맞춘다.
 * 화면이 늘 말일까지 보내면 서버 기본값이 쓰이지 않고 기간 칸에 아직 오지 않은 날짜가 보였다.
 */
export function monthBounds(month: string, today: string) {
  const [y, m] = month.split('-').map(Number)
  const lastDay = new Date(Date.UTC(y, m, 0)).toISOString().slice(0, 10)
  return { from: `${month}-01`, to: month === monthOf(today) ? today : lastDay }
}
