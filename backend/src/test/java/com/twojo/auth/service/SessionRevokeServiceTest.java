package com.twojo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.twojo.auth.entity.RefreshToken;
import com.twojo.auth.repository.RefreshTokenRepository;
import com.twojo.boundary.MemberQuery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 정지·비활성 시 세션 일괄 폐기 (전이표 §9 · ON-08·09 · MB-09·10).
 *
 * <p>09가 "즉시 차단"이라 적은 문장의 실체다. 안 끊기면 비활성화·정지된 뒤에도
 * 이미 로그인해 있던 창에서 최대 14일간 이용이 계속되는데, 응답만 봐서는 드러나지 않는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SessionRevokeServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-01T16:00:00Z");
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID 김서연 = UUID.randomUUID();
    private static final UUID 박지훈 = UUID.randomUUID();

    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private MemberQuery memberQuery;

    private SessionRevokeService sessionRevokeService;

    @BeforeEach
    void setUp() {
        sessionRevokeService = new SessionRevokeService(refreshTokenRepository, memberQuery);
    }

    /** MB-09·10 · 09 전역 규칙 — 비활성화의 "즉시 차단"은 세션 폐기로만 실현된다 */
    @Test
    void 구성원을_비활성화하면_모든_기기_세션이_MEMBER_DEACTIVATED로_폐기된다() {
        // given — 김서연이 노트북과 휴대폰 두 대에서 로그인해 있다
        RefreshToken 노트북 = 활성_토큰(김서연, "hash-laptop");
        RefreshToken 휴대폰 = 활성_토큰(김서연, "hash-phone");
        given(refreshTokenRepository.findByMemberIdAndStatus(김서연, RefreshToken.Status.ACTIVE))
                .willReturn(List.of(노트북, 휴대폰));

        // when — 기업 관리자가 김서연을 비활성화하면
        sessionRevokeService.revokeOnDeactivation(김서연, NOW);

        // then — 한 대만 끊고 끝내면 나머지로 계속 들어올 수 있다
        assertThat(List.of(노트북, 휴대폰)).allSatisfy(token -> {
            assertThat(token.getStatus()).isEqualTo(RefreshToken.Status.REVOKED);
            assertThat(token.getRevokedReason())
                    .isEqualTo(RefreshToken.RevokedReason.MEMBER_DEACTIVATED);
        });
    }

    /** ON-08·09 — refresh_token에 company_id가 없어 구성원 목록을 거치는 유일한 경로다 */
    @Test
    void 회사를_정지하면_전_구성원의_세션이_COMPANY_SUSPENDED로_폐기된다() {
        // given — 한빛오피스에 활성 구성원 둘, 각자 세션 하나씩
        RefreshToken 김서연_세션 = 활성_토큰(김서연, "hash-1");
        RefreshToken 박지훈_세션 = 활성_토큰(박지훈, "hash-2");
        given(memberQuery.findAllActive(COMPANY_ID)).willReturn(List.of(
                new MemberQuery.MemberSummary(김서연, "김서연", true),
                new MemberQuery.MemberSummary(박지훈, "박지훈", true)));
        given(refreshTokenRepository.findByMemberIdInAndStatus(
                        List.of(김서연, 박지훈), RefreshToken.Status.ACTIVE))
                .willReturn(List.of(김서연_세션, 박지훈_세션));

        // when — 플랫폼 관리자가 회사를 정지시키면
        sessionRevokeService.revokeOnSuspension(COMPANY_ID, NOW);

        // then — 역할과 무관하게 전원이 끊긴다 (09 "회사 정지 시" 행)
        assertThat(List.of(김서연_세션, 박지훈_세션)).allSatisfy(token -> {
            assertThat(token.getStatus()).isEqualTo(RefreshToken.Status.REVOKED);
            assertThat(token.getRevokedReason())
                    .isEqualTo(RefreshToken.RevokedReason.COMPANY_SUSPENDED);
        });
    }

    private RefreshToken 활성_토큰(UUID memberId, String tokenHash) {
        return RefreshToken.issueForMember(memberId, tokenHash, NOW.plusSeconds(3600));
    }
}
