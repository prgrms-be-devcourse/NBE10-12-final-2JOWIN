package com.twojo.member.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.CompanyQuery;
import com.twojo.global.error.MissingReferenceException;
import com.twojo.member.dto.MeResponse;
import com.twojo.member.dto.UpdateMeRequest;
import com.twojo.member.entity.Member;
import com.twojo.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보 조회·수정 (AU-03·07).
 *
 * <p>MemberQuery 경계를 쓰지 않는다 — email·phone은 경계에 없고, 밖에 낼 값도 아니다.
 * member 모듈 안이라 엔티티를 직접 읽는다 (11 §7.3). 회사명만 경계를 통한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeService {

    private final MemberRepository memberRepository;
    private final CompanyQuery companyQuery;

    public MeResponse get(AccessContext ctx) {
        return toResponse(findMe(ctx));
    }

    /**
     * 프로필 수정 (AU-07).
     *
     * <p>클래스에 걸린 readOnly를 이 메서드에서만 덮는다. 읽기 전용 트랜잭션은 변경을
     * 밀어 넣지 않아 수정이 조용히 사라지고, 응답에는 바뀐 값이 담겨 성공처럼 보인다.
     */
    @Transactional
    public MeResponse update(AccessContext ctx, UpdateMeRequest request) {
        Member member = findMe(ctx);
        member.updateProfile(request.name(), request.phone());
        return toResponse(member);
    }

    /** 조회 키가 요청이 아니라 토큰에서 온다 — 남의 id를 넣을 자리가 없다. */
    private Member findMe(AccessContext ctx) {
        return memberRepository.findById(ctx.memberId())
                .orElseThrow(() -> new MissingReferenceException("member", ctx.memberId()));
    }

    private MeResponse toResponse(Member member) {
        String companyName = companyQuery.get(member.getCompanyId()).name();

        return new MeResponse(member.getId(), member.getName(), member.getEmail(),
                member.getPhone(), member.getRole().name(), member.getCompanyId(), companyName);
    }
}
