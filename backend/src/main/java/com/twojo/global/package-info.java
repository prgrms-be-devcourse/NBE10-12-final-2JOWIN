/**
 * 공통 모듈 (E) — ErrorCode·ErrorResponse·PageResponse·BaseTimeEntity·채번·설정.
 *
 * <p><b>OPEN 모듈</b>: 하위 패키지(error·response·jpa·sequence·config)까지 전 도메인이
 * 자유롭게 쓴다. 일반 도메인 모듈과 달리 내부 은닉을 적용하지 않는다 — 공통 유틸의 성격상
 * 전부가 공개 API다. (역방향 금지는 그대로: global은 도메인 모듈에 의존하지 않는다)
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.twojo.global;
