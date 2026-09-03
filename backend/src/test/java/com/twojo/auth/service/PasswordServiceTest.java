package com.twojo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.twojo.auth.SessionRevoker;
import com.twojo.auth.dto.ChangePasswordRequest;
import com.twojo.auth.dto.ExecutePasswordResetRequest;
import com.twojo.auth.dto.RequestPasswordResetRequest;
import com.twojo.auth.entity.PasswordResetToken;
import com.twojo.auth.repository.PasswordResetTokenRepository;
import com.twojo.auth.token.SecureTokenFactory;
import com.twojo.boundary.MailCommand;
import com.twojo.boundary.MemberCommand;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 비밀번호 변경·재설정 (AU-04·05).
 *
 * <p><b>PasswordEncoder는 목이 아니라 실물이다.</b> 여기서 막으려는 위험 하나가
 * "저장된 해시가 NULL인 계정"인데(Q-33), 그 처리는 BCryptPasswordEncoder 안에 있다.
 * 목으로 바꾸면 우리가 정한 답을 우리가 확인하는 테스트가 된다.
 *
 * <p>SecureTokenFactory는 스파이다 — hash()는 실물이어야 대조가 의미 있고,
 * generate()는 무작위라 값을 알아야 원문 미저장을 단정할 수 있다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PasswordServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T11:20:33Z");
    private static final UUID 김서연 = UUID.randomUUID();
    private static final UUID 한빛오피스 = UUID.randomUUID();
    private static final String 이메일 = "seoyeon@hanbit.co.kr";
    private static final String 재설정_주소 = "http://localhost:5173/password-reset";

    @Mock private MemberQuery memberQuery;
    @Mock private MemberCommand memberCommand;
    @Mock private SessionRevoker sessionRevoker;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private MailCommand mailCommand;

    @Spy private SecureTokenFactory secureTokenFactory = new SecureTokenFactory();

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private PasswordService passwordService;

    @BeforeEach
    void setUp() {
        passwordService = new PasswordService(
                memberQuery, memberCommand, sessionRevoker, passwordEncoder,
                passwordResetTokenRepository, secureTokenFactory, mailCommand, 재설정_주소);
    }

    @Nested
    class 비밀번호_변경은 {

        /** 07 §A v1.6.5 — 401이 아니다. 세션은 유효하고 값만 틀렸다 */
        @Test
        void 현재_비밀번호가_틀리면_변경할_수_없다() {
            // given — 김서연의 저장된 비밀번호는 test1234! 다
            given(memberQuery.getCredential(김서연)).willReturn(자격(해시("test1234!")));

            // when · then — 다른 값을 넣으면 422로 막힌다
            assertThatThrownBy(() -> passwordService.change(김서연, 변경_요청("틀린값", "newpass123!"), NOW))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        /**
         * 05 §9 — 검증 실패가 부수효과를 남기면, 틀린 비밀번호로도 남의 세션을 끊을 수 있다.
         * 협력자가 다른 모듈이라 상태를 볼 수 없어 "호출되지 않았음"으로 확인한다.
         */
        @Test
        void 현재_비밀번호가_틀리면_비밀번호도_세션도_바뀌지_않는다() {
            // given — 저장된 비밀번호와 다른 값이 들어온다
            given(memberQuery.getCredential(김서연)).willReturn(자격(해시("test1234!")));

            // when — 변경을 시도하면 예외로 끝나고
            assertThatThrownBy(() -> passwordService.change(김서연, 변경_요청("틀린값", "newpass123!"), NOW))
                    .isInstanceOf(BusinessException.class);

            // then — 뒤의 두 호출은 시작되지도 않는다
            verifyNoInteractions(memberCommand, sessionRevoker);
        }

        /** Q-33 — 가입 승인 직후 계정은 password_hash가 NULL이다. NPE면 500이 나간다 */
        @Test
        void 비밀번호가_설정되지_않은_계정도_같은_응답을_받는다() {
            // given — 아직 비밀번호를 설정하지 않은 계정
            given(memberQuery.getCredential(김서연)).willReturn(자격(null));

            // when · then — 500이 아니라 다른 실패와 같은 422다
            assertThatThrownBy(() -> passwordService.change(김서연, 변경_요청("아무값", "newpass123!"), NOW))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        /** 05 §9:160 — 전이표 여섯 전이의 마지막 칸. 본인 세션도 예외가 아니다 */
        @Test
        void 비밀번호를_바꾸면_그_구성원의_세션이_전부_폐기된다() {
            // given — 올바른 현재 비밀번호로 변경을 요청한다
            given(memberQuery.getCredential(김서연)).willReturn(자격(해시("test1234!")));

            // when
            passwordService.change(김서연, 변경_요청("test1234!", "newpass123!"), NOW);

            // then — 비밀번호만 바뀌고 세션이 남으면 유출된 세션이 그대로 살아 있다
            verify(sessionRevoker).revokeOnPasswordChange(eq(김서연), any());
        }

        /** 06 — password_changed_at이 "이 시각 이후 발급된 토큰만 유효"의 기준이다 */
        @Test
        void 비밀번호_변경_시각과_세션_폐기_시각이_같다() {
            // given
            given(memberQuery.getCredential(김서연)).willReturn(자격(해시("test1234!")));

            // when
            passwordService.change(김서연, 변경_요청("test1234!", "newpass123!"), NOW);

            // then — 두 시각이 어긋나면 "언제부터 무효인가"를 나중에 판정할 수 없다
            ArgumentCaptor<Instant> 변경_시각 = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<Instant> 폐기_시각 = ArgumentCaptor.forClass(Instant.class);
            verify(memberCommand).changePassword(eq(김서연), any(), 변경_시각.capture());
            verify(sessionRevoker).revokeOnPasswordChange(eq(김서연), 폐기_시각.capture());

            assertThat(변경_시각.getValue()).isEqualTo(폐기_시각.getValue());
        }
    }

    @Nested
    class 재설정_요청은 {

        /** SC-09 인증 확장 — 응답이 갈리면 이 엔드포인트로 가입 여부를 훑을 수 있다 */
        @Test
        void 가입되지_않은_이메일로_요청해도_토큰이_발급되지_않는다() {
            // given — 그런 구성원이 없다
            given(memberQuery.findCredentialByEmail("nobody@twojo.test")).willReturn(Optional.empty());

            // when — 요청은 예외 없이 끝나고
            passwordService.requestReset(new RequestPasswordResetRequest("nobody@twojo.test"), NOW);

            // then — 저장소를 건드리지 않는다 (응답은 가입된 경우와 같은 202다)
            verifyNoInteractions(passwordResetTokenRepository);
        }

        /** 14 §2-1 — 원문이 저장되면 DB 유출 시 전 계정의 재설정 링크가 만들어진다 */
        @Test
        void 발급된_토큰은_원문이_아니라_해시로_저장된다() {
            // given — 활성 토큰이 아직 없고, 발급될 원문을 고정한다
            given(memberQuery.findCredentialByEmail(이메일)).willReturn(Optional.of(자격(해시("test1234!"))));
            given(passwordResetTokenRepository.findByMemberIdAndStatus(
                            김서연, PasswordResetToken.Status.ACTIVE))
                    .willReturn(Optional.empty());
            willReturn("3Jv8Qw2ZpK1nL7xT").given(secureTokenFactory).generate();
            // 저장한 토큰을 그대로 돌려준다 — 실제 JPA 와 같다. 뒤에서 이 반환값의 id 와 만료 시각을 쓴다
            given(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                    .willAnswer(호출 -> 호출.getArgument(0));

            // when
            passwordService.requestReset(new RequestPasswordResetRequest(이메일), NOW);

            // then — 저장된 값은 원문이 아니라 그 SHA-256이다
            ArgumentCaptor<PasswordResetToken> 저장된_토큰 =
                    ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(passwordResetTokenRepository).save(저장된_토큰.capture());

            assertThat(저장된_토큰.getValue().getTokenHash())
                    .isNotEqualTo("3Jv8Qw2ZpK1nL7xT")
                    .isEqualTo(new SecureTokenFactory().hash("3Jv8Qw2ZpK1nL7xT"));
        }
    }

    @Nested
    class 재설정_안내_메일은 {

        private static final String 원문_토큰 = "3Jv8Qw2ZpK1nL7xT";

        private final UUID 새_토큰id = UUID.randomUUID();

        /** NT-14 — 링크만 만들어지고 메일이 안 나가면 사용자는 아무것도 받지 못한다 */
        @Test
        void 요청이_접수되면_예약된다() {
            // given — 김서연은 가입된 구성원이고, 살아 있는 재설정 링크는 아직 없다
            예약까지_흐른다(이메일);

            // when — 재설정을 요청하면
            passwordService.requestReset(new RequestPasswordResetRequest(이메일), NOW);

            // then — 비밀번호 재설정 종류로 예약이 걸린다
            assertThat(예약된_메일().종류()).isEqualTo(MailCommand.TemplateType.PASSWORD_RESET);
        }

        /** 표기가 갈리면 중복 방지 키가 갈라져, 재발송이 기존 기록을 덮지 못한다 */
        @Test
        void 정규화된_주소로_간다() {
            // given — 사용자가 대문자와 앞뒤 공백을 달고 입력했다
            예약까지_흐른다("  SeoYeon@Hanbit.co.KR  ");

            // when
            passwordService.requestReset(
                    new RequestPasswordResetRequest("  SeoYeon@Hanbit.co.KR  "), NOW);

            // then — 예약에는 다듬어진 값이 넘어간다
            assertThat(예약된_메일().수신자()).isEqualTo("seoyeon@hanbit.co.kr");
        }

        /** 저장 반환값을 안 쓰면 null 이 넘어가는데, 목은 그것도 조용히 받는다 */
        @Test
        void 방금_발급한_토큰을_가리킨다() {
            // given
            예약까지_흐른다(이메일);

            // when
            passwordService.requestReset(new RequestPasswordResetRequest(이메일), NOW);

            // then — 예약이 가리키는 것은 이 요청으로 저장된 토큰이다
            assertThat(예약된_메일().참조()).isEqualTo(새_토큰id);
        }

        /** 재설정 메일은 회사가 있는 구성원에게만 나간다 — null 이면 예약이 스코프를 잃는다 */
        @Test
        void 회사_식별자와_함께_예약된다() {
            // given
            예약까지_흐른다(이메일);

            // when
            passwordService.requestReset(new RequestPasswordResetRequest(이메일), NOW);

            // then
            assertThat(예약된_메일().회사()).isEqualTo(한빛오피스);
        }

        /** NT-14 — 링크가 이 메일의 전부다. 주소·경로·토큰 중 하나만 빠져도 재설정할 수 없다 */
        @Test
        void 본문에_재설정_링크를_담는다() {
            // given
            예약까지_흐른다(이메일);

            // when
            passwordService.requestReset(new RequestPasswordResetRequest(이메일), NOW);

            // then — 설정된 주소 뒤에 발급된 원문이 쿼리로 붙는다
            assertThat(예약된_메일().본문()).contains(재설정_주소 + "?token=" + 원문_토큰);
        }

        /** 14 §2-1 — 본문에 해시가 실리면 링크가 죽고, DB 에 원문이 남으면 유출 시 전 계정이 열린다 */
        @Test
        void 링크에_실리는_토큰은_원문이고_저장되는_값은_해시다() {
            // given
            예약까지_흐른다(이메일);

            // when
            passwordService.requestReset(new RequestPasswordResetRequest(이메일), NOW);

            // then — 같은 토큰이 본문에는 원문으로, 저장된 행에는 SHA-256 으로 들어간다
            ArgumentCaptor<PasswordResetToken> 저장된_토큰 =
                    ArgumentCaptor.forClass(PasswordResetToken.class);
            verify(passwordResetTokenRepository).save(저장된_토큰.capture());

            assertThat(예약된_메일().본문()).contains(원문_토큰);
            assertThat(저장된_토큰.getValue().getTokenHash())
                    .isEqualTo(new SecureTokenFactory().hash(원문_토큰));
        }

        /** UTC 로 나가면 수신자가 아홉 시간 이른 시각을 보고 링크가 죽은 줄 안다 */
        @Test
        void 만료_시각을_한국_시각으로_적는다() {
            // given — 요청 시각이 UTC 11시 20분이라 만료는 11시 50분이다
            예약까지_흐른다(이메일);

            // when
            passwordService.requestReset(new RequestPasswordResetRequest(이메일), NOW);

            // then — 본문에는 같은 순간이 한국 시각 20시 50분으로 적힌다
            assertThat(예약된_메일().본문()).contains("2026-09-02 20:50");
        }

        /** SC-09 — 미가입인데 메일이 나가면 남의 메일함으로 재설정 링크가 간다 */
        @Test
        void 가입되지_않은_이메일에는_예약되지_않는다() {
            // given — 그런 구성원이 없다
            given(memberQuery.findCredentialByEmail("nobody@twojo.test"))
                    .willReturn(Optional.empty());

            // when — 요청은 예외 없이 끝나고
            passwordService.requestReset(
                    new RequestPasswordResetRequest("nobody@twojo.test"), NOW);

            // then — 메일 예약을 아예 건드리지 않는다
            verifyNoInteractions(mailCommand);
        }

        /** 05 §10 — 예약이 옛 토큰을 가리키면 새 링크가 옛 기록을 덮어 재발송이 성립하지 않는다 */
        @Test
        void 다시_요청하면_새_토큰으로_다시_예약된다() {
            // given — 먼젓번에 받은 링크가 아직 살아 있는데 다시 요청한다
            UUID 옛_토큰id = UUID.randomUUID();
            PasswordResetToken 옛_토큰 = 발급된_토큰(PasswordResetToken.Purpose.RESET);
            ReflectionTestUtils.setField(옛_토큰, "id", 옛_토큰id);

            given(memberQuery.findCredentialByEmail(이메일))
                    .willReturn(Optional.of(자격(해시("test1234!"))));
            given(passwordResetTokenRepository.findByMemberIdAndStatus(
                            김서연, PasswordResetToken.Status.ACTIVE))
                    .willReturn(Optional.of(옛_토큰));
            given(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                    .willAnswer(id를_심는다(새_토큰id));

            // when
            passwordService.requestReset(new RequestPasswordResetRequest(이메일), NOW);

            // then — 예약이 가리키는 것은 방금 만든 링크다
            assertThat(예약된_메일().참조())
                    .isEqualTo(새_토큰id)
                    .isNotEqualTo(옛_토큰id);
        }

        /** 가입된 구성원 · 살아 있는 링크 없음 — 예약까지 그대로 흘러가는 상황 */
        private void 예약까지_흐른다(String 입력한_이메일) {
            given(memberQuery.findCredentialByEmail(입력한_이메일))
                    .willReturn(Optional.of(자격(해시("test1234!"))));
            given(passwordResetTokenRepository.findByMemberIdAndStatus(
                            김서연, PasswordResetToken.Status.ACTIVE))
                    .willReturn(Optional.empty());
            willReturn(원문_토큰).given(secureTokenFactory).generate();
            given(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                    .willAnswer(id를_심는다(새_토큰id));
        }
    }

    @Nested
    class 재설정_실행은 {

        /** 05 §10 — 지어낸 토큰이 통과하면 전 계정이 열린다 */
        @Test
        void 없는_토큰으로는_재설정할_수_없다() {
            // given — 그런 해시의 행이 없다
            given(passwordResetTokenRepository.findByTokenHash(any())).willReturn(Optional.empty());

            // when · then
            assertThatThrownBy(() -> passwordService.executeReset(
                            new ExecutePasswordResetRequest("없는토큰", "newpass123!"), NOW))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESET_TOKEN_NOT_ACTIVE);
        }

        /** SC-09 태도 — 구별하면 "그 토큰은 존재한다"를 알려주게 된다 */
        @Test
        void 사용된_토큰은_없는_토큰과_같은_응답을_낸다() {
            // given — 이미 한 번 쓴 토큰이 그대로 다시 들어온다
            PasswordResetToken 사용된_토큰 = 발급된_토큰(PasswordResetToken.Purpose.RESET);
            사용된_토큰.use(NOW);
            given(passwordResetTokenRepository.findByTokenHash(any()))
                    .willReturn(Optional.of(사용된_토큰));

            // when · then — 없는 토큰과 똑같은 409다
            assertThatThrownBy(() -> passwordService.executeReset(
                            new ExecutePasswordResetRequest("원문", "newpass123!"), NOW.plusSeconds(60)))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESET_TOKEN_NOT_ACTIVE);
        }

        /** 05 §10 — 전이(USED)와 효과(refresh 전 행 폐기)가 함께 일어나야 한다 */
        @Test
        void 재설정에_성공하면_토큰이_사용됨이_되고_세션이_폐기된다() {
            // given — 아직 쓰지 않은 재설정 토큰
            PasswordResetToken 토큰 = 발급된_토큰(PasswordResetToken.Purpose.RESET);
            given(passwordResetTokenRepository.findByTokenHash(any())).willReturn(Optional.of(토큰));

            // when
            passwordService.executeReset(new ExecutePasswordResetRequest("원문", "newpass123!"), NOW);

            // then — 토큰이 살아 있으면 같은 링크로 몇 번이든 다시 바꿀 수 있다
            assertThat(토큰.getStatus()).isEqualTo(PasswordResetToken.Status.USED);
            verify(sessionRevoker).revokeOnPasswordChange(김서연, NOW);
        }

        /** Q-33 — 가입 승인 링크(INITIAL_SETUP)가 password_hash NULL을 해소하는 유일한 경로다 */
        @Test
        void 비밀번호가_없는_계정도_재설정으로_첫_비밀번호를_설정할_수_있다() {
            // given — 가입 승인 시 발급된 7일짜리 토큰
            PasswordResetToken 설정_토큰 = 발급된_토큰(PasswordResetToken.Purpose.INITIAL_SETUP);
            given(passwordResetTokenRepository.findByTokenHash(any()))
                    .willReturn(Optional.of(설정_토큰));

            // when — 현재 비밀번호를 묻지 않고 첫 비밀번호를 설정한다
            passwordService.executeReset(
                    new ExecutePasswordResetRequest("원문", "firstpass123!"), NOW);

            // then — 넘어간 해시가 입력한 비밀번호의 것이다
            ArgumentCaptor<String> 새_해시 = ArgumentCaptor.forClass(String.class);
            verify(memberCommand).changePassword(eq(김서연), 새_해시.capture(), eq(NOW));

            assertThat(passwordEncoder.matches("firstpass123!", 새_해시.getValue())).isTrue();
        }
    }

    private MemberQuery.AuthCredential 자격(String passwordHash) {
        return new MemberQuery.AuthCredential(
                김서연, 한빛오피스, "김서연", Role.COMPANY_ADMIN, true, passwordHash);
    }

    private ChangePasswordRequest 변경_요청(String 현재, String 새것) {
        return new ChangePasswordRequest(현재, 새것);
    }

    private String 해시(String 평문) {
        return passwordEncoder.encode(평문);
    }

    private PasswordResetToken 발급된_토큰(PasswordResetToken.Purpose purpose) {
        return PasswordResetToken.issue(김서연, purpose, "a3f1c0", NOW.minusSeconds(60));
    }

    /**
     * schedule 에 넘어간 여섯 인자를 한 번에 잡는다 — 테스트마다 필요한 칸 하나만 본다.
     * 인자마다 캡터를 따로 세우면 단정 한 줄을 위해 배경이 여섯 줄 붙는다.
     */
    private 예약 예약된_메일() {
        ArgumentCaptor<MailCommand.TemplateType> 종류 =
                ArgumentCaptor.forClass(MailCommand.TemplateType.class);
        ArgumentCaptor<UUID> 회사 = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> 수신자 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UUID> 참조 = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> 제목 = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> 본문 = ArgumentCaptor.forClass(String.class);

        verify(mailCommand).schedule(종류.capture(), 회사.capture(), 수신자.capture(),
                참조.capture(), 제목.capture(), 본문.capture());

        return new 예약(종류.getValue(), 회사.getValue(), 수신자.getValue(),
                참조.getValue(), 제목.getValue(), 본문.getValue());
    }

    private record 예약(MailCommand.TemplateType 종류, UUID 회사, String 수신자,
                       UUID 참조, String 제목, String 본문) {}

    /**
     * 저장하면 id 가 붙는다 — 실제로는 JPA 가 하는 일이라 목에서는 우리가 흉내 낸다.
     * 엔티티에 id 를 넣는 통로가 없어(생성이 JPA 몫이다) 리플렉션으로 심는다.
     */
    private Answer<PasswordResetToken> id를_심는다(UUID 토큰id) {
        return 호출 -> {
            PasswordResetToken 저장될_토큰 = 호출.getArgument(0);
            ReflectionTestUtils.setField(저장될_토큰, "id", 토큰id);
            return 저장될_토큰;
        };
    }
}
