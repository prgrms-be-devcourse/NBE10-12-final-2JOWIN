package com.twojo.notification.service;

import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.QuoteQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * NT-05 — 무응답 견적 리마인드 스케줄 배치. 활성 회사를 돌며 발송 후 임계일수가 지나도록 응답 없는 견적을
 * 찾아 {@link RemindWorker}에 견적 단위로 넘긴다. (docs/03-requirements.md §2.13, Q-26·27)
 *
 * <p><b>2단 격리</b> — {@code companyQuery.get}·{@code findAwaitingResponse}의 일시적 예외가 그날 배치
 * 전체를 죽이고 남은 회사를 스킵하면 안 된다. 회사 루프와 견적 루프를 각각 {@code try/catch}로 감싸,
 * 한 회사/견적의 실패가 나머지를 막지 않게 한다. {@code Error}(OOM 등)는 삼키지 않는다.
 *
 * <p><b>트랜잭션 없음</b> — 조립만 한다. 쓰기는 {@link RemindWorker}가 견적마다 {@code REQUIRES_NEW}로
 * 연다({@code NotificationCommand}·{@code MailCommand}가 {@code MANDATORY}인 이유는 그쪽 javadoc).
 *
 * <p><b>단일 인스턴스 전제</b> (docs/14-tech-stack.md §1.2) — 스케일아웃 시 중복 실행 방지(ShedLock 등)는
 * v1 밖이다. 발송 중복은 {@code email_log} UNIQUE가, 인앱 중복은 {@code RemindWorker}의 가드가 막는다.
 */
@Component
class RemindNoResponseBatch {

    private static final Logger log = LoggerFactory.getLogger(RemindNoResponseBatch.class);

    private final CompanyQuery companyQuery;
    private final QuoteQuery quoteQuery;
    private final RemindWorker remindWorker;

    /** 발송 후 이 일수가 지나도록 무응답이면 리마인드 대상 — {@code sentAt} 기준. */
    private final int afterDays;

    /** @RequiredArgsConstructor를 쓰지 않는 이유는 afterDays 하나 — @Value는 생성자 파라미터에 붙는다. */
    RemindNoResponseBatch(CompanyQuery companyQuery,
                          QuoteQuery quoteQuery,
                          RemindWorker remindWorker,
                          @Value("${notification.remind.after-days}") int afterDays) {
        this.companyQuery = companyQuery;
        this.quoteQuery = quoteQuery;
        this.remindWorker = remindWorker;
        this.afterDays = afterDays;
    }

    @Scheduled(cron = "${notification.remind.cron}", zone = "${notification.remind.zone:Asia/Seoul}")
    public void run() {
        List<UUID> companyIds = companyQuery.findActiveIds();
        Instant threshold = Instant.now().minus(Duration.ofDays(afterDays));
        int failedCompanies = 0;

        for (UUID companyId : companyIds) {
            try {
                if (!companyQuery.get(companyId).active()) {
                    continue;   // Q-27 — findActiveIds 시점 이후 정지된 회사
                }
                for (QuoteQuery.QuoteSummary quote : quoteQuery.findAwaitingResponse(companyId)) {
                    if (quote.sentAt() == null || quote.sentAt().isAfter(threshold)) {
                        continue;   // 임계일수 미달
                    }
                    try {
                        remindWorker.remind(companyId, quote);
                    } catch (RuntimeException e) {
                        log.warn("견적 리마인드 스킵 - quoteId={}", quote.id(), e);
                    }
                }
            } catch (RuntimeException e) {
                failedCompanies++;
                log.warn("회사 리마인드 스킵 - companyId={}", companyId, e);
            }
        }

        log.info("NT-05 리마인드 배치 종료 - 회사 {}건 중 {}건 실패", companyIds.size(), failedCompanies);
    }
}
