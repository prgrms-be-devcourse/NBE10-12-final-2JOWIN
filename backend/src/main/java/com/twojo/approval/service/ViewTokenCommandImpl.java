package com.twojo.approval.service;

import com.twojo.approval.entity.QuoteViewToken;
import com.twojo.approval.repository.QuoteViewTokenRepository;
import com.twojo.approval.token.TokenGenerator;
import com.twojo.boundary.CustomerQuery;
import com.twojo.boundary.MailCommand;
import com.twojo.boundary.QuoteQuery;
import com.twojo.boundary.ViewTokenCommand;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link ViewTokenCommand} 구현.
 *
 * <p>{@code issue()} — 실구현. C의 발송 트랜잭션 안에서 동기 호출되어 열람 토큰 1건을 발급하고
 * (DB엔 해시만, 원문은 발급 시점 메모리와 안내 메일에만 — docs/14 §2-1), 안내 메일을
 * {@link MailCommand}로 예약한다. 재발송이면 기존 ACTIVE 링크를 {@code RESENT}로 만료시킨 뒤 새로
 * 발급한다 — 만료 UPDATE를 {@code flush()}로 먼저 내보내야 {@code uk_quote_view_token_active}
 * 부분 유니크에 걸리지 않는다. 근거는 "이 시점에 새 토큰을 아직 persist 하지 않았다"이지
 * "영속성 컨텍스트가 비었다"가 아니다(C 트랜잭션에 합류하므로 C의 더티 엔티티는 있을 수 있으나,
 * 그것들은 {@code quote_view_token} 행이 아니라 부분 유니크와 무관하다).
 *
 * <p>{@code expire()} — 실구현. 견적의 ACTIVE 링크 1건을 EXPIRED + 사유로 전이한다.
 * <b>멱등</b>: ACTIVE 링크가 없으면(이미 만료·응답 완료·미발급) 예외 없이 무동작한다 —
 * C의 회수·Deal 실패·만료 배치가 경쟁적으로 불러도 안전하다(최초 사유 보존).
 *
 * <p>{@code issue}/{@code expire}는 <b>호출자가 연 트랜잭션에 합류</b>한다 —
 * {@code issue}는 C의 발송 트랜잭션(Q-40), {@code expire}는 C의 회수·Deal 실패·만료 배치
 * 트랜잭션. {@code @Transactional(REQUIRES_NEW)}를 붙이지 않는다 — 토큰만 별도 커밋되면
 * 호출자 롤백 시 링크가 살아남는다(고아 링크). {@code issue}는 토큰 생성·메일 예약(동기) 실패가
 * 전부 예외로 전파되어 호출자 트랜잭션을 롤백시킨다 — 메일 <b>발송</b>(커밋 후 비동기) 실패만
 * 호출자 커밋을 되돌리지 않는다.
 *
 * <p>계약 {@code ViewTokenCommand.ExpiredReason} ↔ 엔티티 {@code QuoteViewToken.ExpiredReason}은
 * 별도 enum이다(엔티티는 boundary 무의존). 값 이름이 1:1이라 {@code valueOf(name())}으로 잇는다.
 * 재발송({@code RESENT})은 D 내부 전용이라 {@code issue()}에서 엔티티 enum을 직접 쓴다.
 */
@Service
class ViewTokenCommandImpl implements ViewTokenCommand {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    /** 안내 메일 링크가 가리키는 프론트 라우트 (docs/12-frontend-plan.md — /q/:token). */
    private static final String VIEW_PATH = "/q/";

    private final QuoteViewTokenRepository quoteViewTokenRepository;
    private final QuoteQuery quoteQuery;
    private final CustomerQuery customerQuery;
    private final TokenGenerator tokenGenerator;
    private final MailCommand mailCommand;
    private final String baseUrl;

