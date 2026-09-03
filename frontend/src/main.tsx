import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router'
import { Theme } from '@radix-ui/themes'
import '@radix-ui/themes/styles.css'
import './app/theme.css'
import { router } from './app/router'
import { setSessionExpiredHandler } from './shared/api/client'

const queryClient = new QueryClient()

// 세션이 끊기면 로그인 화면으로 — AU-12. 클라이언트는 라우터를 모르므로 여기서 주입한다
setSessionExpiredHandler(() => {
  queryClient.clear()   // 남의 데이터가 다음 로그인에 비치지 않게 (§6.3-8)
  if (window.location.pathname !== '/login') window.location.href = '/login'
})

async function enableMocking() {
  // 목 우선 개발 (12-frontend-plan.md §5) — 도메인 단위 전환은 VITE_MOCK_DOMAINS로 제어
  if (!import.meta.env.DEV) return
  const { worker } = await import('./mocks/browser')
  const { mockedDomains } = await import('./mocks/handlers')
  // 어느 도메인이 목이고 어느 도메인이 실 API인지 콘솔에 남긴다 — 전환 중 혼선을 줄인다
  console.info('[2JO] 목 도메인:', mockedDomains.join(', ') || '없음 (전부 실 API)')
  // 목이 없는 도메인은 그대로 통과시켜 Vite 프록시가 백엔드로 넘긴다
  return worker.start({ onUnhandledRequest: 'bypass' })
}

enableMocking().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      {/* 토큰 확정: accent blue · gray slate · radius medium · light 고정 (10-screen-design.md §2.2, §9) */}
      <Theme accentColor="blue" grayColor="slate" radius="medium" appearance="light">
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </Theme>
    </StrictMode>,
  )
})
