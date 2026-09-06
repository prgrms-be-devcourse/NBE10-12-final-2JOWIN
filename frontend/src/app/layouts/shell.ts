import type { ComponentType } from 'react'

/** 사이드바 상수·타입 — 컴포넌트 파일과 나눠 둔 이유는 Fast Refresh (상수와 컴포넌트를 한 파일에 두면 편집마다 전체 재평가) */

export interface Menu {
  to: string
  label: string
  icon: ComponentType<{ width?: string | number; height?: string | number }>
  end?: boolean
}

/** 사이드바 톤 — member: white + accent-a2 (10 §2.2) · admin: accent-a4 + 상단 accent 띠 */
export type SidebarTone = 'member' | 'admin'

export const SIDEBAR_WIDTH = { expanded: 240, collapsed: 64 } as const
/** 아이콘 셀 한 변 — 로고 마크·아이콘·버튼이 전부 이 정사각형 안에 가운데 정렬된다 */
export const CELL = 40
