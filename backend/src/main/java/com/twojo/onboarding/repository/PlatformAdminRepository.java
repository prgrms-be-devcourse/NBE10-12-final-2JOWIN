package com.twojo.onboarding.repository;

import com.twojo.onboarding.entity.PlatformAdmin;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** platform_admin 테이블 접근 — 모듈 내부. 다른 모듈은 PlatformAdminQuery로만 조회한다. */
public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, UUID> {

    /**
     * 이메일 조회 — 대소문자를 무시한다.
     *
     * <p>구성원과 달리 이 테이블의 유니크 인덱스는 email 컬럼 그대로라 인덱스를 타지 못한다.
     * 관리자 계정은 한 자릿수여서 전체를 훑어도 무해하고, 대소문자를 가리면
     * 대문자가 섞인 계정이 로그인되지 않는다.
     */
    Optional<PlatformAdmin> findByEmailIgnoreCase(String email);
}
