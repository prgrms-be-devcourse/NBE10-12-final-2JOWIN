/**
 * 구성원 도메인 이벤트 — 타 모듈 리스너가 파라미터 타입으로 참조한다.
 *
 * <p>{@code member}는 노출 선언이 없어 기본 CLOSED다. 하위 패키지는 모듈 내부로 취급되므로
 * 이 선언이 없으면 다른 모듈이 이벤트 타입을 import하는 순간 모듈 검증이 막는다. 이벤트는
 * 받는 쪽이 타입을 알아야 성립하는 계약이라 여기만 열어 둔다 — 서비스·엔티티·리포지토리는
 * 그대로 닫혀 있다.
 */
@org.springframework.modulith.NamedInterface("event")
package com.twojo.member.event;
