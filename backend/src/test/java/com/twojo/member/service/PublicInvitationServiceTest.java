package com.twojo.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.member.dto.AcceptInvitationRequest;
import com.twojo.member.entity.Invitation;
import com.twojo.member.entity.Member;
import com.twojo.member.repository.InvitationRepository;
import com.twojo.member.repository.MemberRepository;
import com.twojo.member.token.InvitationTokenGenerator;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 초대 수락 — 발송과 수락 사이 7일의 공백 (MB-03·04).
 *
 * <p>05 §3은 <b>발송 시점</b>의 이메일 점유만 규정한다. 그 사이 다른 회사가 같은 사람을
 * 먼저 데려갈 수 있고, 다시 확인하지 않으면 이메일 전역 유일 제약에 걸려 500이 된다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PublicInvitationServiceTest {

    private static final Instant NOW = Instant.now();
    private static final UUID 한빛오피스 = UUID.randomUUID();
    private static final UUID 김서연 = UUID.randomUUID();
    private static final String 원문토큰 = "kJ9xQm2LinkToken";

    @Mock private InvitationRepository invitationRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CompanyQuery companyQuery;

    private final InvitationTokenGenerator tokenGenerator = new InvitationTokenGenerator();

    private PublicInvitationService publicInvitationService;

    @BeforeEach
    void setUp() {
        publicInvitationService = new PublicInvitationService(
                invitationRepository, memberRepository, tokenGenerator, passwordEncoder, companyQuery);
    }

    /** 05 §3 EMAIL_ALREADY_MEMBER — 발송 시점 검사만으로는 7일의 공백을 막지 못한다. */
    @Test
    void 수락_시점에_이미_계정이_있으면_수락할_수_없다() {
        // given — 한빛오피스가 부른 사람이 그 사이 다른 회사에 입사해 계정을 갖게 됐다
        Invitation 대기중_초대 = Invitation.issue(
                한빛오피스, 김서연, "newbie@hanbit.co.kr", Role.SALES_REP,
                tokenGenerator.hash(원문토큰), NOW);
        given(invitationRepository.findByTokenHash(tokenGenerator.hash(원문토큰)))
                .willReturn(Optional.of(대기중_초대));
        given(memberRepository.findByEmailLower("newbie@hanbit.co.kr"))
                .willReturn(Optional.of(Member.invited(
                        UUID.randomUUID(), "newbie@hanbit.co.kr", "$2a$10$K7Lm",
                        "한지민", Role.SALES_REP, NOW)));

        // when — 아직 유효한 링크로 수락을 시도하면
        // then  — 계정을 만들지 않고 거절한다. 그냥 두면 DB 제약에 걸려 500이 된다
        assertThatThrownBy(() -> publicInvitationService.accept(
                원문토큰, new AcceptInvitationRequest("한지민", "test1234!")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_MEMBER);

        then(memberRepository).should(never()).save(any());
    }
}
