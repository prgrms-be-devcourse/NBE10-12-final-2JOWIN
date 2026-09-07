package com.twojo.onboarding.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.auth.InitialPasswordSetup;
import com.twojo.boundary.MailCommand;
import com.twojo.boundary.MemberCommand;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.onboarding.entity.Application;
import com.twojo.onboarding.repository.ApplicationRepository;
import com.twojo.onboarding.repository.CompanyRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 사업자번호 중복 승인의 뒷정리 (07 §A COMPANY_BUSINESS_NO_DUPLICATED · ERD 전역 UNIQUE).
 *
 * <p>07은 이 경우를 "반려 유도"로 규정한다 — 관리자가 다시 반려할 수 있어야 한다는 뜻이다.
 * 검사를 상태 전이 뒤에 두면 신청이 APPROVED로 넘어갔다가 롤백되는데, 롤백은 트랜잭션
 * 경계에서만 일어난다. 검사 순서가 코드에만 있어 여기서 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ApplicationAdminServiceTest {

    private static final UUID 신청_ID = UUID.randomUUID();

    @Mock private ApplicationRepository applicationRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private MemberCommand memberCommand;
    @Mock private InitialPasswordSetup initialPasswordSetup;
    @Mock private MailCommand mailCommand;

    @InjectMocks private ApplicationAdminService applicationAdminService;

    @Test
    void 사업자번호가_중복이면_신청은_검토_대기로_남는다() {
        // given — 한빛오피스는 이미 가입돼 있고, 같은 사업자번호로 새 신청이 들어와 있다
        Application 신청 = Application.submit(
                "한빛오피스", "123-45-67890", "seoyeon@hanbit.co.kr", "김서연");
        given(applicationRepository.findById(신청_ID)).willReturn(Optional.of(신청));
        given(companyRepository.existsByBusinessNo("123-45-67890")).willReturn(true);

        // when — 플랫폼 관리자가 승인을 누르면
        // then — 409로 막히고
        assertThatThrownBy(() -> applicationAdminService.approve(신청_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COMPANY_BUSINESS_NO_DUPLICATED);

        // then — 신청은 그대로 검토 대기다. 관리자가 이제 반려할 수 있다
        org.assertj.core.api.Assertions.assertThat(신청.isPending()).isTrue();
        org.assertj.core.api.Assertions.assertThat(신청.getDecidedAt()).isNull();

        // then — 회사도 계정도 만들어지지 않았고 메일도 나가지 않았다
        then(companyRepository).should(never()).save(any());
        then(memberCommand).should(never()).createCompanyAdmin(any(), anyString(), anyString());
        then(initialPasswordSetup).shouldHaveNoInteractions();
        then(mailCommand).shouldHaveNoInteractions();
    }
}
