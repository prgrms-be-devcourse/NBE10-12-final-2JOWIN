import { Card, Flex, Skeleton } from '@radix-ui/themes'

/** 표 로딩 — 행 수·열 폭만 받는다. 화면이 튀지 않게 실제 표와 비슷한 높이로 잡는다 */
export function TableSkeleton({ rows = 5, columns = [3, 1, 1, 1] }: { rows?: number; columns?: number[] }) {
  return (
    <Card>
      <Flex direction="column" gap="3" p="2">
        {Array.from({ length: rows }, (_, i) => (
          <Flex key={i} gap="4" align="center">
            {columns.map((flex, j) => (
              <Skeleton key={j} height="20px" style={{ flex }} />
            ))}
          </Flex>
        ))}
      </Flex>
    </Card>
  )
}
