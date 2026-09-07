import { EmptyState } from './EmptyState'
import { ErrorCallout } from './ErrorCallout'

interface Props {
  /** ApiError.code — RESOURCE_NOT_FOUND면 빈 상태 안내, 그 외는 Callout */
  code: string
  backLabel: string
  onBack: () => void
  onRetry?: () => void
}

/**
 * 상세 조회 실패 — 404는 존재 여부를 구별하지 않는 문구 + 목록으로 (SC-09 · 10 §5.9).
 * 권한 밖 리소스도 404로 오므로(09 전역 규칙) "권한이 없다"고 말하지 않는다.
 */
export function NotFound({ code, backLabel, onBack, onRetry }: Props) {
  if (code === 'RESOURCE_NOT_FOUND' || code === 'ACTIVITY_NOT_AUTHOR') {
    return (
      <EmptyState
        title="요청한 대상을 찾을 수 없습니다"
        description="삭제되었거나 주소가 잘못되었을 수 있습니다."
        action={{ label: backLabel, onClick: onBack }}
      />
    )
  }
  return <ErrorCallout code={code} onRetry={onRetry} />
}
