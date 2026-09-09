package com.twojo.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.twojo.BackendApplication;
import com.twojo.member.event.MemberDeactivated;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * 이벤트 패키지 노출 — 리스너가 붙기 전까지 이 회귀를 잡는 것이 여기뿐이다.
 *
 * <p>{@code ModularityTests}는 <b>실제 참조가 있어야</b> 위반을 본다. 지금은 이벤트를 구독하는
 * 모듈이 없어, 노출 선언을 지워도 그쪽은 그대로 통과한다. 그 상태로 머지되면 리스너를 붙이는
 * 사람이 남의 모듈에서 원인을 찾게 된다.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MemberModuleExposureTest {

    @Test
    void 구성원_이벤트는_모듈_밖에서_참조할_수_있다() {
        ApplicationModules modules = ApplicationModules.of(BackendApplication.class);

        assertThat(modules.getModuleByName("member")).hasValueSatisfying(member ->
                assertThat(member.getNamedInterfaces().stream()
                        .anyMatch(exposed -> exposed.contains(MemberDeactivated.class)))
                        .as("member.event가 노출되지 않으면 리스너 쪽 컴파일이 모듈 검증에 막힌다")
                        .isTrue());
    }
}