    ViewTokenCommandImpl(QuoteViewTokenRepository quoteViewTokenRepository,
                         QuoteQuery quoteQuery,
                         CustomerQuery customerQuery,
                         TokenGenerator tokenGenerator,
                         MailCommand mailCommand,
                         @Value("${app.view-link.base-url}") String baseUrl) {
        this.quoteViewTokenRepository = quoteViewTokenRepository;
        this.quoteQuery = quoteQuery;
        this.customerQuery = customerQuery;
        this.tokenGenerator = tokenGenerator;
        this.mailCommand = mailCommand;
        // 설정 값에 슬래시가 붙어 와도 링크가 "//q/"가 되지 않게 정규화.
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    @Transactional
    public void issue(UUID quoteId, UUID recipientContactId) {
        QuoteQuery.PublicQuoteView view = quoteQuery.getPublicView(quoteId);   // 없으면 RESOURCE_NOT_FOUND 전파
        UUID companyId = Objects.requireNonNull(view.companyId(), "companyId"); // QUOTE_SENT는 계약상 필수

        // 수신 연락처 — 토큰 저장보다 앞: 실패해도 기존 ACTIVE 행에 락을 잡지 않는다.
        CustomerQuery.ContactSummary contact = customerQuery.getContact(recipientContactId);

        // 재발송 — 기존 ACTIVE를 RESENT로 닫고 flush로 UPDATE를 먼저 송출한다(새 토큰은 아직 persist 전).
        quoteViewTokenRepository.findActiveByQuoteId(quoteId).ifPresent(active -> {
            active.expire(QuoteViewToken.ExpiredReason.RESENT);
            quoteViewTokenRepository.flush();
        });

        Instant expiresAt = toExpiresAt(view.validUntil());
        String rawToken = tokenGenerator.generate();
        quoteViewTokenRepository.save(QuoteViewToken.issue(
                quoteId, recipientContactId, tokenGenerator.hash(rawToken), expiresAt));

        String subject = "[" + view.quoteNo() + "] 견적서 열람 안내";
        String body = renderBody(contact.name(), rawToken, view.validUntil());
        String recipientEmail = contact.email().trim().toLowerCase(Locale.ROOT);   // 멱등 키 일부 — 호출자 정규화 책임

        mailCommand.schedule(MailCommand.TemplateType.QUOTE_SENT, companyId, recipientEmail, quoteId, subject, body);
    }

    @Override
    @Transactional
    public void expire(UUID quoteId, ExpiredReason reason) {
        // ACTIVE 링크가 없으면(이미 만료·응답 완료·미발급) 무동작 — 멱등 계약: 경쟁 호출·중복 호출 안전.
        quoteViewTokenRepository.findActiveByQuoteId(quoteId)
                .ifPresent(token -> token.expire(QuoteViewToken.ExpiredReason.valueOf(reason.name())));
    }

    /** valid_until 당일 23:59:59 KST → Instant. LocalTime.MAX는 μs 반올림으로 write≠read라 쓰지 않는다. */
    private static Instant toExpiresAt(LocalDate validUntil) {
        return validUntil.atTime(23, 59, 59).atZone(KST).toInstant();
    }

    /**
     * 안내 메일 본문 — 평문, 링크는 독립된 한 줄(메일 클라이언트 자동 링크·raw 추출 단순).
     * {@code name}은 {@link CustomerQuery.ContactSummary}가 boundary record라 null 계약이 없다 —
     * 여기서 NPE가 나면 RESENT 만료를 이미 flush한 뒤라 폴백 메일도 못 나가고 트랜잭션이 통째로 롤백된다.
     */
    private String renderBody(String name, String rawToken, LocalDate validUntil) {
        String safeName = Objects.requireNonNullElse(name, "고객");
        return safeName + "님, 아래 링크에서 견적서를 확인하실 수 있습니다.\n\n"
                + baseUrl + VIEW_PATH + rawToken + "\n\n"
                + "유효기간: " + validUntil;
    }
}
