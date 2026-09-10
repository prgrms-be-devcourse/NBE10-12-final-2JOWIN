package com.twojo.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.twojo.boundary.CompanyQuery.CompanySummary;
import com.twojo.global.error.MissingReferenceException;
import com.twojo.onboarding.entity.Company;
import com.twojo.onboarding.repository.CompanyRepository;
import java.util.List;
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

    /**
     * companyId는 {@code member.company_id} FK를 타고 온다 — 없다는 것은 조회 실패가 아니라
     * 데이터 이상이다. 404로 답하면 이 경계를 부르는 쪽(로그인·필터·내 정보·초대 메일)이
     * 전부 "없거나 범위 밖"이라는 다른 뜻의 응답을 받는다 (#165).
     */
    @Test
    @DisplayName("없는 회사는 무결성 이상으로 터진다 — 404가 아니다 (#165)")
    void 없는_회사는_무결성_이상이다() {
        given(companyRepository.findById(COMPANY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> companyQueryService.get(COMPANY_ID))
                .isInstanceOf(MissingReferenceException.class)
                .hasMessageContaining("company")
                .hasMessageContaining(COMPANY_ID.toString());
    }

    /**
     * 거르는 상태를 잘못 넣어도 컴파일은 통과한다 — {@code SUSPENDED}를 넘기면 배치가 정확히
     * 정지된 회사에만 알림을 보낸다. 넘어간 인자를 직접 봐야 그 뒤집힘이 잡힌다 (Q-27).
     */
    @Test
    @DisplayName("ACTIVE로 걸러 조회하고 결과를 그대로 돌려준다 — 배치의 회사 순회 (Q-27)")
    void 활성_회사만_조회한다() {
        UUID 다른회사 = UUID.fromString("c0000000-0000-4000-8000-000000000002");
        given(companyRepository.findIdsByStatus(Company.Status.ACTIVE))
                .willReturn(List.of(COMPANY_ID, 다른회사));

        assertThat(companyQueryService.findActiveIds()).containsExactly(COMPANY_ID, 다른회사);
    }

    /**
     * {@code get}과 달리 없는 것이 이상 상황이 아니다 — 회사가 하나도 없는 순간이 실제로 있고
     * (첫 가입 승인 전), 정지가 전부여도 정상이다. 여기서 던지면 배치가 그날 통째로 죽는다.
     */
    @Test
    @DisplayName("활성 회사가 없으면 빈 목록 — 예외도 null도 아니다")
    void 활성_회사가_없으면_빈_목록이다() {
        given(companyRepository.findIdsByStatus(Company.Status.ACTIVE)).willReturn(List.of());

        assertThat(companyQueryService.findActiveIds()).isEmpty();
    }
}
