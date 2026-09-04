package com.twojo.boundary;

import java.util.Optional;
import java.util.UUID;

/**
 * 플랫폼 관리자 조회 계약 (AU-08) — 구현은 onboarding, 호출은 auth.
 *
 * <p>같은 담당자의 두 모듈을 잇는 계약인데도 도메인이 아니라 여기에 두는 이유는
 * 회사 정지가 반대 방향을 요구하기 때문이다 — onboarding이 auth의 세션 폐기를 부른다.
 * 이 인터페이스를 onboarding에 두면 두 모듈이 서로를 참조해 순환이 되고,
 * 모듈 경계 검증이 빌드에서 막는다.
 */
public interface PlatformAdminQuery {

    /**
     * 로그인 자격 조회 — 비밀번호 비교는 호출자가 한다.
     *
     * <p>대소문자와 앞뒤 공백은 무시한다(구현이 정규화). 계정이 없으면 빈 값이다 —
     * 예외로 알리면 미가입과 자격 불일치의 응답이 갈린다.
     */
    Optional<AdminCredential> findCredentialByEmail(String email);

    /**
     * 재발급 시 계정이 아직 살아 있는지 확인한다.
     * 없는 id도 false다 — "없음"과 "비활성"이 호출자에게 구별되지 않아야 한다.
     */
    boolean isActive(UUID platformAdminId);

    /**
     * 인증에 필요한 최소 정보.
     *
     * <p>email이 들어 있는 것은 로그인 응답에 표시할 이름이 이 값이기 때문이다 —
     * 관리자에게는 이름 컬럼이 없다.
     */
    record AdminCredential(UUID id, String email, String passwordHash, boolean active) {}
}
