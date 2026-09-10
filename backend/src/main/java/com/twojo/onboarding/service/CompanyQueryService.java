package com.twojo.onboarding.service;

import com.twojo.boundary.CompanyQuery;
import com.twojo.global.error.MissingReferenceException;
import com.twojo.onboarding.entity.Company;
import com.twojo.onboarding.repository.CompanyRepository;
import java.util.List;
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
                .orElseThrow(() -> new MissingReferenceException("company", companyId));
    }

    @Override
    public List<UUID> findActiveIds() {
        return companyRepository.findIdsByStatus(Company.Status.ACTIVE);
    }
}
