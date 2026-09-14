import type { DashboardSummaryResponse } from '../../shared/api/types'

/** 요약에 보일 것이 하나도 없는지 — 이때만 딜 수를 따로 묻는다(평소 대시보드는 요청이 늘지 않는다) */
export function looksEmpty(summary: DashboardSummaryResponse) {
  const openDeals = summary.pipeline.reduce((sum, p) => sum + p.count, 0)
  return (
    openDeals + summary.monthWonCount === 0 &&
    summary.waitingQuotes.length === 0 &&
    summary.followUps.length === 0 &&
    summary.recentActivities.length === 0
  )
}

/**
 * 시작하기 체크리스트 (10 §6.2) — **딜이 한 건도 없을 때만** 띄운다 (#357).
 *
 * 요약은 진행 중 딜과 이달 성사만 센다. 그것만으로 판정하면 지난달 이전에 성사했거나 실패 딜만 남은 회사도
 * "처음 시작하는 회사"로 보였다. 딜 목록의 전체 건수(성사·실패 포함, 영업 담당자는 본인 담당)로 판정하고,
 * 건수를 아직 모르면 띄우지 않는다.
 */
export function showStartChecklist(summary: DashboardSummaryResponse, dealTotal: number | undefined) {
  return looksEmpty(summary) && dealTotal === 0
}
