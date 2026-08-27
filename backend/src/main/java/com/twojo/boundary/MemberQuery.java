package com.twojo.boundary;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 구성원 조회 계약 — 구현: A(member 모듈). B·C·D는 member 테이블을 직접 조회하지 않는다.
 * (docs/11-work-breakdown.md §2)
 */
public interface MemberQuery {

    /** 이름 표시용 (B·C·D). 없으면 RESOURCE_NOT_FOUND를 던진다. */
    MemberSummary get(UUID memberId);

    /** 담당자 선택지 (C) */
    List<MemberSummary> findAllActive(UUID companyId);

    /** 배정·이관 대상 검증 (C) */
    boolean isActive(UUID memberId);

    /** Q-26 폴백 수신자 (D) */
    List<UUID> findAdminIds(UUID companyId);

    /**
     * 로그인 자격 조회 — auth 모듈 전용.
     *
     * <p>Modulith가 member.entity 직접 참조를 막으므로, 인증에 필요한 값만 이 경계로 넘긴다.
     * 비밀번호 비교는 호출자(auth)가 수행한다 — 평문을 경계 밖으로 흘리지 않기 위한 선택이며,
     * BCrypt 해시는 단방향이라 그 자체로는 비밀이 아니다.
     *
     * <p>대소문자와 앞뒤 공백은 무시한다(구현이 정규화). 조회는 lower(email) 유니크 인덱스를 탄다.
     */
    Optional<AuthCredential> findCredentialByEmail(String email);

    record MemberSummary(UUID id, String name, boolean active) {}

    /**
     * 인증에 필요한 최소 정보.
     * passwordHash는 NULL일 수 있다 — 가입 승인 직후 비밀번호 미설정 계정 (Q-33).
     */
    record AuthCredential(UUID id, UUID companyId, Role role, boolean active, String passwordHash) {}
}
