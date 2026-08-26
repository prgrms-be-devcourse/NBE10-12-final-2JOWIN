/**
 * 현황 대시보드 — 집계는 C의 SalesStatsQuery·QuoteQuery 경유 (직접 조회 금지)
 *
 * <p>담당: D 이준형 · 요구사항: DB(현황) · 소유 테이블: (조회 전용 — 소유 테이블 없음)
 * <p>모듈 규칙(docs/11-work-breakdown.md §7): 타 모듈은 이 패키지 루트의 공개 인터페이스만 사용한다.
 * 내부 구현(서비스·리포지토리·엔티티)은 하위 패키지에 두어 모듈 밖 접근을 차단한다.
 */
package com.twojo.dashboard;
