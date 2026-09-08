package com.twojo.member.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.twojo.global.error.MissingReferenceException;
import com.twojo.member.repository.MemberRepository;
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

/**
 * 비밀번호 쓰기 경계 계약 (boundary/MemberCommand · 11 §7.3).
 *
 * <p>정상 경로의 필드 변화는 MemberTest가 맡는다 — Member의 생성자가 protected라
 * 이 패키지에서는 인스턴스를 만들 수 없다. 여기서는 없는 구성원 경로만 본다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MemberCommandServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T11:20:33Z");

    @Mock private MemberRepository memberRepository;

    private MemberCommandService memberCommandService;

    @BeforeEach
    void setUp() {
        memberCommandService = new MemberCommandService(memberRepository);
    }

    /**
     * 호출자(auth)가 토큰으로 대상을 특정하고 getCredential까지 통과한 뒤라,
     * 없다는 것은 명세가 다루는 실패가 아니라 <b>데이터 이상</b>이다 — 404가 아니라 500이다 (#165).
     *
     * <p>404로 답하면 SC-09의 "없거나 범위 밖"과 구별되지 않아, 무결성 이상이
     * 정상적인 조회 실패처럼 보이고 로그에도 남지 않는다.
     */
    @Test
    void 없는_구성원의_비밀번호는_바꿀_수_없다() {
        // given — 이미 삭제됐거나 애초에 없는 id가 넘어왔다
        UUID 없는_구성원 = UUID.randomUUID();
        given(memberRepository.findById(없는_구성원)).willReturn(Optional.empty());

        // when · then — 조용히 넘기지 않고 예외로 드러낸다
        assertThatThrownBy(() -> memberCommandService.changePassword(없는_구성원, "$2a$10$K7Lm", NOW))
                .isInstanceOf(MissingReferenceException.class)
                .hasMessageContaining("member")
                .hasMessageContaining(없는_구성원.toString());
    }
}
