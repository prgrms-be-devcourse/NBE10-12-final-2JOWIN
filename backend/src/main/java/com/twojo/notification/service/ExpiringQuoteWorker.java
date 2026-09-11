package com.twojo.notification.service;

import com.twojo.boundary.CustomerQuery;
import com.twojo.boundary.MailCommand;
import com.twojo.boundary.MailCommand.TemplateType;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.ViewTokenQuery;
import com.twojo.notification.repository.EmailLogRepository;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * NT-06 — 유효기간이 임박한 견적 1건에 대해 고객사 담당자에게 안내 메일을 예약한다.
 * {@link ExpiringQuoteBatch}가 견적마다 호출한다.
 *
 * <p><b>{@code @Transactional(REQUIRES_NEW)}</b> — {@code @Scheduled} 메서드엔 트랜잭션이 없고
 * {@link MailCommand}는 {@code MANDATORY}라 트랜잭션이 필요하다. 견적 단위로 여는 이유는 NT-05
 * {@code RemindWorker}와 같다 — {@code MailCommand} 계약의 건별 트랜잭션 요구, 한 견적의 데이터 이상이
 * 다른 견적을 막지 않도록 하는 예외 격리.
 *
 * <p><b>메일 전용 — 인앱 레그가 없다.</b> 수신자는 계정 없는 고객사 담당자다(docs/03 §2.13). 따라서
 * NT-05가 쓰는 {@code notification} 존재 가드를 쓸 수 없고, <b>견적당 1회</b>는
 * {@code uk_email_log_dedup(QUOTE_EXPIRING, quoteId, email)} + 예약 전 사전 체크로 보장한다.
 *
 * <p>수신 연락처는 활성 열람 토큰에서 얻는다({@link ViewTokenQuery#recipientContactIdOf}) — 견적이
 * 어느 연락처로 나갔는지는 D의 {@code quote_view_token}에만 있다. 활성 토큰이 없으면 조용히 건너뛴다.
 * 이메일 정규화(trim·소문자)는 {@link MailCommand} 계약상 호출자 책임이라 여기서 한다.
 * 메일 본문에는 클릭 링크가 없다 — 원문 토큰을 저장하지 않아(docs/14 §7.3) 재구성 불가.
 */
@Component
@RequiredArgsConstructor
class ExpiringQuoteWorker {

    private static final Logger log = LoggerFactory.getLogger(ExpiringQuoteWorker.class);

    private final ViewTokenQuery viewTokenQuery;
    private final CustomerQuery customerQuery;
    private final EmailLogRepository emailLogRepository;
    private final MailCommand mailCommand;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void remind(QuoteQuery.QuoteSummary quote, String companyName) {
        UUID contactId = viewTokenQuery.recipientContactIdOf(quote.id()).orElse(null);
        if (contactId == null) {
            log.warn("임박 알림 수신 연락처 없음 - quoteId={}", quote.id());
            return;
        }

        String email = customerQuery.getContact(contactId).email().strip().toLowerCase(Locale.ROOT);
        if (emailLogRepository.existsByTemplateTypeAndRefIdAndRecipientEmail(
                TemplateType.QUOTE_EXPIRING, quote.id(), email)) {
            return;   // 견적당 1회 — 이미 예약됨
        }

        mailCommand.schedule(TemplateType.QUOTE_EXPIRING, quote.companyId(), email,
                quote.id(), mailSubject(companyName, quote.quoteNo()),
                mailBody(quote.quoteNo(), quote.validUntil()));
    }

    private static String mailSubject(String companyName, String quoteNo) {
        return "[" + companyName + "] 견적서 유효기간이 곧 만료됩니다 - " + quoteNo;
    }

    private static String mailBody(String quoteNo, LocalDate validUntil) {
        return "보내드린 견적서 [" + quoteNo + "]의 유효기간이 " + validUntil + "까지입니다.\n"
                + "기한 내 검토 부탁드리며, 견적 내용은 앞서 보내드린 안내 메일의 링크에서 확인하실 수 있습니다.";
    }
}
