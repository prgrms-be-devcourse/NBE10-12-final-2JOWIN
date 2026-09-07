import { useSearchParams } from 'react-router'
import { Box, Flex, Grid, IconButton, Skeleton, Text } from '@radix-ui/themes'
import { ChevronLeftIcon, ChevronRightIcon } from '@radix-ui/react-icons'
import { ErrorCallout, PageHeader } from '../../../shared/ui'
import { codeOf } from '../../../shared/api/client'
import { hasCompanyWideScope, useSession } from '../../../app/session'
import { useCompleteTask, useDashboardSummary } from '../hooks'
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
const thisMonth = () => new Date().toISOString().slice(0, 7)
const shiftMonth = (month: string, delta: number) => {
  const [y, m] = month.split('-').map(Number)
  const d = new Date(Date.UTC(y, m - 1 + delta, 1))
  return d.toISOString().slice(0, 7)
}
const monthLabel = (month: string) => `${Number(month.slice(5, 7))}월`
const monthBounds = (month: string) => {
  const [y, m] = month.split('-').map(Number)
  return { from: `${month}-01`, to: new Date(Date.UTC(y, m, 0)).toISOString().slice(0, 10) }
}

export function DashboardPage() {
  const session = useSession()
  const admin = hasCompanyWideScope(session)
  const [params, setParams] = useSearchParams()
  const month = /^\d{4}-\d{2}$/.test(params.get('month') ?? '') ? params.get('month')! : thisMonth()
  const bounds = monthBounds(month)
  const from = params.get('from') || bounds.from
  const to = params.get('to') || bounds.to

  const { data, isPending, isFetching, error, refetch } = useDashboardSummary(month)
  const completeTask = useCompleteTask()

  const update = (next: Record<string, string>) => {
    const merged = new URLSearchParams(params)
    for (const [key, value] of Object.entries(next)) {
      if (value) merged.set(key, value)
      else merged.delete(key)
    }
    setParams(merged, { replace: true })
  }

  const totalDeals = data ? data.pipeline.reduce((sum, p) => sum + p.count, 0) + data.monthWonCount : 0
  const empty = data && totalDeals === 0 && data.waitingQuotes.length === 0 && data.followUps.length === 0 && data.recentActivities.length === 0

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
            <IconButton variant="soft" color="gray" size="1" aria-label="다음 달" disabled={month >= thisMonth()} onClick={() => update({ month: shiftMonth(month, 1), from: '', to: '' })}>
              <ChevronRightIcon />
            </IconButton>
          </Flex>
        }
      />

      {error && <ErrorCallout code={codeOf(error)} onRetry={() => refetch()} />}

      {isPending ? (
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
