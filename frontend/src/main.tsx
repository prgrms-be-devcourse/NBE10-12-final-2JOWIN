import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router'
import { Theme } from '@radix-ui/themes'
import '@radix-ui/themes/styles.css'
import { router } from './app/router'

const queryClient = new QueryClient()

async function enableMocking() {
  // 목 우선 개발 (12-frontend-plan.md §5) — 도메인 단위 전환은 VITE_MOCK_DOMAINS로 제어
  if (!import.meta.env.DEV) return
  const { worker } = await import('./mocks/browser')
  return worker.start({ onUnhandledRequest: 'bypass' })
}

enableMocking().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      {/* 토큰 확정: accent indigo · gray slate · radius medium · light 고정 (10-screen-design.md §2.2, §9) */}
      <Theme accentColor="indigo" grayColor="slate" radius="medium" appearance="light">
        <QueryClientProvider client={queryClient}>
          <RouterProvider router={router} />
        </QueryClientProvider>
      </Theme>
    </StrictMode>,
  )
})
