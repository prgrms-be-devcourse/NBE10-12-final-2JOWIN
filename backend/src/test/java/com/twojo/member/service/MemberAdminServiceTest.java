package com.twojo.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.error.ErrorResponse;
import com.twojo.member.dto.ChangeRoleRequest;
import com.twojo.member.entity.Member;
import com.twojo.member.repository.MemberRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

/**
 * 구성원 관리 — 역할 판정(Q-43) · 회사 스코프(SC-01·09) · 마지막 관리자 보호(MB-11).
 *
 * <p>역할 위반은 403, 리소스 범위 위반은 404다. 두 관문의 <b>순서</b>도 규칙이라 따로 본다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MemberAdminServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-04T09:00:00Z");
    private static final UUID 한빛오피스 = UUID.randomUUID();
    private static final UUID 김서연 = UUID.randomUUID();
    private static final UUID 박지훈 = UUID.randomUUID();

    private static final AccessContext 김서연_관리자 =
            new AccessContext(한빛오피스, 김서연, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
    private static final AccessContext 박지훈_영업 =
            new AccessContext(한빛오피스, 박지훈, Role.SALES_REP, AccessScope.OWNED_ONLY);

    @Mock private MemberRepository memberRepository;

    @InjectMocks private MemberAdminService memberAdminService;

    @Nested
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 영업_담당자는 {

        /** 07 §A 역할 칸 "기업 관리자" · Q-43 — 뚫리면 회사 전원의 이메일·연락처가 나간다. */
        @Test
        void 구성원_목록을_조회할_수_없다() {
            // given — 박지훈은 활성 구성원이지만 영업 담당자다
            // when  — 구성원 목록을 요청하면
            // then  — 역할로 갈리는 행위라 403이다
            assertThatThrownBy(() -> memberAdminService.list(박지훈_영업, PageRequest.of(0, 20)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        /** 09 §84 — 뚫리면 영업 담당자가 스스로 관리자가 될 수 있다. */
        @Test
        void 역할을_변경할_수_없다() {
            // given — 박지훈이 자기 자신을 관리자로 올리려 한다
            // when  — 역할 변경을 요청하면
            // then  — 403이다
            assertThatThrownBy(() -> memberAdminService.changeRole(
                    박지훈_영업, 박지훈, new ChangeRoleRequest("COMPANY_ADMIN")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);
        }

        /**
         * SC-09 — 스코프를 먼저 보면 403(우리 회사에 있음)과 404(없음)의 차이가
         * 사내 구성원 id의 존재를 알려준다. 역할을 먼저 보면 무엇을 넣든 403이다.
         */
        @Test
        void 어떤_id를_넣어도_조회에_닿지_못한다() {
            // given — 박지훈이 아무 UUID나 경로에 넣는다
            UUID 아무거나 = UUID.randomUUID();

            // when — 역할 변경을 요청하면 403으로 막히고
            assertThatThrownBy(() -> memberAdminService.changeRole(
                    박지훈_영업, 아무거나, new ChangeRoleRequest("SALES_REP")))
                    .isInstanceOf(BusinessException.class);

            // then — 그 id로 조회가 일어나지 않는다. 존재 여부를 판정할 기회 자체가 없다
            then(memberRepository).should(never()).findByIdAndCompanyId(any(), any());
        }
    }

    @Nested
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 기업_관리자는 {

        /** SC-01·09 — 회사 조건이 where에 있어 남의 회사 구성원은 조회에 나오지 않는다. */
        @Test
        void 다른_회사_구성원의_역할을_변경할_수_없다() {
            // given — 김서연이 다른 회사 구성원의 id를 경로에 넣는다
            UUID 남의회사_구성원 = UUID.randomUUID();
            given(memberRepository.findByIdAndCompanyId(남의회사_구성원, 한빛오피스))
                    .willReturn(Optional.empty());

            // when — 역할 변경을 요청하면
            // then  — 권한 없음(403)이 아니라 없음(404)이다. 둘을 구별해 말하지 않는다
            assertThatThrownBy(() -> memberAdminService.changeRole(
                    김서연_관리자, 남의회사_구성원, new ChangeRoleRequest("COMPANY_ADMIN")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

            // and — 회사 조건이 where에 실려 나갔다. 조회한 뒤 비교하는 방식이면 이 단정이 깨진다
            then(memberRepository).should().findByIdAndCompanyId(남의회사_구성원, 한빛오피스);
        }

        /**
         * 05 §4 해석 — 전이표는 "마지막 기업 관리자 역할 강등"만 적고 비활성 관리자를 말하지 않는다.
         * 비활성 관리자는 활성 관리자 수에 들어가지 않아 강등해도 회사가 관리자를 잃지 않는다.
         */
        @Test
        void 비활성_관리자는_마지막_관리자_판정에서_제외된다() {
            // given — 퇴사 처리된 관리자가 하나 있고, 활성 관리자는 김서연 한 명뿐이다
            Member 비활성_관리자 = 비활성(Member.invited(
                    한빛오피스, "old@hanbit.co.kr", "$2a$10$K7Lm", "이전관리자", Role.COMPANY_ADMIN, NOW));
            UUID 대상 = UUID.randomUUID();
            given(memberRepository.findByIdAndCompanyId(대상, 한빛오피스))
                    .willReturn(Optional.of(비활성_관리자));
            lenient().when(memberRepository.countByCompanyIdAndRoleAndStatus(
                    한빛오피스, Role.COMPANY_ADMIN, Member.Status.ACTIVE)).thenReturn(1L);

            // when — 김서연이 그 비활성 관리자를 영업 담당자로 내리면
            memberAdminService.changeRole(김서연_관리자, 대상, new ChangeRoleRequest("SALES_REP"));

            // then — 막히지 않고 역할이 바뀐다. 활성 관리자 수를 셀 일도 없다
            assertThat(비활성_관리자.getRole()).isEqualTo(Role.SALES_REP);
            then(memberRepository).should(never())
                    .countByCompanyIdAndRoleAndStatus(any(), any(), any());
        }

        /**
         * 요청 필드가 String이라 오타가 @NotBlank를 통과해 서비스까지 온다 (08 §A).
         * 07 부록이 VALIDATION_FAILED에 "fieldErrors 참조"라고 적으므로 빈 배열로 나가면 안 된다.
         */
        @Test
        void 없는_역할_값은_어느_필드가_틀렸는지_알려준다() {
            given(memberRepository.findByIdAndCompanyId(박지훈, 한빛오피스))
                    .willReturn(Optional.of(mock(Member.class)));

            assertThatThrownBy(() -> memberAdminService.changeRole(
                    김서연_관리자, 박지훈, new ChangeRoleRequest("BAD_ROLE")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getFieldErrors())
                    .asInstanceOf(InstanceOfAssertFactories.list(ErrorResponse.FieldError.class))
                    .singleElement()
                    .satisfies(fe -> {
                        assertThat(fe.field()).isEqualTo("role");
                        // 허용 목록은 enum에서 만든다 — 역할이 늘면 문구도 따라 는다
                        assertThat(fe.reason())
                                .contains("COMPANY_ADMIN")
                                .contains("SALES_REP");
                    });
        }

        /** 보낸 값을 응답에 되돌려주지 않는다 — 반사형 XSS 표면을 만들지 않는다. */
        @Test
        void 사유_문구에_보낸_값을_싣지_않는다() {
            given(memberRepository.findByIdAndCompanyId(박지훈, 한빛오피스))
                    .willReturn(Optional.of(mock(Member.class)));

            assertThatThrownBy(() -> memberAdminService.changeRole(
                    김서연_관리자, 박지훈, new ChangeRoleRequest("<script>")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getFieldErrors().getFirst().reason())
                    .asString()
                    .doesNotContain("script");
        }
    }

    /** 비활성 구성원을 만들 통로가 아직 없다 — 비활성화(MB-09)는 DealCommand 대기로 이번 범위 밖이다. */
    private static Member 비활성(Member member) {
        try {
            var status = Member.class.getDeclaredField("status");
            status.setAccessible(true);
            status.set(member, Member.Status.INACTIVE);
            return member;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
