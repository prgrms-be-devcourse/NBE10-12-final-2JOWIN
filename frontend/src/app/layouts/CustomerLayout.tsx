import { Box } from '@radix-ui/themes'
import { Outlet } from 'react-router'

/**
 * 고객 열람 페이지 레이아웃 (10-screen-design.md §1 · §5.6).
 *
 * **구성원 앱의 레이아웃을 절대 상속하지 않는다** — 상단 내비·탭이 없다.
 * 고객에게 이건 웹앱이 아니라 받은 문서다. 그래서 가운데 정렬된 카드 하나만 놓는다.
 *
 * 메일 링크는 폰에서 열리므로 **여기만 반응형이 필수**다 (§9-4).
 */
export function CustomerLayout() {
  return (
    <Box className="customer-page">
      <Box mx="auto" width="100%" style={{ maxWidth: 720 }}>
        <Outlet />
      </Box>
    </Box>
  )
}
