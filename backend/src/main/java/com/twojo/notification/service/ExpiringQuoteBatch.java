package com.twojo.notification.service;

import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.CompanyQuery.CompanySummary;
import com.twojo.boundary.QuoteQuery;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * NT-06 — 유효기간 임박 알림 스케줄 배치. {@code valid_until}이 오늘 ~ N일 구간에 든 미응답 견적을
 * 찾아 회사별로 묶고 {@link ExpiringQuoteWorker}에 견적 단위로 넘긴다.
 * (docs/03-requirements.md §2.13, Q-17·26·27)
 *
 * <p><b>회사 그룹핑 + 2단 격리</b> — {@code findExpiringUntil}은 전 회사를 평평하게 돌려준다.
 * {@code groupingBy(companyId)}로 묶어 회사당 {@code companyQuery.get}을 1회만 부른다 — 플랫 루프면
 * 한 회사의 조회 실패가 그 회사 견적 수만큼 재실패한다. 회사 루프와 견적 루프를 각각 {@code try/catch}로
 * 감싸 한 회사/견적의 실패가 나머지를 막지 않게 한다. {@code Error}(OOM 등)는 삼키지 않는다.
 *
 * <p><b>트랜잭션 없음</b> — 조립만 한다. 쓰기는 {@link ExpiringQuoteWorker}가 견적마다 {@code REQUIRES_NEW}로
 * 연다({@code MailCommand}가 {@code MANDATORY}인 이유는 그쪽 javadoc).
 *
 * <p><b>단일 인스턴스 전제</b> (docs/14-tech-stack.md §1.2) — 스케일아웃 시 중복 실행 방지는 v1 밖이다.
 * 발송 중복은 {@code email_log} UNIQUE {@code (QUOTE_EXPIRING, ref_id, recipient_email)}가 막는다.
 */
@Component
class ExpiringQuoteBatch {

    private static final Logger log = LoggerFactory.getLogger(ExpiringQuoteBatch.class);

    private final CompanyQuery companyQuery;
    private final QuoteQuery quoteQuery;
    private final ExpiringQuoteWorker worker;

    /** {@code valid_until}이 오늘 ~ 이 일수 뒤 구간에 들면 임박 대상. */
    private final int beforeDays;
    /** "오늘"을 계산하는 시간대 — {@code @Scheduled} cron zone과 같은 값이어야 한다. */
    private final ZoneId zone;

    /** @RequiredArgsConstructor를 쓰지 않는 이유는 @Value 두 개 — @Value는 생성자 파라미터에 붙는다. */
    ExpiringQuoteBatch(CompanyQuery companyQuery,
                       QuoteQuery quoteQuery,
                       ExpiringQuoteWorker worker,
                       @Value("${notification.expiring.before-days}") int beforeDays,
                       @Value("${notification.expiring.zone:Asia/Seoul}") String zone) {
        this.companyQuery = companyQuery;
        this.quoteQuery = quoteQuery;
        this.worker = worker;
        this.beforeDays = beforeDays;
        this.zone = ZoneId.of(zone);
    }

    @Scheduled(cron = "${notification.expiring.cron}", zone = "${notification.expiring.zone:Asia/Seoul}")
    public void run() {
        LocalDate today = LocalDate.now(zone);
        List<QuoteQuery.QuoteSummary> quotes =
                quoteQuery.findExpiringUntil(today, today.plusDays(beforeDays));
        Map<UUID, List<QuoteQuery.QuoteSummary>> byCompany = quotes.stream()
                .collect(Collectors.groupingBy(QuoteQuery.QuoteSummary::companyId));
        int failedCompanies = 0;

        for (Map.Entry<UUID, List<QuoteQuery.QuoteSummary>> entry : byCompany.entrySet()) {
            UUID companyId = entry.getKey();
            try {
                CompanySummary company = companyQuery.get(companyId);
                if (!company.active()) {
                    continue;   // Q-27 — 정지 회사는 배치 알림 억제
                }
                for (QuoteQuery.QuoteSummary quote : entry.getValue()) {
                    try {
                        worker.remind(quote, company.name());
                    } catch (RuntimeException e) {
                        log.warn("견적 임박 알림 스킵 - quoteId={}", quote.id(), e);
                    }
                }
            } catch (RuntimeException e) {
                failedCompanies++;
                log.warn("회사 임박 알림 스킵 - companyId={}", companyId, e);
            }
        }

        log.info("NT-06 임박 알림 배치 종료 - 회사 {}건 중 {}건 실패, 견적 {}건",
                byCompany.size(), failedCompanies, quotes.size());
    }
}
