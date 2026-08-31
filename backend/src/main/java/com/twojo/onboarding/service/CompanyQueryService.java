package com.twojo.onboarding.service;

import com.twojo.boundary.CompanyQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.onboarding.entity.Company;
import com.twojo.onboarding.repository.CompanyRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** CompanyQuery 구현 — onboarding 모듈이 밖에 내보이는 조회 경로 (11 §7.3). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanyQueryService implements CompanyQuery {

    private final CompanyRepository companyRepository;

    @Override
    public CompanyIdentity getIdentity(UUID companyId) {
        return companyRepository.findById(companyId)
                .map(c -> new CompanyIdentity(c.getId(), c.getName()))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** 없는 회사를 "정지 아님"으로 답하면 인증이 열린다 — 판정 불가는 막는 쪽으로 접는다. */
    @Override
    public boolean isSuspended(UUID companyId) {
        return companyRepository.findById(companyId)
                .map(c -> c.getStatus() == Company.Status.SUSPENDED)
                .orElse(true);
    }
}
