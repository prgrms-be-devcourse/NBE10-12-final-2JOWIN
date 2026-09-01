package com.twojo.member.service;

import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.member.entity.Member;
import com.twojo.member.repository.MemberRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MemberQuery 구현 — member 모듈이 밖에 내보이는 유일한 조회 경로 (11 §2 · §7.3).
 *
 * <p>엔티티를 그대로 반환하지 않고 boundary의 record로 변환한다.
 * 엔티티가 새면 다른 모듈이 member 내부 구조에 묶이고 Modulith 검증도 깨진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberQueryService implements MemberQuery {

    private final MemberRepository memberRepository;

    /**
     * 없으면 예외를 던진다 — 호출부가 복합 FK로 존재를 보장받는 자리라 없다는 것은 데이터 이상이다.
     * null을 흘리면 이름을 찍는 쪽에서 NPE로 터진다.
     */
    @Override
    public MemberSummary get(UUID memberId) {
        return memberRepository.findById(memberId)
                .map(this::toSummary)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 담당자 선택지 — 활성 구성원만 (DL-04). */
    @Override
    public List<MemberSummary> findAllActive(UUID companyId) {
        return memberRepository.findByCompanyIdAndStatus(companyId, Member.Status.ACTIVE)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 없는 id는 false — 타사 id를 넘겼을 때 "비활성"과 "존재하지 않음"이
     * 구별되지 않아야 한다 (SC-09).
     */
    @Override
    public boolean isActive(UUID memberId) {
        return memberRepository.findById(memberId)
                .map(Member::isActive)
                .orElse(false);
    }

    /** Q-26 폴백 수신자 — 활성 기업 관리자. MB-11이 최소 1명 존재를 보장한다. */
    @Override
    public List<UUID> findAdminIds(UUID companyId) {
        return memberRepository
                .findByCompanyIdAndRoleAndStatus(companyId, Role.COMPANY_ADMIN, Member.Status.ACTIVE)
                .stream()
                .map(Member::getId)
                .toList();
    }

    /**
     * 정규화를 여기서 한다 — 호출자에게 맡기면 한 곳만 빠뜨려도
     * 조회가 조용히 실패하고 원인을 찾기 어렵다.
     */
    @Override
    public Optional<AuthCredential> findCredentialByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return memberRepository.findByEmailLower(email.trim().toLowerCase(Locale.ROOT))
                .map(this::toCredential);
    }

    /** 회전 시 access claim 을 채우는 경로 — 없으면 데이터 이상이다 (FK 보장). */
    @Override
    public AuthCredential getCredential(UUID memberId) {
        return memberRepository.findById(memberId)
                .map(this::toCredential)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 열람 페이지 담당자 표시 — 없으면 데이터 이상이다 (deal.assignee_member_id FK 보장). */
    @Override
    public MemberContact getContact(UUID memberId) {
        return memberRepository.findById(memberId)
                .map(this::toContact)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private AuthCredential toCredential(Member member) {
        return new AuthCredential(
                member.getId(), member.getCompanyId(), member.getName(), member.getRole(),
                member.isActive(), member.getPasswordHash());
    }

    private MemberContact toContact(Member member) {
        return new MemberContact(member.getName(), member.getEmail(), member.getPhone());
    }

    private MemberSummary toSummary(Member member) {
        return new MemberSummary(member.getId(), member.getName(), member.isActive());
    }
}
