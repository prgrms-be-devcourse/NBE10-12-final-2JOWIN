import { Box, Table, Text } from '@radix-ui/themes'
import { Link } from 'react-router'
import { DealStageBadge, Money } from '../../../shared/ui'
import type { DealStage } from '../../../shared/ui'
import { dateShort } from '../../../shared/lib/format'
import type { CustomerDealSummary } from '../../../shared/api/types'

/** 딜 이력 (CU-12). 성사는 주문 합계(wonAmount), 그 외는 예상 금액 (DL-18). 딜 상세는 C 담당 — 링크만 */
export function DealHistoryTable({ deals }: { deals: CustomerDealSummary[] }) {
  if (deals.length === 0) {
    return (
      <Box py="5">
        <Text as="p" size="2" color="gray" align="center">
          아직 딜이 없습니다. 딜 보드에서 이 고객사로 첫 딜을 만들어 보세요.
        </Text>
      </Box>
    )
  }

  return (
    <Table.Root variant="ghost" size="2">
      <Table.Header>
        <Table.Row>
          <Table.ColumnHeaderCell>딜</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell width="96px" align="center">단계</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell align="center" width="150px">
            금액
          </Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell width="96px" align="center">생성일</Table.ColumnHeaderCell>
        </Table.Row>
      </Table.Header>
      <Table.Body>
        {deals.map((deal) => {
          const won = deal.stage === 'WON'
          const amount = won ? deal.wonAmount : deal.expectedAmount
          return (
            <Table.Row key={deal.id} className="row-hover">
              <Table.RowHeaderCell>
                <Text asChild size="2" weight="medium">
                  <Link to={`/deals/${deal.id}`} style={{ color: 'inherit', textDecoration: 'none' }}>
                    {deal.title}
                  </Link>
                </Text>
              </Table.RowHeaderCell>
              <Table.Cell align="center">
                <DealStageBadge stage={deal.stage as DealStage} />
              </Table.Cell>
              <Table.Cell align="center">
                {amount === null ? (
                  <Text size="2" color="gray">
                    —
                  </Text>
                ) : (
                  <>
                    <Money value={amount} unit color={won ? undefined : 'gray'} />
                    {!won && (
                      <Text size="1" color="gray">
                        {' '}
                        예상
                      </Text>
                    )}
                  </>
                )}
              </Table.Cell>
              <Table.Cell align="center">
                <Text size="2" color="gray">
                  {dateShort(deal.createdAt)}
                </Text>
              </Table.Cell>
            </Table.Row>
          )
        })}
      </Table.Body>
    </Table.Root>
  )
}
