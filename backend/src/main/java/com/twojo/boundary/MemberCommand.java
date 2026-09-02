package com.twojo.boundary;

import java.time.Instant;
import java.util.UUID;

/**
 * 구성원 쓰기 계약 — 구현은 member 모듈, 소비는 auth 모듈 (11 §7.2. 둘 다 A 소유).
 *
 * <p>조회의 {@link MemberQuery}와 나눠 둔다. 이름이 곧 방향이라, Query에 쓰기를 섞으면
 * 소비자가 부작용 있는 호출을 조회로 오해한다. QuoteCommand·ViewTokenCommand와 같은 결이다.
 *
 * <p><b>auth가 member를 직접 참조하지 않게 하려고 여기에 둔다.</b> SessionRevoker가
 * member·onboarding에서 auth로 들어오는 방향을 이미 잡고 있어(MB-09·10 · ON-08·09),
 * 반대 방향을 직접 이으면 모듈 순환이 되어 Modulith 검증이 CI에서 막는다.
 */
public interface MemberCommand {

    /**
     * 비밀번호 교체 — 변경(AU-04)과 최초 설정(AU-05 INITIAL_SETUP)이 같은 경로다.
     * 저장 동작이 같고, 현재 비밀번호 검증·재설정 토큰 검증은 호출자가 이미 끝낸 뒤다.
     *
     * <p><b>평문이 아니라 해시를 받는다</b> — {@link MemberQuery#findCredentialByEmail}과 같은
     * 원칙이다. BCrypt 인코딩은 auth가 하고 여기서는 저장만 한다.
     *
     * <p>{@code changedAt}을 인자로 받는 이유는 호출자가 <b>같은 시각으로 세션도 폐기</b>하기
     * 때문이다 (전이표 §9 — 비밀번호 변경·재설정 시 refresh_token 전 행 폐기). 여기서 now()를
     * 새로 부르면 두 시각이 어긋나 "언제부터 무효인가"를 나중에 판정할 수 없다.
     *
     * <p>없는 memberId면 RESOURCE_NOT_FOUND — 호출자가 인증·토큰으로 존재를 보장받은
     * 자리라 없다는 것은 데이터 이상이다.
     */
    void changePassword(UUID memberId, String newPasswordHash, Instant changedAt);
}
