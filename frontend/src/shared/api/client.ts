/**
 * API 클라이언트 — TODO(E 1주차): 12-frontend-plan.md §6.3의 8개 항목을 여기서 구현한다.
 *
 * 필수 처리 (요약):
 *  1. access 만료(401) 시 재발급 큐잉 — refresh는 한 번만 호출, 나머지 요청은 대기 후 재시도
 *     (안 하면 회전 토큰 동시 사용 → REUSE_DETECTED로 세션 전체 폐기)
 *  2. REFRESH_TOKEN_NOT_ACTIVE(401) → 로그인 화면 이동 + "세션이 만료되었습니다" (AU-12)
 *  3. 에러 문구는 shared/api/errors.ts 상수(=API 명세서 부록)만 사용
 *  4. STALE_VERSION(409) → 쿼리 무효화 + "새로고침 후 다시 시도" 안내
 *  5. 404 통일 — 존재/권한 구별 금지 (SC-09)
 *  6. 인증 요청은 credentials: 'include' — refresh는 HttpOnly 쿠키(2jo_rt), 값을 읽지도 저장하지도 않는다
 *  7. access token은 메모리에만 — 부팅 시 /auth/refresh 1회로 복구
 *  8. 로그아웃 — 메모리 access 폐기 + 캐시 클리어 (쿠키는 서버가 지움)
 */
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'
