package com.twojo.member.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
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

    /**
     * Q-33 · 전이표 §1 — 승인은 계정만 만들고 비밀번호는 본인이 링크로 정한다.
     *
     * <p>해시가 비어 있는 것이 로그인 차단의 근거다. 여기에 임의의 값이라도 들어가면
     * 그 값을 아는 경로가 생기는 셈이고, 설정 링크를 받기 전에 로그인이 뚫린다.
     *
     * <p>{@code passwordChangedAt}도 비어야 한다 — 최초 설정이 곧 첫 변경 시점이라
     * 그때 찍힌다. 지금으로 찍으면 "이 시각 이후 토큰만 유효"의 기준이 거짓이 된다.
     */
    @Test
    void 가입_승인으로_생긴_관리자_계정은_비밀번호가_없다() {
        // given, when — 한빛오피스의 신청이 승인되어 김서연 계정이 만들어지면
        Member 김서연 = Member.companyAdmin(
                UUID.randomUUID(), "seoyeon@hanbit.co.kr", "김서연");

        // then — 계정은 활성이지만 비밀번호를 아직 갖지 않는다
        assertThat(김서연.isActive()).isTrue();
        assertThat(김서연.hasPassword()).isFalse();
        assertThat(김서연.getPasswordHash()).isNull();
        assertThat(김서연.getPasswordChangedAt()).isNull();
    }
}
