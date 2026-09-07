package com.twojo.member.repository;

import com.twojo.member.entity.Invitation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** invitation 테이블 접근 — 모듈 내부 전용. */
public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    /** 대기 초대는 회사·이메일당 하나다 — uk_invitation_pending 이 그것을 강제한다. */
    Optional<Invitation> findByCompanyIdAndEmailAndStatus(
            UUID companyId, String email, Invitation.Status status);

    /** 회사 조건을 where에 넣는다 — 못 찾으면 없는 것과 남의 것을 구별하지 않고 404다. */
    Optional<Invitation> findByIdAndCompanyId(UUID id, UUID companyId);

    Page<Invitation> findByCompanyId(UUID companyId, Pageable pageable);

    Page<Invitation> findByCompanyIdAndStatus(
            UUID companyId, Invitation.Status status, Pageable pageable);

    /** 공개 링크 진입점 — URL의 원문을 해시해 이 값으로 찾는다. */
    Optional<Invitation> findByTokenHash(String tokenHash);
}
