/**
 * 알림 — 인앱(폴링 30초)·시스템 메일·배치 (email_log UNIQUE가 이중 발송 차단)
 *
 * <p>담당: D 이준형 · 요구사항: NT · 소유 테이블: notification · email_log · notification_setting
 * <p>모듈 규칙(docs/11-work-breakdown.md §7): 타 모듈은 이 패키지 루트의 공개 인터페이스만 사용한다.
 * 내부 구현(서비스·리포지토리·엔티티)은 하위 패키지에 두어 모듈 밖 접근을 차단한다.
 */
package com.twojo.notification;
