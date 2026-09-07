import { Badge, Code, Dialog, Flex, Skeleton, Table, Text } from '@radix-ui/themes'
import { ErrorCallout, AUDIT_ACTOR_TYPE_LABEL } from '../../../shared/ui'
import { codeOf } from '../../../shared/api/client'
import { dateTime } from '../../../shared/lib/format'
import { useAuditLogDetail } from '../hooks'

interface Props {
  id: string | null
  onOpenChange: (open: boolean) => void
}

/** 값 표시 — null은 비어 있음, 객체는 JSON. 비밀번호·토큰은 저장 규약상 여기 오지 않는다 (08 payload 규약) */
function renderValue(value: unknown) {
  if (value === null || value === undefined) return <Text size="2" color="gray">—</Text>
  if (typeof value === 'object') return <Code size="1" variant="ghost">{JSON.stringify(value)}</Code>
  return <Text size="2">{String(value)}</Text>
}

/**
 * 감사 로그 상세 (AC-11 "무엇을 변경했는지"의 실체 · 08 AuditLogDetailResponse).
 * payload 규약: 변경된 필드만 {before, after}. 상세는 별도 조회라 목록 행 클릭 때만 부른다.
 */
export function AuditLogDetailDialog({ id, onOpenChange }: Props) {
  const { data, isPending, error } = useAuditLogDetail(id)

  return (
    <Dialog.Root open={id !== null} onOpenChange={onOpenChange}>
      <Dialog.Content maxWidth="560px">
        <Dialog.Title>변경 상세</Dialog.Title>
        {isPending ? (
          <Flex direction="column" gap="3" mt="3">
            <Skeleton height="20px" width="60%" />
            <Skeleton height="120px" />
          </Flex>
        ) : error || !data ? (
          <ErrorCallout code={codeOf(error)} />
        ) : (
          <>
            <Dialog.Description size="2" color="gray">
              {dateTime(data.occurredAt)} · {AUDIT_ACTOR_TYPE_LABEL[data.actorType]}
              {data.actorName ? ` ${data.actorName}` : ''}
            </Dialog.Description>

            <Flex align="center" gap="2" mt="3" wrap="wrap">
              <Badge color="gray" variant="soft">{data.entityType}</Badge>
              <Badge color="blue" variant="soft">{data.eventType}</Badge>
              <Code size="1" variant="ghost" color="gray">{data.entityId}</Code>
            </Flex>

            {Object.keys(data.changes).length === 0 ? (
              <Text as="p" size="2" color="gray" mt="4">
                기록된 필드 변경이 없습니다.
              </Text>
            ) : (
              <Table.Root variant="surface" size="1" mt="4">
                <Table.Header>
                  <Table.Row>
                    <Table.ColumnHeaderCell width="140px">필드</Table.ColumnHeaderCell>
                    <Table.ColumnHeaderCell>변경 전</Table.ColumnHeaderCell>
                    <Table.ColumnHeaderCell>변경 후</Table.ColumnHeaderCell>
                  </Table.Row>
                </Table.Header>
                <Table.Body>
                  {Object.entries(data.changes).map(([field, change]) => (
                    <Table.Row key={field}>
                      <Table.RowHeaderCell>
                        <Code size="1" variant="ghost">{field}</Code>
                      </Table.RowHeaderCell>
                      <Table.Cell>{renderValue(change.before)}</Table.Cell>
                      <Table.Cell>{renderValue(change.after)}</Table.Cell>
                    </Table.Row>
                  ))}
                </Table.Body>
              </Table.Root>
            )}
          </>
        )}
      </Dialog.Content>
    </Dialog.Root>
  )
}
