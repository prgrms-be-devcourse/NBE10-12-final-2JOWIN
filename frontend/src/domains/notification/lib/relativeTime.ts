import { dateShort } from '../../../shared/lib/format'

/**
 * 상대 시각 — "방금 전 · 5분 전 · 2시간 전 · 어제 · 3일 전", 7일이 넘으면 날짜.
 * 알림·최근 활동처럼 "얼마나 됐는지"가 정확한 시각보다 중요한 자리에만 쓴다.
 * TODO: shared/lib/format.ts로 옮기는 것이 맞다 — 이 작업 범위에서는 알림 도메인에 둔다.
 */
export function relativeTime(iso: string, now: Date = new Date()): string {
  const diff = now.getTime() - new Date(iso).getTime()
  const minute = 60_000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '방금 전'
  if (diff < hour) return `${Math.floor(diff / minute)}분 전`
  if (diff < day) return `${Math.floor(diff / hour)}시간 전`
  const days = Math.floor(diff / day)
  if (days === 1) return '어제'
  if (days < 7) return `${days}일 전`
  return dateShort(iso)
}
