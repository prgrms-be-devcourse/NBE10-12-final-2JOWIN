import { useSearchParams } from 'react-router'
import { Box, Flex, Grid, IconButton, Skeleton, Text } from '@radix-ui/themes'
import { ChevronLeftIcon, ChevronRightIcon } from '@radix-ui/react-icons'
import { ErrorCallout, PageHeader } from '../../../shared/ui'
import { codeOf } from '../../../shared/api/client'
import { date } from '../../../shared/lib/format'
import { hasCompanyWideScope, useSession } from '../../../app/session'
import { useDealList } from '../../deal/hooks'
import { useCompleteTask, useDashboardSummary } from '../hooks'
import { isMonthParam, monthBounds, monthLabel, monthOf, shiftMonth } from '../period'
import { looksEmpty, showStartChecklist } from '../checklist'
import { PipelineCards } from '../components/PipelineCards'
import { PipelineChart } from '../components/PipelineChart'
import { WaitingQuotes } from '../components/WaitingQuotes'
import { FollowUps } from '../components/FollowUps'
import { RecentActivities } from '../components/RecentActivities'
import { PerformanceSection } from '../components/PerformanceSection'
import { StartChecklist } from '../components/StartChecklist'

/**
 * 대시보드 — 로그인 후 진입점 (10 §5.1 · DB-01~08 · 커트라인 2번).
 *
 * - 파이프라인 4칸 + 이달 성사 / 응답 대기 · 후속 필요 / 최근 활동 — `GET /dashboard/summary?month=`
 * - 영업 담당자는 본인 담당 기준 집계 (🔶). 기업 관리자만 담당자별 실적·전환율 섹션 (`/dashboard/performance`)
 * - 월 이동·실적 기간은 URL 쿼리 (`month` · `from`·`to`) — 새로고침·링크 공유가 그대로 동작한다
 * - 딜이 하나도 없으면 시작하기 체크리스트 (§6.2)
 */
export function DashboardPage() {
  const session = useSession()
  const admin = hasCompanyWideScope(session)
  const [params, setParams] = useSearchParams()
  // 오늘은 KST 기준 — UTC로 자르면 매달 1일 아침 9시 전에는 지난달이 뜬다
  const today = date(new Date().toISOString())
  const monthParam = params.get('month')
  const month = isMonthParam(monthParam) ? monthParam : monthOf(today)
  const bounds = monthBounds(month, today)
  const from = params.get('from') || bounds.from
  const to = params.get('to') || bounds.to

  const { data, isPending, isFetching, error, refetch } = useDashboardSummary(month)
  const completeTask = useCompleteTask()
  // 요약이 비어 보일 때만 딜 수를 묻는다 — 지난달 성사·실패 딜만 남은 회사를 체크리스트로 보내지 않는다 (#357)
  const summaryEmpty = data ? looksEmpty(data) : false
  const dealCount = useDealList({ size: 1 }, summaryEmpty)

  const update = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value) merged.set(key, value)
      else merged.delete(key)
    }
    setParams(merged, { replace: true })
  }

  const empty = data ? showStartChecklist(data, dealCount.data?.totalElements) : false
  // 비어 보이는 요약에서 딜 수를 기다리는 동안은 체크리스트도 빈 대시보드도 그리지 않는다 — 한쪽이 번쩍 보였다 바뀌지 않게
  const checkingDeals = summaryEmpty && dealCount.isPending

  return (
    <>
      <PageHeader
        title="대시보드"
        description={admin ? '회사 전체 현황입니다.' : `${session.name} 님이 담당하는 딜 기준입니다.`}
        actions={
          <Flex align="center" gap="2">
            <IconButton variant="soft" color="gray" size="1" aria-label="이전 달" onClick={() => update({ month: shiftMonth(month, -1), from: '', to: '' })}>
              <ChevronLeftIcon />
            </IconButton>
            <Text size="2" weight="medium" style={{ fontVariantNumeric: 'tabular-nums', minWidth: 64, textAlign: 'center' }}>
              {month.replace('-', '.')}
            </Text>
            <IconButton variant="soft" color="gray" size="1" aria-label="다음 달" disabled={month >= monthOf(today)} onClick={() => update({ month: shiftMonth(month, 1), from: '', to: '' })}>
              <ChevronRightIcon />
            </IconButton>
          </Flex>
        }
      />

      {error && <ErrorCallout code={codeOf(error)} onRetry={() => refetch()} />}

      {isPending || checkingDeals ? (
        <DashboardSkeleton />
      ) : empty ? (
        <StartChecklist canInvite={admin} />
      ) : (
        data && (
          <Box className="enter-fade" style={{ opacity: isFetching ? 0.7 : 1, transition: 'opacity var(--motion-fast)' }}>
            <Box mb="4">
              <PipelineCards pipeline={data.pipeline} monthWonAmount={data.monthWonAmount} monthWonCount={data.monthWonCount} monthLabel={monthLabel(month)} />
            </Box>

            {/* 크기 비교는 차트로, 행동이 필요한 목록은 목록으로 — 숫자 타일(위)과 막대(아래)가 같은 값을 두 형태로 보여준다 */}
            <Grid columns={{ initial: '1', md: '2' }} gap="4" mb="4">
              <PipelineChart pipeline={data.pipeline} />
              <WaitingQuotes quotes={data.waitingQuotes} />
            </Grid>

            <Grid columns={{ initial: '1', md: '2' }} gap="4" mb="4">
              <FollowUps followUps={data.followUps} onComplete={(taskId) => completeTask.mutate(taskId)} completing={completeTask.isPending} />
              <RecentActivities activities={data.recentActivities} />
            </Grid>
            {completeTask.error && <ErrorCallout code={codeOf(completeTask.error)} />}

            {/* 담당자별 실적·전환율 — 기업 관리자만 (DB-06~08, §3.2) */}
            {admin && <PerformanceSection from={from} to={to} onRangeChange={(range) => update({ from: range.from, to: range.to })} />}
          </Box>
        )
      )}
    </>
  )
}

function DashboardSkeleton() {
  return (
    <Box>
      <Grid columns={{ initial: '2', sm: '4', md: '6' }} gap="3" mb="4">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} height="88px" />
        ))}
        <Skeleton height="88px" style={{ gridColumn: 'span 2' }} />
      </Grid>
      <Grid columns={{ initial: '1', md: '2' }} gap="4" mb="4">
        <Skeleton height="220px" />
        <Skeleton height="220px" />
      </Grid>
      <Skeleton height="160px" />
    </Box>
  )
}
