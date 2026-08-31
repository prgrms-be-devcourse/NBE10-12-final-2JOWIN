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
    public CompanySummary get(UUID companyId) {
        return companyRepository.findById(companyId)
                .map(c -> new CompanySummary(
                        c.getId(), c.getName(), c.getBusinessNo(),
                        c.getStatus() == Company.Status.ACTIVE))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
