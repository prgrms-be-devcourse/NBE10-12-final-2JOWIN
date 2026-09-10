package com.twojo.quote.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

import com.twojo.boundary.ViewTokenCommand;
import com.twojo.quote.entity.Quote;
import com.twojo.quote.repository.QuoteRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 기간 만료 배치·워커 (Q-37) — 전이표 §6.
 *
 * <p>여기서 고정하는 것은 셋이다: <b>대상 선정</b>(응답 대기 + 어제까지),
 * <b>견적과 링크의 원자성</b>(견적만 닫히고 링크가 살아 있으면 고객이 계속 열람한다),
 * <b>격리</b>(한 견적의 실패가 나머지를 막지 않는다).
 */
@ExtendWith(MockitoExtension.class)
class QuoteExpiryBatchTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 11);

    @Mock private QuoteRepository quoteRepository;
    @Mock private QuoteExpiryWorker quoteExpiryWorker;
    @Mock private ViewTokenCommand viewTokenCommand;

    @InjectMocks private QuoteExpiryBatch batch;
    @InjectMocks private QuoteExpiryWorker worker;

    private static Quote quoteAt(Quote.Status status) {
        Quote quote = Quote.draft(UUID.randomUUID(), UUID.randomUUID(), "Q-2609-001",
                LocalDate.of(2026, 9, 10));
        ReflectionTestUtils.setField(quote, "status", status);
        return quote;
    }

    @Nested
    @DisplayName("배치 — 대상 선정과 격리")
    class Batch {

        /**
         * <b>회사를 순회하지 않는다.</b> 만료는 알림이 아니라 상태 전이라 정지 회사(Q-27)도
         * 닫혀야 한다 — NT-05·06과 갈리는 지점이라 조회 인자로 고정한다.
         */
        @Test
        @DisplayName("응답 대기 상태만, 오늘 이전 유효기간만 조회한다 — 회사는 나누지 않는다")
        void 대상_선정() {
            given(quoteRepository.findIdsExpiredBefore(any(), any())).willReturn(List.of());

            batch.run(TODAY);

            ArgumentCaptor<java.util.Collection<Quote.Status>> statuses =
                    ArgumentCaptor.forClass(java.util.Collection.class);
            then(quoteRepository).should().findIdsExpiredBefore(statuses.capture(), eq(TODAY));
            assertThat(statuses.getValue())
                    .containsExactlyInAnyOrder(Quote.Status.SENT, Quote.Status.VIEWED);
        }

        @Test
        @DisplayName("대상마다 워커를 부른다 — 견적 단위로 트랜잭션이 갈린다")
        void 견적_단위_위임() {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            given(quoteRepository.findIdsExpiredBefore(any(), any())).willReturn(List.of(first, second));
            given(quoteExpiryWorker.expire(any())).willReturn(true);

            batch.run(TODAY);

            then(quoteExpiryWorker).should().expire(first);
            then(quoteExpiryWorker).should().expire(second);
        }

        /**
         * 한 견적의 데이터 이상이 그날 배치 전체를 죽이면, 뒤에 선 견적들은 다음 날까지
         * 만료되지 않은 채 고객에게 열려 있다.
         */
        @Test
        @DisplayName("한 견적이 실패해도 나머지는 계속 돈다")
        void 실패_격리() {
            UUID broken = UUID.randomUUID();
            UUID healthy = UUID.randomUUID();
            given(quoteRepository.findIdsExpiredBefore(any(), any())).willReturn(List.of(broken, healthy));
            willThrow(new IllegalStateException("깨진 견적")).given(quoteExpiryWorker).expire(broken);
            given(quoteExpiryWorker.expire(healthy)).willReturn(true);

            batch.run(TODAY);   // 예외가 밖으로 나오지 않는다

            then(quoteExpiryWorker).should().expire(healthy);
        }

        @Test
        @DisplayName("대상이 없으면 워커를 부르지 않는다")
        void 대상_없음() {
            given(quoteRepository.findIdsExpiredBefore(any(), any())).willReturn(List.of());

            batch.run(TODAY);

            then(quoteExpiryWorker).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("워커 — 견적과 링크의 원자성")
    class Worker {

        @Test
        @DisplayName("견적을 닫고 링크도 TIME으로 닫는다 — 둘이 갈리면 만료된 견적이 계속 열람된다")
        void 견적과_링크를_함께_닫는다() {
            UUID quoteId = UUID.randomUUID();
            Quote target = quoteAt(Quote.Status.SENT);
            given(quoteRepository.findById(quoteId)).willReturn(Optional.of(target));

            assertThat(worker.expire(quoteId)).isTrue();

            assertThat(target.getStatus()).isEqualTo(Quote.Status.EXPIRED);
            then(viewTokenCommand).should().expire(quoteId, ViewTokenCommand.ExpiredReason.TIME);
        }

        /**
         * 조회와 이 트랜잭션 사이에 담당자가 회수하거나 고객이 응답했을 수 있다.
         * 그 전이들은 <b>각자 링크를 이미 처리했으므로</b>(회수는 WITHDRAWN, 응답은 RESPONDED)
         * 여기서 TIME을 덮어쓰면 만료 사유가 거짓이 된다.
         */
        @Test
        @DisplayName("이미 닫힌 견적이면 링크를 건드리지 않는다 — 만료 사유가 거짓이 되면 안 된다")
        void 이미_닫힌_견적() {
            UUID quoteId = UUID.randomUUID();
            given(quoteRepository.findById(quoteId)).willReturn(Optional.of(quoteAt(Quote.Status.APPROVED)));

            assertThat(worker.expire(quoteId)).isFalse();

            then(viewTokenCommand).should(never()).expire(any(), any());
        }

        @Test
        @DisplayName("견적이 사라졌으면 조용히 넘어간다 — 배치라 오류가 아니다")
        void 견적_없음() {
            UUID quoteId = UUID.randomUUID();
            given(quoteRepository.findById(quoteId)).willReturn(Optional.empty());

            assertThat(worker.expire(quoteId)).isFalse();

            then(viewTokenCommand).shouldHaveNoInteractions();
        }
    }
}
