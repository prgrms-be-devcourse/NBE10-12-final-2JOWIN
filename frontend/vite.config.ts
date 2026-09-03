import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

/**
 * 목이 잡지 않은 요청만 백엔드로 넘긴다 (12-frontend-plan.md §5.3).
 *
 * MSW가 먼저 가로채므로, `VITE_MOCK_DOMAINS`에서 뺀 도메인의 요청만 여기까지 내려온다.
 * 덕분에 도메인 단위 전환이 코드 수정 없이 환경변수 하나로 끝난다.
 */
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/public': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
