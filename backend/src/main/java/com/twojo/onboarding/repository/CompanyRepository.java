package com.twojo.onboarding.repository;

import com.twojo.onboarding.entity.Company;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** company 테이블 접근 — 모듈 내부. 다른 모듈은 CompanyQuery로만 조회한다 (11 §7.3). */
public interface CompanyRepository extends JpaRepository<Company, UUID> {}
