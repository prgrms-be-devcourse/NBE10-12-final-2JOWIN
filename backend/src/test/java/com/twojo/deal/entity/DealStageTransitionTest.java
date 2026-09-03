package com.twojo.deal.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.twojo.deal.entity.Deal.Stage;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Deal 단계 전이 — <b>전이표 §5의 표를 그대로 옮긴다</b>.
 *
 * <p>표에 있는 전이가 되는지만 보는 게 아니라, <b>표에 없는 전이가 막히는지</b>를 함께 고정한다.
 * "표에 없는 전이는 전부 불가이며, 불가 전이 하나가 에러 코드 하나가 된다"가 전이표의 원칙이다.
 */
class DealStageTransitionTest {

    /**
     * 지정한 단계의 Deal — <b>실패(LOST)는 {@code lose()}를 거쳐 만든다.</b>
     *
     * <p>단계만 리플렉션으로 바꾸면 {@code lost_from_stage}가 빈 LOST가 생기는데, 그건
     * 실제로는 존재할 수 없는 상태다(실패 처리가 항상 채운다). 테스트가 현실에 없는 객체를
     * 만들면 거기서 나는 실패는 코드가 아니라 테스트의 문제다.
     */
    private static Deal dealAt(Stage stage) {
        Deal deal = Deal.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "한빛 사무가구 30석", 5_000_000L, null);
        if (stage == Stage.LOST) {
            deal.lose("예산 부족");   // NEGOTIATION 이전 단계는 LEAD — 복원 지점이 채워진다
            return deal;
        }
        ReflectionTestUtils.setField(deal, "stage", stage);
        return deal;
    }

    private static ErrorCode errorOf(Throwable e) {
        return ((BusinessException) e).getErrorCode();
    }

    @Nested
    @DisplayName("다음 단계 (DL-07)")
    class Advance {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"LEAD, CONSULT", "CONSULT, QUOTE", "QUOTE, NEGOTIATION"})
        @DisplayName("인접 한 단계씩만 이동한다")
        void 인접_이동(Stage from, Stage to) {
            Deal deal = dealAt(from);

            deal.advance();

            assertThat(deal.getStage()).isEqualTo(to);
        }

        @Test
        @DisplayName("협상에서는 성사로 갈 수 없다 — 승인 견적 없이 성사 불가를 코드로 강제 (DL-09)")
        void 협상에서_성사는_수동_불가() {
            Deal deal = dealAt(Stage.NEGOTIATION);

            assertThatThrownBy(deal::advance)
                    .isInstanceOf(BusinessException.class)
                    .extracting(DealStageTransitionTest::errorOf)
                    .isEqualTo(ErrorCode.DEAL_WON_REQUIRES_ORDER);

            assertThat(deal.getStage()).isEqualTo(Stage.NEGOTIATION);   // 실패해도 단계는 그대로
        }
    }

    @Nested
    @DisplayName("이전 단계 (DL-08)")
    class Revert {

        @ParameterizedTest(name = "{0} → {1}")
        @CsvSource({"CONSULT, LEAD", "QUOTE, CONSULT", "NEGOTIATION, QUOTE"})
        @DisplayName("인접 이전 단계로만 되돌린다")
        void 인접_복귀(Stage from, Stage to) {
            Deal deal = dealAt(from);

            deal.revert();

            assertThat(deal.getStage()).isEqualTo(to);
        }

        @Test
        @DisplayName("리드에서는 되돌릴 단계가 없다 — DEAL_NOT_OPEN이 아니다(리드는 진행 중이므로)")
        void 리드는_되돌릴_수_없다() {
            assertThatThrownBy(dealAt(Stage.LEAD)::revert)
                    .isInstanceOf(BusinessException.class)
                    .extracting(DealStageTransitionTest::errorOf)
                    .isEqualTo(ErrorCode.DEAL_NO_PREVIOUS_STAGE);
        }
    }

    @Nested
    @DisplayName("실패·재개 (DL-10~12)")
    class LoseAndReopen {

        @ParameterizedTest(name = "{0}에서 실패 처리")
        @EnumSource(value = Stage.class, names = {"LEAD", "CONSULT", "QUOTE", "NEGOTIATION"})
        @DisplayName("진행 중이면 어느 단계에서든 실패 처리할 수 있다")
        void 어느_단계에서든_실패(Stage from) {
            Deal deal = dealAt(from);

            deal.lose("예산 부족");

            assertThat(deal.getStage()).isEqualTo(Stage.LOST);
            assertThat(deal.getLostReason()).isEqualTo("예산 부족");
            assertThat(deal.getLostFromStage()).isEqualTo(from.name());   // 재개용 (DL-12)
        }

        @ParameterizedTest(name = "{0}에서 실패 → 재개하면 {0}으로")
        @EnumSource(value = Stage.class, names = {"LEAD", "CONSULT", "QUOTE", "NEGOTIATION"})
        @DisplayName("재개하면 실패 직전 단계로 돌아간다")
        void 재개는_직전_단계로(Stage from) {
            Deal deal = dealAt(from);
            deal.lose("예산 부족");

            deal.reopen();

            assertThat(deal.getStage()).isEqualTo(from);
            assertThat(deal.getLostReason()).isNull();       // 사유·복원 지점은 함께 지운다
            assertThat(deal.getLostFromStage()).isNull();
        }

        @Test
        @DisplayName("진행 중인 Deal은 재개할 수 없다 — 문구가 정반대라 DEAL_NOT_OPEN을 쓰지 않는다")
        void 진행_중_재개_불가() {
            assertThatThrownBy(dealAt(Stage.QUOTE)::reopen)
                    .isInstanceOf(BusinessException.class)
                    .extracting(DealStageTransitionTest::errorOf)
                    .isEqualTo(ErrorCode.DEAL_NOT_LOST);
        }
    }

    @Nested
    @DisplayName("종결 Deal은 전이가 막힌다")
    class Closed {

        @Test
        @DisplayName("성사 Deal은 어떤 전이도 할 수 없다 — DEAL_ALREADY_WON")
        void 성사는_전부_차단() {
            assertThatThrownBy(dealAt(Stage.WON)::advance)
                    .extracting(DealStageTransitionTest::errorOf).isEqualTo(ErrorCode.DEAL_ALREADY_WON);
            assertThatThrownBy(dealAt(Stage.WON)::revert)
                    .extracting(DealStageTransitionTest::errorOf).isEqualTo(ErrorCode.DEAL_ALREADY_WON);
            assertThatThrownBy(() -> dealAt(Stage.WON).lose("사유"))
                    .extracting(DealStageTransitionTest::errorOf).isEqualTo(ErrorCode.DEAL_ALREADY_WON);
            assertThatThrownBy(dealAt(Stage.WON)::reopen)
                    .extracting(DealStageTransitionTest::errorOf).isEqualTo(ErrorCode.DEAL_ALREADY_WON);
        }

        @Test
        @DisplayName("실패 Deal에서 나가는 전이는 재개뿐이다 — 나머지는 DEAL_NOT_OPEN")
        void 실패는_재개만_열린다() {
            assertThatThrownBy(dealAt(Stage.LOST)::advance)
                    .extracting(DealStageTransitionTest::errorOf).isEqualTo(ErrorCode.DEAL_NOT_OPEN);
            assertThatThrownBy(dealAt(Stage.LOST)::revert)
                    .extracting(DealStageTransitionTest::errorOf).isEqualTo(ErrorCode.DEAL_NOT_OPEN);
            assertThatThrownBy(() -> dealAt(Stage.LOST).lose("사유"))
                    .extracting(DealStageTransitionTest::errorOf).isEqualTo(ErrorCode.DEAL_NOT_OPEN);
        }
    }

    /**
     * DL-09("승인된 견적 없이 성사될 수 없다")를 전이 메서드 전체에 대해 고정한다.
     * 출발점에서 WON은 제외한다 — 이미 성사인 것은 "도달"이 아니다.
     */
    @ParameterizedTest(name = "{0}에서는 어떤 전이로도 성사에 닿지 않는다")
    @EnumSource(value = Stage.class, names = {"LEAD", "CONSULT", "QUOTE", "NEGOTIATION", "LOST"})
    @DisplayName("성사는 전이 메서드로 도달할 수 없다 — 주문 전환만이 만든다 (DL-09)")
    void 성사는_전이로_도달_불가(Stage from) {
        for (java.util.function.Consumer<Deal> transition :
                java.util.List.<java.util.function.Consumer<Deal>>of(
                        Deal::advance, Deal::revert, Deal::reopen, deal -> deal.lose("사유"))) {
            Deal deal = dealAt(from);
            try {
                transition.accept(deal);
            } catch (BusinessException ignored) {
                // 차단되는 것이 정상 — 여기서 보는 것은 "차단되든 성공하든 WON이 되지 않는다"이다
            }
            assertThat(deal.getStage()).as("%s에서 전이 후", from).isNotEqualTo(Stage.WON);
        }
    }
}
