/**
 * 고객 열람·승인 — 토큰이 곧 인증 (SC-07~09). 견적 상태는 quote 모듈의 커맨드 경유로만 변경
 *
 * <p>담당: D 이준형 · 요구사항: AP · 소유 테이블: quote_view_token · customer_inquiry
 * <p>모듈 규칙(docs/11-work-breakdown.md §7): 타 모듈은 이 패키지 루트의 공개 인터페이스만 사용한다.
 * 내부 구현(서비스·리포지토리·엔티티)은 하위 패키지에 두어 모듈 밖 접근을 차단한다.
 */
package com.twojo.approval;
