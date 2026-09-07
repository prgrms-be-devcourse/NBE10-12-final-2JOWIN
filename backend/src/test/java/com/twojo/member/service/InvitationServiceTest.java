package com.twojo.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.MailCommand;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.member.dto.CreateInvitationRequest;
import com.twojo.member.entity.Invitation;
import com.twojo.member.repository.InvitationRepository;
import com.twojo.member.repository.MemberRepository;
import com.twojo.member.token.InvitationTokenGenerator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 초대 발송·취소 — 역할 판정(Q-43) · 회사 스코프(SC-01·09) · 이메일 점유(MB-13).
 *
 * <p>대기 초대 중복은 05 §3에 없는 경우다. 부분 유니크 인덱스가 막아 그냥 두면 500이 되므로
 * 서비스가 먼저 잡고 422로 답한다 — 그 판단이 코드에만 있어 여기서 고정한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class InvitationServiceTest {

    private static final Instant NOW = Instant.now();
    private static final UUID 한빛오피스 = UUID.randomUUID();
    private static final UUID 김서연 = UUID.randomUUID();
    private static final UUID 박지훈 = UUID.randomUUID();

    private static final AccessContext 김서연_관리자 =
            new AccessContext(한빛오피스, 김서연, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
    private static final AccessContext 박지훈_영업 =
            new AccessContext(한빛오피스, 박지훈, Role.SALES_REP, AccessScope.OWNED_ONLY);

    private static final String 초대_주소 = "http://localhost:5173/invite";

    @Mock private InvitationRepository invitationRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private InvitationTokenGenerator tokenGenerator;
    @Mock private CompanyQuery companyQuery;
    @Mock private MailCommand mailCommand;

    private InvitationService invitationService;

    @BeforeEach
    void setUp() {
        invitationService = new InvitationService(
                invitationRepository, memberRepository, tokenGenerator,
                companyQuery, mailCommand, 초대_주소);
    }

    /** 07 §A 역할 칸 "기업 관리자" — 영업 담당자가 사람을 부를 수 있으면 조직이 통제되지 않는다. */
    @Test
    void 영업_담당자는_초대할_수_없다() {
        // given — 박지훈이 새 사람을 부르려 한다
        // when  — 초대를 요청하면
        // then  — 403이고, 이메일 조회에도 닿지 않는다
        assertThatThrownBy(() -> invitationService.create(
                박지훈_영업, new CreateInvitationRequest("newbie@hanbit.co.kr", "SALES_REP")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);

        then(memberRepository).shouldHaveNoInteractions();
        then(invitationRepository).shouldHaveNoInteractions();
    }

    /**
     * 05 §3에 없는 경우 — uk_invitation_pending 이 회사·이메일당 대기 행을 하나로 묶는다.
     * 서비스가 먼저 잡지 않으면 INSERT 가 인덱스에 걸려 500 이 나간다.
     */
    @Test
    void 대기_초대가_살아_있는_이메일은_다시_초대할_수_없다() {
        // given — 어제 보낸 초대가 아직 대기 중이고 기한도 남았다 (7일 유효)
        Invitation 살아있는_초대 = Invitation.issue(
                한빛오피스, 김서연, "newbie@hanbit.co.kr", Role.SALES_REP, "hash", NOW);
        given(memberRepository.findByEmailLower("newbie@hanbit.co.kr")).willReturn(Optional.empty());
        given(invitationRepository.findByCompanyIdAndEmailAndStatus(
                한빛오피스, "newbie@hanbit.co.kr", Invitation.Status.PENDING))
                .willReturn(Optional.of(살아있는_초대));

        // when — 김서연이 같은 사람을 또 부르면
        // then  — 그 이메일은 이미 쓰이고 있다. 새 행을 만들지 않는다
        assertThatThrownBy(() -> invitationService.create(
                김서연_관리자, new CreateInvitationRequest("newbie@hanbit.co.kr", "SALES_REP")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_MEMBER);

        then(invitationRepository).should(never()).save(any());
    }

    /** SC-01·09 — 회사 조건이 where에 있어 남의 회사 초대는 조회에 나오지 않는다. */
    @Test
    void 다른_회사의_초대는_취소할_수_없다() {
        // given — 김서연이 다른 회사가 보낸 초대의 id를 경로에 넣는다
        UUID 남의회사_초대 = UUID.randomUUID();
        given(invitationRepository.findByIdAndCompanyId(남의회사_초대, 한빛오피스))
                .willReturn(Optional.empty());

        // when — 취소를 요청하면
        // then  — 권한 없음(403)이 아니라 없음(404)이다
        assertThatThrownBy(() -> invitationService.cancel(김서연_관리자, 남의회사_초대))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        // and — 회사 조건이 where에 실려 나갔다
        then(invitationRepository).should().findByIdAndCompanyId(남의회사_초대, 한빛오피스);
    }

    @Nested
    class 초대_안내_메일은 {

        private static final String 초대이메일 = "newbie@hanbit.co.kr";
        private static final String 원문토큰 = "invite-raw-token-0123456789";
        private static final UUID 초대id = UUID.randomUUID();

        /** NT-01 — 링크가 도달하지 않으면 초대 행만 남고 아무도 들어오지 못한다. */
        @Test
        void 초대를_발송하면_안내_메일이_예약된다() {
            // given — 김서연이 아직 어디에도 속하지 않은 사람을 부른다
            발송_준비();

            // when — 초대를 발송하면
            invitationService.create(김서연_관리자, new CreateInvitationRequest(초대이메일, "SALES_REP"));

            // then — 초대받은 사람 앞으로 회사 이름표를 단 초대 안내가 예약된다
            then(mailCommand).should().schedule(
                    eq(MailCommand.TemplateType.INVITATION),
                    eq(한빛오피스),
                    eq(초대이메일),
                    any(UUID.class),
                    eq("[2JO] 초대 안내"),
                    any(String.class));
        }

        /** MB-03 — 로그에도 DB에도 본문이 남지 않는다. 링크가 틀렸는지 알 수 있는 곳은 여기뿐이다. */
        @Test
        void 안내_메일은_수락_화면_링크와_원문_토큰을_담는다() {
            발송_준비();

            invitationService.create(김서연_관리자, new CreateInvitationRequest(초대이메일, "SALES_REP"));

            assertThat(예약된_본문()).contains(초대_주소 + "/" + 원문토큰);
        }

        /** Q-34 (7일) — 서버가 KST로 바꿔 넣는다. UTC로 나가면 아홉 시간 이른 시각을 보게 된다. */
        @Test
        void 안내_메일은_초대_유효기간을_KST로_담는다() {
            발송_준비();

            invitationService.create(김서연_관리자, new CreateInvitationRequest(초대이메일, "SALES_REP"));

            Instant 만료 = 저장된_초대().getExpiresAt();
            assertThat(예약된_본문())
                    .contains(시각(만료, "Asia/Seoul"))
                    .doesNotContain(시각(만료, "UTC"));
        }

        /** 근거 ID가 없는 구현 판단 — 받는 사람이 누가 왜 불렀는지 알 수 있어야 한다. */
        @Test
        void 안내_메일은_초대한_회사_이름을_담는다() {
            발송_준비();

            invitationService.create(김서연_관리자, new CreateInvitationRequest(초대이메일, "SALES_REP"));

            assertThat(예약된_본문()).contains("한빛오피스");
        }

        /** Q-31 — 재발송이 행을 새로 만드니 발송 기록도 행마다 하나다. 옛 id를 넘기면 중복 키에 막힌다. */
        @Test
        void 발송_기록은_초대_행마다_하나씩_생긴다() {
            발송_준비();

            invitationService.create(김서연_관리자, new CreateInvitationRequest(초대이메일, "SALES_REP"));

            assertThat(예약된_발송_식별자()).isEqualTo(초대id);
        }

        /** MB-06 · Q-31 — 재발송은 옛 행을 닫고 새 행을 연다. 새 행의 id로 다시 나가야 한다. */
        @Test
        void 재발송하면_안내_메일이_다시_예약된다() {
            // given — 어제 보낸 초대가 아직 대기 중이다
            UUID 옛_초대id = UUID.randomUUID();
            given(invitationRepository.findByIdAndCompanyId(옛_초대id, 한빛오피스)).willReturn(
                    Optional.of(Invitation.issue(
                            한빛오피스, 김서연, 초대이메일, Role.SALES_REP, "옛해시", NOW)));
            토큰과_저장을_준비();
            회사를_준비();

            // when — 재발송을 누르면
            invitationService.resend(김서연_관리자, 옛_초대id);

            // then — 새로 열린 행의 id로 메일이 한 번 더 예약된다
            assertThat(예약된_발송_식별자()).isEqualTo(초대id).isNotEqualTo(옛_초대id);
        }

        /** MB-05 — 종결된 초대의 링크가 다시 나가면 안 된다. */
        @Test
        void 초대를_취소해도_메일은_나가지_않는다() {
            // given — 대기 중인 초대가 하나 있다
            UUID 초대 = UUID.randomUUID();
            given(invitationRepository.findByIdAndCompanyId(초대, 한빛오피스)).willReturn(
                    Optional.of(Invitation.issue(
                            한빛오피스, 김서연, 초대이메일, Role.SALES_REP, "해시", NOW)));

            // when — 김서연이 취소하면
            invitationService.cancel(김서연_관리자, 초대);

            // then — 발송 통로에 닿지 않는다
            then(mailCommand).shouldHaveNoInteractions();
        }

        /** 운영 환경변수에 "/"가 붙어 오면 //토큰이 되어 프론트 라우트가 안 잡힌다. */
        @Test
        void 주소_끝에_슬래시가_있어도_링크는_한_번만_구분된다() {
            // given — 주소 끝에 슬래시가 붙은 채로 주입됐다
            InvitationService 슬래시_붙은_주소 = new InvitationService(
                    invitationRepository, memberRepository, tokenGenerator,
                    companyQuery, mailCommand, 초대_주소 + "/");
            발송_준비();

            // when — 그 인스턴스로 초대를 발송하면
            슬래시_붙은_주소.create(김서연_관리자, new CreateInvitationRequest(초대이메일, "SALES_REP"));

            // then — 구분자는 한 번뿐이다
            assertThat(예약된_본문())
                    .contains(초대_주소 + "/" + 원문토큰)
                    .doesNotContain("//" + 원문토큰);
        }

        private void 발송_준비() {
            given(memberRepository.findByEmailLower(초대이메일)).willReturn(Optional.empty());
            given(invitationRepository.findByCompanyIdAndEmailAndStatus(
                    한빛오피스, 초대이메일, Invitation.Status.PENDING)).willReturn(Optional.empty());
            토큰과_저장을_준비();
            회사를_준비();
        }

        /** save가 id를 채워 돌려주는 것을 흉내 낸다 — 발송 식별자가 그 id다. */
        private void 토큰과_저장을_준비() {
            given(tokenGenerator.generate()).willReturn(원문토큰);
            given(tokenGenerator.hash(원문토큰)).willReturn("해시");
            given(invitationRepository.save(any(Invitation.class))).willAnswer(호출 -> {
                Invitation 저장될_초대 = 호출.getArgument(0);
                ReflectionTestUtils.setField(저장될_초대, "id", 초대id);
                return 저장될_초대;
            });
        }

        private void 회사를_준비() {
            given(companyQuery.get(한빛오피스)).willReturn(
                    new CompanyQuery.CompanySummary(한빛오피스, "한빛오피스", "123-45-67890", true));
        }

        private String 예약된_본문() {
            ArgumentCaptor<String> 본문 = ArgumentCaptor.forClass(String.class);
            then(mailCommand).should().schedule(any(), any(), any(), any(), any(), 본문.capture());
            return 본문.getValue();
        }

        private UUID 예약된_발송_식별자() {
            ArgumentCaptor<UUID> 식별자 = ArgumentCaptor.forClass(UUID.class);
            then(mailCommand).should().schedule(any(), any(), any(), 식별자.capture(), any(), any());
            return 식별자.getValue();
        }

        private Invitation 저장된_초대() {
            ArgumentCaptor<Invitation> 저장 = ArgumentCaptor.forClass(Invitation.class);
            then(invitationRepository).should().save(저장.capture());
            return 저장.getValue();
        }

        private String 시각(Instant 순간, String 지역) {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
                    .format(순간.atZone(ZoneId.of(지역)));
        }
    }
}
