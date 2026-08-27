package com.twojo.member.repository;

import com.twojo.boundary.Role;
import com.twojo.member.entity.Member;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** member 테이블 접근 — 모듈 내부. 다른 모듈은 MemberQuery로만 조회한다 (11 §7.3). */
public interface MemberRepository extends JpaRepository<Member, UUID> {

    /**
     * 이메일 조회 — 유니크 인덱스가 uk_member_email_lower(lower(email))이므로
     * 컬럼도 lower()로 감싸야 인덱스를 탄다. 인자는 정규화된 소문자가 들어온다.
     */
    @Query("select m from Member m where lower(m.email) = :email")
    Optional<Member> findByEmailLower(@Param("email") String email);

    List<Member> findByCompanyIdAndStatus(UUID companyId, Member.Status status);

    List<Member> findByCompanyIdAndRoleAndStatus(UUID companyId, Role role, Member.Status status);
}
