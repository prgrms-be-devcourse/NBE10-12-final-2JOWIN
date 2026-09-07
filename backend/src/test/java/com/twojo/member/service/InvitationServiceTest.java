package com.twojo.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.member.dto.CreateInvitationRequest;
import com.twojo.member.entity.Invitation;
import com.twojo.member.repository.InvitationRepository;
import com.twojo.member.repository.MemberRepository;
import com.twojo.member.token.InvitationTokenGenerator;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Mock private InvitationRepository invitationRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private InvitationTokenGenerator tokenGenerator;

    @InjectMocks private InvitationService invitationService;

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
}
