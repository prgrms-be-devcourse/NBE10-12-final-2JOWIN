import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

/**
 * 목이 잡지 않은 요청만 백엔드로 넘긴다 (12-frontend-plan.md §5.3).
 *
 * MSW가 먼저 가로채므로, `VITE_MOCK_DOMAINS`에서 뺀 도메인의 요청만 여기까지 내려온다.
 * 덕분에 도메인 단위 전환이 코드 수정 없이 환경변수 하나로 끝난다.
 */
// 백엔드 주소 — 기본 8080. 워크트리마다 다른 포트로 백엔드를 띄울 때 `BACKEND_PROXY_TARGET`으로 바꾼다
// (브라우저 오리진은 그대로라 백엔드 CORS 설정을 건드리지 않아도 된다).
const target = process.env.BACKEND_PROXY_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': { target, changeOrigin: true },
      '/public': { target, changeOrigin: true },
      // 플랫폼 관리자 API (AU-08). `/admin`은 SPA 화면 경로라 `/admin/api`만 넘긴다
      '/admin/api': { target, changeOrigin: true },
    },
  },
})
