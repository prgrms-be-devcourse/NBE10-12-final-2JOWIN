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

    /**
     * memberId 로 자격 조회 — refresh 회전 시 access claim(companyId·role)을 채운다.
     * refresh_token 행에는 member_id 밖에 없어 MemberSummary 로는 부족하다.
     */
    AuthCredential getCredential(UUID memberId);

    /**
     * 담당자 연락처 — D의 고객 열람 페이지 전용 (AP-18 · 08 §D AssigneeInfo).
     *
     * <p>MemberSummary를 넓히지 않고 따로 둔다. 그쪽은 findAllActive가 리스트로 실어 나르고
     * 07 §A가 /members/options를 "이름·id만"으로 규정한다 — 연락처가 딸려갈 경로가 아니다.
     * AuthCredential을 auth 전용으로 둔 것과 같은 패턴이다.
     *
     * <p>없으면 RESOURCE_NOT_FOUND. 비활성 구성원도 그대로 반환한다 — 던지면 고객 열람
     * 페이지 전체가 죽는다. 담당자는 MB-14의 이관 강제로 정상적으론 활성이다.
     */
    MemberContact getContact(UUID memberId);

    record MemberSummary(UUID id, String name, boolean active) {}

    /** 열람 페이지에 표시할 담당자 연락처 (AP-18). 세 필드 전부 표시용이며 판정에 쓰지 않는다. */
    record MemberContact(String name, String email, String phone) {}

    /**
     * 인증에 필요한 최소 정보.
     * passwordHash는 NULL일 수 있다 — 가입 승인 직후 비밀번호 미설정 계정 (Q-33).
     * name은 로그인 응답(08 §A)에 필요해 함께 싣는다 — 같은 행이라 추가 조회가 없다.
     */
    record AuthCredential(UUID id, UUID companyId, String name, Role role,
                          boolean active, String passwordHash) {}
}
