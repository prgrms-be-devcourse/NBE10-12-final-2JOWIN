package com.twojo.onboarding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.onboarding.dto.CreateApplicationRequest;
import com.twojo.onboarding.entity.Application;
import com.twojo.onboarding.repository.ApplicationRepository;
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
 * 사용 신청 접수의 두 관문 (ON-01·13 · 05 §1 "막히는 것" · Q-15).
 *
 * <p>인증 없이 누구나 부르는 경로라 응답이 곧 안내다. 어느 관문에 먼저 걸리느냐가
 * 신청자가 받는 안내를 바꾼다 — 그 순서가 코드에만 있어 여기서 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ApplicationServiceTest {

    private static final CreateApplicationRequest 김서연_신청 = new CreateApplicationRequest(
            "한빛오피스", "123-45-67890", "seoyeon@hanbit.co.kr", "김서연");

    @Mock private ApplicationRepository applicationRepository;
    @Mock private MemberQuery memberQuery;

    @InjectMocks private ApplicationService applicationService;

    /**
     * Q-15 — 반려 사유를 기록하고 행을 보존하기로 한 것은 재신청을 전제한 결정이다.
     *
     * <p>대기 검사가 상태를 보지 않고 이메일만 보면, 한 번 반려된 사람은 그 이메일로
     * 영원히 다시 신청할 수 없다. 이 회사는 반려 후 서류를 고쳐 다시 오는 것이 정상 경로다.
     */
    @Test
    void 반려된_신청이_있어도_같은_이메일로_다시_신청할_수_있다() {
        // given — 김서연은 구성원이 아니고, 지난 신청은 반려로 종결됐다(대기 행 없음)
        given(memberQuery.findCredentialByEmail("seoyeon@hanbit.co.kr"))
                .willReturn(Optional.empty());
        given(applicationRepository.findByEmailLowerAndStatus(
                "seoyeon@hanbit.co.kr", Application.Status.PENDING))
                .willReturn(Optional.empty());
        given(applicationRepository.saveAndFlush(any(Application.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        // when — 같은 이메일로 다시 신청하면
        var response = applicationService.submit(김서연_신청);

        // then — 접수되어 검토 대기로 들어간다
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.applicantName()).isEqualTo("김서연");
    }

    /**
     * 07 §A — 두 코드가 서로 다른 안내로 이어진다. EMAIL_ALREADY_MEMBER는
     * "이미 계정이 있으니 로그인하라"이고, APPLICATION_ALREADY_PENDING은 "기다리라"다.
     *
     * <p>계정이 있는 사람에게 기다리라고 답하면 영원히 기다린다 — 그 신청은 승인돼도
     * 계정을 만들지 못한다(이메일 전역 유일, Q-14). 구성원 검사가 반드시 먼저다.
     */
    @Test
    void 구성원이면서_대기_신청도_있으면_이미_계정이_있다고_답한다() {
        // given — 김서연은 이미 한빛오피스의 구성원이다. 지난 대기 신청도 남아 있다
        given(memberQuery.findCredentialByEmail("seoyeon@hanbit.co.kr"))
                .willReturn(Optional.of(new MemberQuery.AuthCredential(
                        UUID.randomUUID(), UUID.randomUUID(), "김서연",
                        Role.COMPANY_ADMIN, true, "$2a$10$K7LmQz9")));

        // when — 다시 신청하면
        // then — 대기 신청이 아니라 계정 존재를 알린다
        assertThatThrownBy(() -> applicationService.submit(김서연_신청))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_MEMBER);

        // 대기 신청 조회에 닿지도 않는다 — 닿으면 순서가 뒤집힌 것이다
        then(applicationRepository).should(never())
                .findByEmailLowerAndStatus(anyString(), any());
        then(applicationRepository).should(never()).saveAndFlush(any());
    }
}
