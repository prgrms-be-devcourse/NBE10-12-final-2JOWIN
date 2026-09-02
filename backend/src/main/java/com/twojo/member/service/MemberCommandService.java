package com.twojo.member.service;

import com.twojo.boundary.MemberCommand;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.member.entity.Member;
import com.twojo.member.repository.MemberRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MemberCommand 구현 — member 모듈이 밖에 내보이는 유일한 쓰기 경로 (11 §7.3).
 *
 * <p>조회의 MemberQueryService와 클래스를 나눈다. 그쪽은 readOnly = true라
 * 한 클래스에 두면 쓰기 메서드마다 트랜잭션 설정을 덮어써야 한다.
 *
 * <p><b>전파는 기본(REQUIRED)이다.</b> 호출자인 auth는 같은 트랜잭션에서 비밀번호 교체와
 * refresh_token 폐기를 함께 끝내야 한다 — 비밀번호만 바뀌고 세션이 남으면 전이표 §9가 깨진다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MemberCommandService implements MemberCommand {

    private final MemberRepository memberRepository;

    /**
     * 검증은 호출자(auth)가 이미 끝냈다 — 여기서는 저장만 한다.
     * 벌크 UPDATE가 아니라 영속 엔티티의 메서드를 부른다 (14 §1.2).
     */
    @Override
    public void changePassword(UUID memberId, String newPasswordHash, Instant changedAt) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        member.changePassword(newPasswordHash, changedAt);
    }
}
