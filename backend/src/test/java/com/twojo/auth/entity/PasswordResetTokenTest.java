package com.twojo.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.auth.entity.PasswordResetToken.Purpose;
import com.twojo.auth.entity.PasswordResetToken.Status;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * 재설정 토큰의 세 전이 (05 §10 · Q-33·34).
 *
 * <p>수명 경과 -> EXPIRED 전이는 배치 소유인데(Q-34) 그 배치가 아직 없다. 그래서
 * "상태는 ACTIVE인데 수명이 지난" 행이 실제로 존재하고, use()의 시각 비교가 유일한
 * 방어선이다 — 그 조건이 지워져도 상태만 보는 테스트는 초록불이 된다.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PasswordResetTokenTest {

    private static final UUID 김서연 = UUID.randomUUID();
    private static final Instant 발급_시각 = Instant.parse("2026-09-02T11:20:33Z");
    private static final Instant 사용_시각 = Instant.parse("2026-09-02T11:35:00Z");

    /** Q-34 — RESET 30분. 발급 지점이 둘(AU-05 요청 · ON-07 승인)이라 값이 흩어지면 한쪽만 고친다 */
    @Test
    void 재설정_토큰은_발급_30분_뒤에_만료된다() {
        // given · when — 김서연이 재설정을 요청해 RESET 토큰이 발급되면
        PasswordResetToken token = 발급된_토큰(Purpose.RESET);

        // then — 11:20:33 + 30분
        assertThat(token.getExpiresAt()).isEqualTo(Instant.parse("2026-09-02T11:50:33Z"));
    }

    /** Q-33·34 — 가입 승인 링크는 수신자가 언제 열지 몰라 7일이다 */
    @Test
    void 비밀번호_설정_토큰은_발급_7일_뒤에_만료된다() {
        // given · when — 가입이 승인돼 INITIAL_SETUP 토큰이 발급되면
        PasswordResetToken token = 발급된_토큰(Purpose.INITIAL_SETUP);

        // then — 같은 코드 줄이 다른 값을 낸다 (수명은 Purpose가 들고 있다)
        assertThat(token.getExpiresAt()).isEqualTo(Instant.parse("2026-09-09T11:20:33Z"));
    }

    /** 05 §10 — 활성(ACTIVE) -> 사용됨(USED) 전이 */
    @Test
    void 토큰을_사용하면_사용됨_상태와_사용_시각이_남는다() {
        // given — 아직 쓰지 않은 재설정 토큰이 있다
        PasswordResetToken token = 발급된_토큰(Purpose.RESET);

        // when — 링크를 열어 새 비밀번호를 설정하면
        token.use(사용_시각);

        // then — 언제 썼는지가 남아야 이후 감사에서 재사용 여부를 가릴 수 있다
        assertThat(token.getStatus()).isEqualTo(Status.USED);
        assertThat(token.getUsedAt()).isEqualTo(사용_시각);
    }

    /** 05 §10 "막히는 것" — 1회성이 깨지면 링크가 영구히 재사용된다 */
    @Test
    void 이미_사용된_토큰은_다시_사용할_수_없다() {
        // given — 김서연이 이미 그 링크로 비밀번호를 바꿨다
        PasswordResetToken token = 발급된_토큰(Purpose.RESET);
        token.use(사용_시각);

        // when · then — 같은 링크로 다시 들어오면 막힌다
        assertThatThrownBy(() -> token.use(사용_시각.plusSeconds(60)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESET_TOKEN_NOT_ACTIVE);
    }

    /** Q-34 — 만료 배치가 아직 없어 상태만 보면 뚫린다. isUsableAt의 시각 비교가 유일한 방어선이다 */
    @Test
    void 수명이_지난_토큰은_상태가_활성이어도_사용할_수_없다() {
        // given — 발급 30분이 지났지만 배치가 돌지 않아 상태는 아직 ACTIVE다
        PasswordResetToken token = 발급된_토큰(Purpose.RESET);

        // when · then — 31분 뒤에 링크를 열면 상태와 무관하게 막힌다
        assertThat(token.getStatus()).isEqualTo(Status.ACTIVE);
        assertThatThrownBy(() -> token.use(발급_시각.plusSeconds(31 * 60)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.RESET_TOKEN_NOT_ACTIVE);
    }

    /** 05 §10 — USED를 EXPIRED로 덮으면 그 링크를 실제로 썼는지가 사라진다 */
    @Test
    void 이미_종결된_토큰은_만료_처리해도_사용_여부가_지워지지_않는다() {
        // given — 이미 사용된 토큰이 있다
        PasswordResetToken token = 발급된_토큰(Purpose.RESET);
        token.use(사용_시각);

        // when — 재요청 흐름이 그 행에 만료 처리를 시도하면
        token.expire();

        // then — 아무 일도 일어나지 않는다
        assertThat(token.getStatus()).isEqualTo(Status.USED);
        assertThat(token.getUsedAt()).isEqualTo(사용_시각);
    }

    private PasswordResetToken 발급된_토큰(Purpose purpose) {
        return PasswordResetToken.issue(김서연, purpose, "a3f1c0", 발급_시각);
    }
}
