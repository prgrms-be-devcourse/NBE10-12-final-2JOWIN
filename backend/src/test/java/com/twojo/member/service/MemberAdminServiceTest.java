package com.twojo.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.twojo.auth.SessionRevoker;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.DealCommand;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.error.ErrorResponse;
import com.twojo.member.dto.ChangeRoleRequest;
import com.twojo.member.dto.DeactivateMemberRequest;
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
import org.springframework.test.util.ReflectionTestUtils;

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
    private static final UUID 최민아 = UUID.randomUUID();

    private static final AccessContext 김서연_관리자 =
            new AccessContext(한빛오피스, 김서연, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
    private static final AccessContext 박지훈_영업 =
            new AccessContext(한빛오피스, 박지훈, Role.SALES_REP, AccessScope.OWNED_ONLY);

    @Mock private MemberRepository memberRepository;
    @Mock private DealQuery dealQuery;
    @Mock private DealCommand dealCommand;
    @Mock private SessionRevoker sessionRevoker;

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

        /** 07 §A 역할 칸 "기업 관리자" — 관문 순서가 뒤집히면 404/403 차이로 사내 id를 캐낸다. */
        @Test
        void 구성원을_비활성화할_수_없다() {
            assertThatThrownBy(() -> memberAdminService.deactivate(
                    박지훈_영업, 김서연, new DeactivateMemberRequest(null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);

            then(memberRepository).shouldHaveNoInteractions();
            then(dealQuery).shouldHaveNoInteractions();
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

    @Nested
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 비활성화는 {

        /** 07 §A · MB-14 — 담당자 없는 진행 딜이 남으면 아무도 그 딜을 이어받지 못한다. */
        @Test
        void 진행_중_담당_Deal이_있는데_이관_대상이_없으면_비활성화할_수_없다() {
            // given — 박지훈이 진행 중인 딜 두 건을 맡고 있다
            Member 박지훈_구성원 = 대상_영업();
            given(dealQuery.countOpenAssigned(한빛오피스, 박지훈)).willReturn(2L);

            // when · then — 이관 대상 없이 비활성화하면 422로 막힌다
            비활성화가_막힌다(이관_없이(), ErrorCode.MEMBER_INACTIVE_TRANSFER_REQUIRED);
            assertThat(박지훈_구성원.isActive()).isTrue();
        }

        /** 07 §A "0건이면 생략" · Q-48 — 종결 딜은 세지 않는다. 이게 Q-48의 출발점이었다. */
        @Test
        void 진행_중_담당_Deal이_없으면_이관_대상_없이_비활성화된다() {
            // given — 맡은 진행 딜이 없다
            Member 박지훈_구성원 = 대상_영업();
            given(dealQuery.countOpenAssigned(한빛오피스, 박지훈)).willReturn(0L);

            // when — 이관 대상 없이 비활성화하면
            memberAdminService.deactivate(김서연_관리자, 박지훈, 이관_없이());

            // then — 그대로 비활성이 된다
            assertThat(박지훈_구성원.isActive()).isFalse();
        }

        /** Q-48 — 넘길 것이 없으면 이관 통로를 아예 밟지 않는다. */
        @Test
        void 종결_Deal만_담당하면_이관_통로에_닿지_않는다() {
            // given — 성사·실패 딜만 남아 진행 중은 0건으로 세어진다
            대상_영업();
            given(dealQuery.countOpenAssigned(한빛오피스, 박지훈)).willReturn(0L);

            memberAdminService.deactivate(김서연_관리자, 박지훈, 이관_없이());

            // then — 옮길 것이 없으니 쓰기가 트랜잭션에 들어가지 않는다
            then(dealCommand).shouldHaveNoInteractions();
        }

        /** MB-12 · Q-48 — 진행 중 딜만 새 담당자에게 넘어간다. */
        @Test
        void 이관_대상이_지정되면_진행_중_담당_Deal이_넘어간다() {
            // given — 박지훈이 진행 딜 세 건을 맡고 있고, 최민아가 받을 사람이다
            대상_영업();
            given(dealQuery.countOpenAssigned(한빛오피스, 박지훈)).willReturn(3L);
            given(memberRepository.findByIdAndCompanyId(최민아, 한빛오피스))
                    .willReturn(Optional.of(활성_영업(최민아, "mina@hanbit.co.kr", "최민아")));

            memberAdminService.deactivate(김서연_관리자, 박지훈, 이관(최민아));

            // then — 회사·넘기는 사람·받는 사람 셋이 그대로 계약에 실린다
            then(dealCommand).should().reassignOpenDeals(한빛오피스, 박지훈, 최민아);
        }

        /** 05 §9 · MB-10 — 안 끊으면 access 수명 15분 동안 비활성 구성원이 계속 쓴다. */
        @Test
        void 비활성화하면_그_구성원의_세션이_폐기된다() {
            대상_영업();
            given(dealQuery.countOpenAssigned(한빛오피스, 박지훈)).willReturn(0L);

            memberAdminService.deactivate(김서연_관리자, 박지훈, 이관_없이());

            then(sessionRevoker).should().revokeOnDeactivation(eq(박지훈), any(Instant.class));
        }

        /** MB-11 — 역할 변경에만 있고 여기 없으면 관리자를 0명으로 만드는 뒷문이 된다. */
        @Test
        void 마지막_활성_관리자는_비활성화할_수_없다() {
            // given — 활성 관리자가 김서연 한 명뿐이다
            given(memberRepository.findByIdAndCompanyId(김서연, 한빛오피스))
                    .willReturn(Optional.of(활성_관리자(김서연)));
            given(memberRepository.countByCompanyIdAndRoleAndStatus(
                    한빛오피스, Role.COMPANY_ADMIN, Member.Status.ACTIVE)).willReturn(1L);

            // when · then — 본인을 비활성화하려 해도 막힌다
            assertThatThrownBy(() -> memberAdminService.deactivate(
                    김서연_관리자, 김서연, 이관_없이()))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.LAST_ADMIN_PROTECTED);
        }

        /** 07 §A — 받는 사람이 비활성이면 옮기나 마나다. */
        @Test
        void 이관_대상이_비활성_구성원이면_비활성화할_수_없다() {
            대상_영업();
            given(dealQuery.countOpenAssigned(한빛오피스, 박지훈)).willReturn(1L);
            given(memberRepository.findByIdAndCompanyId(최민아, 한빛오피스)).willReturn(
                    Optional.of(비활성(활성_영업(최민아, "mina@hanbit.co.kr", "최민아"))));

            비활성화가_막힌다(이관(최민아), ErrorCode.RESOURCE_NOT_FOUND);
        }

        /** SC-01·09 — 넘기면 딜이 테넌트를 넘는다. 있고 없고를 구별해 말하지 않는다. */
        @Test
        void 이관_대상이_다른_회사_구성원이면_비활성화할_수_없다() {
            대상_영업();
            UUID 남의회사_구성원 = UUID.randomUUID();
            given(dealQuery.countOpenAssigned(한빛오피스, 박지훈)).willReturn(1L);
            given(memberRepository.findByIdAndCompanyId(남의회사_구성원, 한빛오피스))
                    .willReturn(Optional.empty());

            비활성화가_막힌다(이관(남의회사_구성원), ErrorCode.RESOURCE_NOT_FOUND);
        }

        /** 이 시점에 본인은 아직 활성이라 다른 검사를 전부 통과한다 — 따로 막지 않으면 뚫린다. */
        @Test
        void 자기_자신을_이관_대상으로_지정할_수_없다() {
            대상_영업();
            given(dealQuery.countOpenAssigned(한빛오피스, 박지훈)).willReturn(1L);

            비활성화가_막힌다(이관(박지훈), ErrorCode.RESOURCE_NOT_FOUND);
            then(dealCommand).shouldHaveNoInteractions();
        }

        /** 11 §2 "한 트랜잭션" — 422가 났는데 상태가 바뀌어 있으면 화면과 DB가 갈린다. */
        @Test
        void 이관에_실패하면_상태도_세션도_그대로다() {
            Member 박지훈_구성원 = 대상_영업();
            given(dealQuery.countOpenAssigned(한빛오피스, 박지훈)).willReturn(2L);

            비활성화가_막힌다(이관_없이(), ErrorCode.MEMBER_INACTIVE_TRANSFER_REQUIRED);

            assertThat(박지훈_구성원.isActive()).isTrue();
            then(sessionRevoker).shouldHaveNoInteractions();
        }

        private Member 대상_영업() {
            Member member = 활성_영업(박지훈, "jihun@hanbit.co.kr", "박지훈");
            given(memberRepository.findByIdAndCompanyId(박지훈, 한빛오피스))
                    .willReturn(Optional.of(member));
            return member;
        }

        private void 비활성화가_막힌다(DeactivateMemberRequest 요청, ErrorCode 기대) {
            assertThatThrownBy(() -> memberAdminService.deactivate(김서연_관리자, 박지훈, 요청))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(기대);
        }
    }

    @Nested
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class 재활성화는 {

        /** 05 §구성원 — 전이표의 마지막 한 행이다. */
        @Test
        void 비활성_구성원을_다시_활성으로_되돌릴_수_있다() {
            // given — 지난주에 비활성화된 박지훈이 복귀한다
            Member 박지훈_구성원 = 비활성(활성_영업(박지훈, "jihun@hanbit.co.kr", "박지훈"));
            given(memberRepository.findByIdAndCompanyId(박지훈, 한빛오피스))
                    .willReturn(Optional.of(박지훈_구성원));

            memberAdminService.reactivate(김서연_관리자, 박지훈);

            assertThat(박지훈_구성원.isActive()).isTrue();
        }

        /** SC-01·09 — 남의 회사 계정을 되살릴 수 있으면 안 된다. */
        @Test
        void 다른_회사_구성원은_재활성화할_수_없다() {
            UUID 남의회사_구성원 = UUID.randomUUID();
            given(memberRepository.findByIdAndCompanyId(남의회사_구성원, 한빛오피스))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> memberAdminService.reactivate(김서연_관리자, 남의회사_구성원))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

            // and — 회사 조건이 where에 실려 나갔다. 회사 없이 조회해도 없는 id는 똑같이 404가 나서,
            // 이 단정이 없으면 스코프가 빠진 것을 잡지 못한다
            then(memberRepository).should().findByIdAndCompanyId(남의회사_구성원, 한빛오피스);
        }

        /** Q-48 — 돌아오면 이관받은 사람 것을 뺏는다. Deal 통로에 닿지 않는 것이 규칙이다. */
        @Test
        void 재활성화해도_담당하던_Deal은_돌아오지_않는다() {
            given(memberRepository.findByIdAndCompanyId(박지훈, 한빛오피스)).willReturn(
                    Optional.of(비활성(활성_영업(박지훈, "jihun@hanbit.co.kr", "박지훈"))));

            memberAdminService.reactivate(김서연_관리자, 박지훈);

            then(dealCommand).shouldHaveNoInteractions();
            then(dealQuery).shouldHaveNoInteractions();
        }
    }

    /** 비활성화 통로가 생겨(MB-09) 강제로 필드를 건드릴 이유가 없어졌다. */
    private static Member 비활성(Member member) {
        member.deactivate();
        return member;
    }

    private static Member 활성_영업(UUID id, String email, String name) {
        Member member = Member.invited(한빛오피스, email, "$2a$10$K7Lm", name, Role.SALES_REP, NOW);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static Member 활성_관리자(UUID id) {
        Member member = Member.invited(
                한빛오피스, "seoyeon@hanbit.co.kr", "$2a$10$K7Lm", "김서연", Role.COMPANY_ADMIN, NOW);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static DeactivateMemberRequest 이관_없이() {
        return new DeactivateMemberRequest(null);
    }

    private static DeactivateMemberRequest 이관(UUID 대상) {
        return new DeactivateMemberRequest(대상);
    }
}
