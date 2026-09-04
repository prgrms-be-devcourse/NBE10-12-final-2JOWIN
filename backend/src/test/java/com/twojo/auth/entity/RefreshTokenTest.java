package com.twojo.auth.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/** 한 표를 나눠 쓰는 두 주체 (06 refresh_token · AU-08). */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RefreshTokenTest {

    private static final Instant NOW = Instant.parse("2026-09-03T10:00:00Z");

    /** 06 CHECK — 두 주인 컬럼 중 정확히 하나. 둘 다 차거나 둘 다 비면 INSERT 가 거부된다 */
    @Test
    void 관리자_세션은_관리자_id만_채우고_구성원_id는_비운다() {
        // given — 관리자 로그인이 성공해 세션 행을 만드는 상황
        UUID adminId = UUID.randomUUID();

        // when — 관리자용 팩토리로 발급하면
        RefreshToken token = RefreshToken.issueForPlatformAdmin(
                adminId, "해시", NOW.plus(Duration.ofDays(14)));

        // then — 자기 주인 컬럼만 차고 반대쪽은 비어 있다
        assertThat(token.getPlatformAdminId()).isEqualTo(adminId);
        assertThat(token.getMemberId()).isNull();
        assertThat(token.getActorType()).isEqualTo(ActorType.PLATFORM_ADMIN);
    }
}
