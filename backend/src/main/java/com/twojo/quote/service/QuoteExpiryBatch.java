package com.twojo.quote.service;

import com.twojo.quote.entity.Quote;
import com.twojo.quote.repository.QuoteRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Q-37 — 유효기간이 지난 견적을 기간 만료(EXPIRED)로 닫는 스케줄 배치.
 *
 * <p>전이표 §6의 "발송됨·열람됨 → 기간 만료 · 주체 시스템" 행이다. <b>배치 소유는 C</b>이고,
 * 견적을 전이시킨 뒤 D의 {@code ViewTokenCommand.expire(TIME)}로 링크도 함께 닫는다 —
 * 견적 상태 변경의 주체는 항상 C라는 경계 합의의 귀결이다 (03 Q-37).
 *
 * <p><b>회사를 순회하지 않는다.</b> NT-05·06 알림 배치와 갈리는 지점이다 — 만료는 알림이
 * 아니라 상태 전이라 정지 회사(Q-27)도 그대로 닫혀야 한다. {@code CompanyQuery}를 거칠
 * 이유가 없어 한 번의 조회로 전 회사 대상을 얻는다.
 *
 * <p><b>트랜잭션 없음</b> — 조립만 한다. 쓰기는 {@link QuoteExpiryWorker}가 견적마다
 * {@code REQUIRES_NEW}로 연다. 견적 하나의 실패가 나머지를 막지 않도록
 * {@code try/catch}로 격리한다 ({@code Error}는 삼키지 않는다).
 *
 * <p><b>단일 인스턴스 전제</b> (docs/14 §1.2) — 스케일아웃 시 중복 실행 방지는 v1 밖이다.
 * 다만 이 배치는 <b>멱등</b>이라 중복 실행이 상태를 망가뜨리지 않는다:
 * {@code Quote.expire()}가 이미 닫힌 건에 {@code false}를 돌려주고,
 * {@code ViewTokenCommand.expire}도 멱등이다.
 *
 * <p><b>{@code Clock}을 주입받는다</b> — 만료 경계가 한국 날짜라(Q-17 · 채번과 같은 규약)
 * 테스트가 "오늘"을 고정할 수 있어야 한다.
 */
@Component
class QuoteExpiryBatch {

    private static final Logger log = LoggerFactory.getLogger(QuoteExpiryBatch.class);

    /** 만료 대상 — 응답 대기 중인 것만. 승인·반려·회수는 이미 종결이다 (전이표 §6) */
    private static final List<Quote.Status> AWAITING_RESPONSE =
            List.of(Quote.Status.SENT, Quote.Status.VIEWED);

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final QuoteRepository quoteRepository;
    private final QuoteExpiryWorker quoteExpiryWorker;

    QuoteExpiryBatch(QuoteRepository quoteRepository, QuoteExpiryWorker quoteExpiryWorker) {
        this.quoteRepository = quoteRepository;
        this.quoteExpiryWorker = quoteExpiryWorker;
    }

    @Scheduled(cron = "${quote.expiry.cron}", zone = "${quote.expiry.zone:Asia/Seoul}")
    public void run() {
        run(LocalDate.now(KST));
    }

    /** 대상 날짜를 받는 본체 — 테스트가 "오늘"을 고정한다 (스케줄러 없이 직접 부른다). */
    void run(LocalDate today) {
        List<UUID> targets = quoteRepository.findIdsExpiredBefore(AWAITING_RESPONSE, today);

        int expired = 0;
        int failed = 0;
        for (UUID quoteId : targets) {
            try {
                if (quoteExpiryWorker.expire(quoteId)) {
                    expired++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.warn("견적 만료 스킵 - quoteId={}", quoteId, e);
            }
        }

        log.info("기간 만료 배치 종료 - 대상 {}건 중 {}건 전이, {}건 실패", targets.size(), expired, failed);
    }
}
