package com.twojo.member.service;

import com.twojo.boundary.CompanyQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.member.dto.AcceptInvitationRequest;
import com.twojo.member.dto.InvitationInfoResponse;
import com.twojo.member.entity.Invitation;
import com.twojo.member.entity.Member;
import com.twojo.member.repository.InvitationRepository;
import com.twojo.member.repository.MemberRepository;
import com.twojo.member.token.InvitationTokenGenerator;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 초대 링크로 들어오는 확인·수락 (MB-03·04) — 로그인하지 않은 사람이 부른다.
 *
 * <p>자격 증명은 URL의 토큰 하나뿐이라 AccessContext가 없다. 회사는 요청자가 알려주는 것이
 * 아니라 찾아낸 초대 행이 알려준다 — 남의 회사에 계정을 만들 경로가 생기지 않는다.
 *
 * <p>클래스에 readOnly를 걸지 않는다. 조회조차 기한이 지난 초대를 만나면 그 자리에서
 * 만료로 넘긴다 — 만료를 돌려주는 배치가 없다.
 */
@Service
@RequiredArgsConstructor
public class PublicInvitationService {

    private final InvitationRepository invitationRepository;
    private final MemberRepository memberRepository;
    private final InvitationTokenGenerator tokenGenerator;
    private final PasswordEncoder passwordEncoder;
    private final CompanyQuery companyQuery;

    /** 확인 (MB-03) — 수락 화면이 "어느 회사가 어떤 역할로 불렀는가"를 그릴 값. */
    @Transactional
    public InvitationInfoResponse info(String rawToken) {
        Invitation invitation = requireUsable(rawToken, Instant.now());

        String companyName = companyQuery.get(invitation.getCompanyId()).name();

        return new InvitationInfoResponse(companyName, invitation.getEmail(),
                invitation.getRole().name());
    }

    /**
     * 수락 (MB-03) — 계정을 만들고 초대를 닫는다.
     *
     * <p>발송 시점에 없던 계정이 7일 사이에 생겼을 수 있다. 다시 확인하지 않으면 이메일
     * 전역 유일 제약에 걸려 500이 된다.
     */
    @Transactional
    public void accept(String rawToken, AcceptInvitationRequest request) {
        Instant now = Instant.now();
        Invitation invitation = requireUsable(rawToken, now);

        if (memberRepository.findByEmailLower(invitation.getEmail()).isPresent()) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_MEMBER);
        }
        memberRepository.save(Member.invited(
                invitation.getCompanyId(), invitation.getEmail(),
                passwordEncoder.encode(request.password()), request.name(),
                invitation.getRole(), now));

        invitation.accept(now);
    }

    /**
     * 토큰으로 쓸 수 있는 초대를 찾는다.
     *
     * <p>기한이 지난 대기 행은 여기서 EXPIRED(TIME)로 넘긴 뒤 거절한다. 이미 종결된 행의
     * 기한은 건드리지 않는다 — 수락·취소 기록을 만료가 덮으면 안 된다.
     *
     * <p>now를 인자로 받는 이유는 수락이 계정 생성과 초대 종결에 같은 시각을 써야 하기
     * 때문이다. 여기서 새로 부르면 두 행의 시각이 어긋난다.
     */
    private Invitation requireUsable(String rawToken, Instant now) {
        Invitation invitation = invitationRepository.findByTokenHash(tokenGenerator.hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        if (invitation.isPending() && invitation.isExpired(now)) {
            invitation.expire(Invitation.ExpiredReason.TIME, now);
        }
        if (!invitation.isPending()) {
            throw new BusinessException(ErrorCode.INVITATION_NOT_PENDING);
        }
        return invitation;
    }
}
