package com.twojo.notification.service;

import com.twojo.boundary.MailCommand;
import com.twojo.boundary.MailCommand.TemplateType;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.NotificationCommand;
import com.twojo.boundary.NotificationCommand.NotificationType;
import com.twojo.boundary.NotificationCommand.RefType;
import com.twojo.boundary.NotificationSettingQuery;
import com.twojo.boundary.NotificationSettingType;
import com.twojo.boundary.QuoteQuery;
import com.twojo.notification.entity.Notification;
import com.twojo.notification.repository.NotificationRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * NT-05 — 응답 없는 견적 1건을 담당 구성원에게 리마인드한다. {@link RemindNoResponseBatch}가 견적마다 호출한다.
 *
 * <p><b>{@code @Transactional(REQUIRES_NEW)}</b> — {@code @Scheduled} 메서드엔 트랜잭션이 없고
 * {@link NotificationCommand}·{@link MailCommand}는 {@code MANDATORY}라 트랜잭션이 필요하다. 견적 단위로 여는 이유:
 * (1) {@code MailCommand} 계약이 배치의 <b>건별 트랜잭션</b>을 요구한다 — 한 트랜잭션에 여러 건 예약 중
 * {@code uk_email_log_dedup} 충돌 1건이 전체를 롤백시킨다. (2) 한 견적의 인앱 + 메일 예약은 원자적이어야
 * 정합이 유지된다 — 커밋이 실패해 롤백되면 {@code notification} 행도 사라져 다음 주기가 깨끗이 재시도한다.
 * (3) 한 견적의 데이터 이상이 다른 견적을 막지 않는다 — 오케스트레이터가 견적 단위로 예외를 격리한다.
 *
 * <p><b>멱등 — 견적당 1회.</b> 진입 시 {@code (companyId, REMIND_NO_RESPONSE, refId=quoteId)} 알림이
 * 이미 있으면 견적 전체를 건너뛴다. 이 가드가 인앱·메일 두 레그를 함께 막으므로 정상 흐름에서는
 * {@code uk_email_log_dedup} 충돌 자체가 생기지 않는다(그 UNIQUE는 백스톱). 담당자가 재배정돼도
 * 재알림하지 않는다(11 §5).
 *
 * <p>메일은 NT-07 수신 설정이 ON({@code REMIND_NO_RESPONSE})인 수신자에게만 예약한다 — 인앱은 항상.
 * 수신 이메일 정규화(trim·소문자)는 {@code MailCommand} 계약상 호출자 책임이라 여기서 한다.
 * 메일 본문에는 고객 열람 링크·토큰을 넣지 않는다 — 사내 구성원 대상이다.
 */
@Component
@RequiredArgsConstructor
class RemindWorker {

    private static final Logger log = LoggerFactory.getLogger(RemindWorker.class);

    private final NotificationRepository notificationRepository;
    private final NotificationCommand notificationCommand;
    private final MailCommand mailCommand;
    private final NotificationSettingQuery notificationSettingQuery;
    private final MemberQuery memberQuery;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void remind(UUID companyId, QuoteQuery.QuoteSummary quote) {
        if (notificationRepository.existsByCompanyIdAndTypeAndRefId(
                companyId, Notification.Type.REMIND_NO_RESPONSE, quote.id())) {
            return;   // 견적당 1회 — 인앱·메일 두 레그 함께 스킵
        }

        List<UUID> recipients = notificationCommand.dealRecipients(
                NotificationType.REMIND_NO_RESPONSE, companyId, quote.dealId());
        if (recipients.isEmpty()) {
            log.warn("리마인드 수신자 없음 - quoteId={}", quote.id());
            return;
        }

        String inAppMessage = inAppMessage(quote.quoteNo());
        for (UUID memberId : recipients) {
            notificationCommand.notify(NotificationType.REMIND_NO_RESPONSE, companyId, memberId,
                    inAppMessage, RefType.QUOTE, quote.id());
            if (mailEnabled(memberId)) {
                String email = memberQuery.getContact(memberId).email().strip().toLowerCase(Locale.ROOT);
                mailCommand.schedule(TemplateType.QUOTE_REMIND, companyId, email,
                        quote.id(), mailSubject(quote.quoteNo()), mailBody(quote.quoteNo()));
            }
        }
    }

    /** NT-07 기본값은 ON — 저장 행이 없는 type은 {@code settingsOf}가 채우지만 방어적으로 기본 TRUE. */
    private boolean mailEnabled(UUID memberId) {
        return notificationSettingQuery.settingsOf(memberId)
                .getOrDefault(NotificationSettingType.REMIND_NO_RESPONSE, Boolean.TRUE);
    }

    private static String inAppMessage(String quoteNo) {
        return "[" + quoteNo + "] 발송한 견적서에 아직 고객 응답이 없습니다. 확인 후 필요하면 다시 안내해 주세요.";
    }

    private static String mailSubject(String quoteNo) {
        return "[2JO] 견적서 응답 대기 알림 - " + quoteNo;
    }

    private static String mailBody(String quoteNo) {
        return "발송한 견적서 [" + quoteNo + "]에 아직 고객 응답이 없습니다.\n"
                + "2JO에서 견적 상세를 확인하고 필요하면 고객에게 다시 안내해 주세요.";
    }
}
