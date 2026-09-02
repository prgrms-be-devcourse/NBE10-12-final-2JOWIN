package com.twojo.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * 구성원 엔티티의 비밀번호 교체 (AU-04·05 · 06 §member).
 *
 * <p>패키지가 com.twojo.member.entity인 이유는 Member의 기본 생성자가 protected라서다
 * (@NoArgsConstructor(access = PROTECTED)). 같은 패키지에서만 만들 수 있다.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MemberTest {

    private static final Instant 변경_시각 = Instant.parse("2026-09-02T11:20:33Z");

    /**
     * 06 — password_changed_at은 "이 시각 이후 발급된 토큰만 유효"의 기준이다.
     * 해시만 바뀌고 시각이 안 남으면 그 판정이 불가능해진다.
     */
    @Test
    void 비밀번호를_바꾸면_해시와_변경_시각이_함께_기록된다() {
        // given — 비밀번호가 아직 설정되지 않은 계정 (가입 승인 직후, Q-33)
        Member member = new Member();

        // when — 새 비밀번호 해시를 설정하면
        member.changePassword("$2a$10$K7LmQz9", 변경_시각);

        // then — 두 컬럼이 함께 채워진다
        assertThat(member.getPasswordHash()).isEqualTo("$2a$10$K7LmQz9");
        assertThat(member.getPasswordChangedAt()).isEqualTo(변경_시각);
    }
}
