package com.twojo.member.service;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.CompanyQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.member.dto.MeResponse;
import com.twojo.member.entity.Member;
import com.twojo.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 정보 조회 (AU-03·07).
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
        // 조회 키가 요청이 아니라 토큰에서 온다 — 남의 id를 넣을 자리가 없다
        Member member = memberRepository.findById(ctx.memberId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        String companyName = companyQuery.get(member.getCompanyId()).name();

        return new MeResponse(member.getId(), member.getName(), member.getEmail(),
                member.getPhone(), member.getRole().name(), member.getCompanyId(), companyName);
    }
}
