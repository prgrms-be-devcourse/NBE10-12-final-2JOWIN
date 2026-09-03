import { Button, Callout, Flex } from '@radix-ui/themes'
import { ExclamationTriangleIcon, InfoCircledIcon } from '@radix-ui/react-icons'
import { messageOf } from '../api/errors'

interface Props {
  /** 서버가 준 ErrorResponse.code */
  code?: string
  /** STALE_VERSION일 때 재조회 — 넘기면 [새로고침] 버튼이 붙는다 */
  onRetry?: () => void
}

/**
 * 에러 표시 (10-screen-design.md §6.3).
 *
 * 문구를 여기서 쓰지 않는다 — API 명세서 부록에서 온 상수만 쓴다.
 * 색은 뜻을 가른다 (§2.3): `STALE_VERSION`은 "확인하고 다시 하면 된다"이므로 amber,
 * 나머지 실패는 red. amber와 red를 섞지 않는 것이 규칙이다.
 */
export function ErrorCallout({ code, onRetry }: Props) {
  const recoverable = code === 'STALE_VERSION'
  return (
    <Callout.Root color={recoverable ? 'amber' : 'red'} role="alert" my="3">
      <Callout.Icon>
        {recoverable ? <InfoCircledIcon /> : <ExclamationTriangleIcon />}
      </Callout.Icon>
      <Flex align="center" justify="between" gap="3" width="100%">
        <Callout.Text>{messageOf(code)}</Callout.Text>
        {recoverable && onRetry && (
          <Button size="1" variant="soft" color="amber" onClick={onRetry}>
            새로고침
          </Button>
        )}
      </Flex>
    </Callout.Root>
  )
}
