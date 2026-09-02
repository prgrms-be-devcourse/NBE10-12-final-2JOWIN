/// <reference types="vite/client" />

/** 프로젝트가 쓰는 환경변수 — `.env.development` 참조 */
interface ImportMetaEnv {
  /** API 기본 경로. **상대 경로여야 MSW가 가로챈다** (12-frontend-plan.md §5.3) */
  readonly VITE_API_BASE_URL?: string
  /** 목으로 처리할 도메인 목록(쉼표 구분). 여기서 빼면 실 API로 간다 */
  readonly VITE_MOCK_DOMAINS?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
