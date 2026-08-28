package com.twojo.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.twojo.auth.entity.ActorType;
import com.twojo.auth.entity.LoginAttempt;
import com.twojo.auth.repository.LoginAttemptRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 잠금 판정 (AU-09 · 06 "DB로 못 막는 것").
 * 10분은 잠금 지속 시간이지 판정 윈도우가 아니다 — 이 구분이 깨지면 8분 간격 공격을 못 막는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LoginAttemptServiceTest {

    private static final String EMAIL = "jihun@hanbit.co.kr";
    private static final Instant NOW = Instant.parse("2026-08-28T05:00:00Z");

    @Mock
    private LoginAttemptRepository loginAttemptRepository;

    @InjectMocks
    private LoginAttemptService loginAttemptService;

    @Test
    void 기록이_5건_미만이면_잠기지_않는다() {
        given(loginAttemptRepository.findByEmailAndActorTypeOrderByAttemptedAtDesc(any(), any(), any()))
                .willReturn(실패들(4, NOW.minus(Duration.ofMinutes(1))));

        boolean locked = loginAttemptService.isLocked(EMAIL, ActorType.MEMBER, NOW);

        assertThat(locked).isFalse();
    }

    @Test
    void 최근_5건이_전부_실패이고_마지막_실패가_10분_이내면_잠긴다() {
        given(loginAttemptRepository.findByEmailAndActorTypeOrderByAttemptedAtDesc(any(), any(), any()))
                .willReturn(실패들(5, NOW.minus(Duration.ofMinutes(1))));

        boolean locked = loginAttemptService.isLocked(EMAIL, ActorType.MEMBER, NOW);

        assertThat(locked).isTrue();
    }

    @Test
    void 최근_5건에_성공이_섞여_있으면_잠기지_않는다() {
        // 세 번째가 성공이라 마지막 성공 이후 연속 실패는 2회다
        List<LoginAttempt> recent = 실패들(5, NOW.minus(Duration.ofMinutes(1)));
        recent.set(2, LoginAttempt.success(EMAIL, ActorType.MEMBER, null, null,
                NOW.minus(Duration.ofMinutes(5))));
        given(loginAttemptRepository.findByEmailAndActorTypeOrderByAttemptedAtDesc(any(), any(), any()))
                .willReturn(recent);

        boolean locked = loginAttemptService.isLocked(EMAIL, ActorType.MEMBER, NOW);

        assertThat(locked).isFalse();
    }

    @Test
    void 마지막_실패로부터_10분이_지나면_잠금이_풀린다() {
        given(loginAttemptRepository.findByEmailAndActorTypeOrderByAttemptedAtDesc(any(), any(), any()))
                .willReturn(실패들(5, NOW.minus(Duration.ofMinutes(11))));

        boolean locked = loginAttemptService.isLocked(EMAIL, ActorType.MEMBER, NOW);

        // 배치가 푸는 것이 아니라 시각 비교로 저절로 풀린다
        assertThat(locked).isFalse();
    }

    @Test
    void 마지막_실패가_정확히_10분_전이면_잠금이_풀린다() {
        given(loginAttemptRepository.findByEmailAndActorTypeOrderByAttemptedAtDesc(any(), any(), any()))
                .willReturn(실패들(5, NOW.minus(Duration.ofMinutes(10))));

        boolean locked = loginAttemptService.isLocked(EMAIL, ActorType.MEMBER, NOW);

        // isAfter 는 배타적이다 — 정확히 10분은 "이내"에 들지 않는다
        assertThat(locked).isFalse();
    }

    /** 최신순 실패 목록. 첫 원소가 가장 최근이고 뒤로 갈수록 1분씩 과거다. */
    private List<LoginAttempt> 실패들(int count, Instant latest) {
        return IntStream.range(0, count)
                .mapToObj(i -> LoginAttempt.failure(EMAIL, ActorType.MEMBER, null, null,
                        latest.minus(Duration.ofMinutes(i))))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
