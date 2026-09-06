import { Tooltip } from '@radix-ui/themes'
import { BellIcon } from '@radix-ui/react-icons'

/** 알림 벨 자리 — 알림 도메인 PR에서 미읽음 배지 + 최근 5건 드롭다운으로 교체된다 (10 §6.4, NT-08) */
export function NotificationBell() {
  return (
    <Tooltip content="알림">
      <button type="button" className="icon-cell" aria-label="알림">
        <BellIcon width="18" height="18" />
      </button>
    </Tooltip>
  )
}
