package com.twojo.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.auth.InitialPasswordSetup;
import com.twojo.boundary.MailCommand;
import com.twojo.boundary.MemberCommand;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.onboarding.dto.RejectApplicationRequest;
import com.twojo.onboarding.entity.Application;
import com.twojo.onboarding.repository.ApplicationRepository;
import com.twojo.onboarding.repository.CompanyRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

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

    @Nested
    class 신청_반려는 {

        private static final String 사유 = "사업자등록증 사본이 불명확합니다";

        /** ON-05·06 · 07 §반려 행 — 07이 반려의 효과로 통보 메일 발송을 규정한다. */
        @Test
        void 신청을_반려하면_통보_메일이_예약된다() {
            // given — 김서연의 한빛오피스 신청이 검토 대기로 들어와 있다
            대기_신청();

            // when — 플랫폼 관리자가 사유를 적어 반려하면
            applicationAdminService.reject(신청_ID, new RejectApplicationRequest(사유));

            // then — 신청자 앞으로 반려 통보가 신청서 id를 달고 예약된다
            then(mailCommand).should().schedule(
                    eq(MailCommand.TemplateType.SIGNUP_REJECTED),
                    any(),
                    eq("seoyeon@hanbit.co.kr"),
                    eq(신청_ID),
                    eq("[2JO] 사용 신청이 반려되었습니다"),
                    any());
        }

        /** 계약 isPlatformIssued — 반려는 회사를 만들지 않는다. 값을 넣으면 없는 회사를 가리키는 기록이 된다. */
        @Test
        void 반려_통보에는_회사_id가_실리지_않는다() {
            대기_신청();

            applicationAdminService.reject(신청_ID, new RejectApplicationRequest(사유));

            then(mailCommand).should().schedule(any(), isNull(), any(), any(), any(), any());
        }

        /** ON-14가 남기게 한 사유가 ON-06의 통보로 이어지지 않으면, 무엇을 고쳐 다시 낼지 알 수 없다. */
        @Test
        void 반려_통보는_사유를_담는다() {
            대기_신청();

            applicationAdminService.reject(신청_ID, new RejectApplicationRequest(사유));

            ArgumentCaptor<String> 본문 = ArgumentCaptor.forClass(String.class);
            then(mailCommand).should().schedule(any(), any(), any(), any(), any(), 본문.capture());
            assertThat(본문.getValue()).contains(사유);
        }

        /** ON-05 — 처리된 신청은 되살아나지 않는다. 두 번째 반려가 통과하면 통보도 두 번 나간다. */
        @Test
        void 처리된_신청은_다시_반려할_수_없고_메일도_나가지_않는다() {
            // given — 어제 이미 반려된 신청이다
            Application 반려된_신청 = 신청();
            반려된_신청.reject(사유, Instant.parse("2026-09-06T02:00:00Z"));
            given(applicationRepository.findById(신청_ID)).willReturn(Optional.of(반려된_신청));

            // when · then — 409로 막히고
            assertThatThrownBy(() -> applicationAdminService.reject(
                    신청_ID, new RejectApplicationRequest("다시 반려")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.APPLICATION_ALREADY_DECIDED);

            // then — 통보도 나가지 않는다
            then(mailCommand).shouldHaveNoInteractions();
        }

        private Application 신청() {
            Application 신청 = Application.submit(
                    "한빛오피스", "123-45-67890", "seoyeon@hanbit.co.kr", "김서연");
            ReflectionTestUtils.setField(신청, "id", 신청_ID);
            return 신청;
        }

        private void 대기_신청() {
            given(applicationRepository.findById(신청_ID)).willReturn(Optional.of(신청()));
        }
    }
}
