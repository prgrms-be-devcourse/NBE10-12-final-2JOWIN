package com.twojo.member.dto;

import com.twojo.member.entity.Member;
import java.util.UUID;

/**
 * 담당자 선택지 (08 §A · DL-04) — 이름과 id만.
 *
 * <p>연락처가 딸려가지 않는다. 선택지를 그리는 데 필요 없는 값이고, 영업 담당자도 부르는
 * 엔드포인트라 회사 전원의 연락처가 나갈 이유가 없다.
 */
public record MemberOptionResponse(UUID id, String name) {

    public static MemberOptionResponse of(Member member) {
        return new MemberOptionResponse(member.getId(), member.getName());
    }
}
