package com.twojo.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.twojo.boundary.CompanyQuery.CompanySummary;
import com.twojo.onboarding.entity.Company;
import com.twojo.onboarding.repository.CompanyRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 경계 밖으로 나가는 회사 정보의 <b>매핑 누락</b>을 잡는다.
 *
 * <p>필드를 하나 빠뜨려도 컴파일은 통과하고 화면에는 빈칸만 뜬다 — 고객 열람 페이지에서
 * 사업자등록번호가 사라지는 식이다(10 §5.6). 그런 종류는 테스트로만 막힌다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CompanyQueryServiceTest {

    private static final UUID COMPANY_ID = UUID.fromString("c0000000-0000-4000-8000-000000000001");

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private Company company;

    @InjectMocks
    private CompanyQueryService companyQueryService;

    @Test
    @DisplayName("네 필드를 모두 옮긴다 — businessNo를 빠뜨리지 않는다")
    void 회사_정보를_빠짐없이_매핑한다() {
        given(company.getId()).willReturn(COMPANY_ID);
        given(company.getName()).willReturn("한빛오피스");
        given(company.getBusinessNo()).willReturn("123-45-67890");
        given(company.getStatus()).willReturn(Company.Status.ACTIVE);
        given(companyRepository.findById(COMPANY_ID)).willReturn(Optional.of(company));

        CompanySummary summary = companyQueryService.get(COMPANY_ID);

        assertThat(summary).isEqualTo(
                new CompanySummary(COMPANY_ID, "한빛오피스", "123-45-67890", true));
    }

    @Test
    @DisplayName("SUSPENDED면 active가 false — D의 정지 판정 근거 (SC-10 · Q-27)")
    void 정지된_회사는_active가_false다() {
        given(company.getStatus()).willReturn(Company.Status.SUSPENDED);
        given(companyRepository.findById(COMPANY_ID)).willReturn(Optional.of(company));

        assertThat(companyQueryService.get(COMPANY_ID).active()).isFalse();
    }
}
