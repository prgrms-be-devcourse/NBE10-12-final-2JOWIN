package com.twojo.onboarding.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

/**
 * 회사 상태 전이 (05 §2 · ON-08·10).
 *
 * <p>패키지가 com.twojo.onboarding.entity인 이유는 Company의 기본 생성자가 protected라서다.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CompanyTest {

    /**
     * 05 §2 "정지 해제 → 데이터는 그대로" — 그 '데이터'에 정지 사유는 들어가지 않는다.
     *
     * <p>05가 사유를 지우라고 적지는 않는다. 남기는 쪽을 고르면 {@code ACTIVE}인데 정지 사유가
     * 붙은 행이 생기고, 회사 목록(ON-12)이 상태와 사유 중 무엇을 표시할지 갈린다.
     * 그 판단이 코드에만 있어 여기서 고정한다.
     */
    @Test
    void 정지를_해제하면_정지_사유가_지워진다() {
        // given — 한빛오피스가 미납으로 정지된 상태다
        Company 한빛오피스 = Company.create(UUID.randomUUID(), "한빛오피스", "123-45-67890");
        한빛오피스.suspend("이용료 미납");

        // when — 플랫폼 관리자가 정지를 해제하면
        한빛오피스.reactivate();

        // then — 운영 중으로 돌아가고, 정지 사유가 남지 않는다
        assertThat(한빛오피스.isActive()).isTrue();
        assertThat(한빛오피스.getSuspendReason()).isNull();
    }
}
