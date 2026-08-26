/**
 * 인증 — 로그인·refresh 회전·재설정·잠금. AccessContext와 필터 체인 3분리의 소유처
 *
 * <p>담당: A 조민석 · 요구사항: AU · SC 공통 기반 · 소유 테이블: refresh_token · password_reset_token · login_attempt
 * <p>모듈 규칙(docs/11-work-breakdown.md §7): 타 모듈은 이 패키지 루트의 공개 인터페이스만 사용한다.
 * 내부 구현(서비스·리포지토리·엔티티)은 하위 패키지에 두어 모듈 밖 접근을 차단한다.
 */
package com.twojo.auth;
