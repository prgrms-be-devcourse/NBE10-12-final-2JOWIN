package com.twojo.quote.service;

import com.twojo.boundary.AuditActor;
import com.twojo.boundary.QuoteCommand;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.quote.QuoteApproved;
import com.twojo.quote.QuoteRejected;
import com.twojo.quote.QuoteViewed;
import com.twojo.quote.entity.Quote;
import com.twojo.quote.repository.QuoteRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link QuoteCommand} 구현 — 고객 응답에 따른 견적 상태 전이 (전이표 §6).
 *
 * <p>D의 열람·승인 API가 이 빈을 주입해 쓴다. <b>상태 변경 주체는 언제나 이 모듈</b>이고,
 * D는 엔드포인트와 링크 상태를 맡는다 (docs/11 §7.1).
 *
 * <p><b>회사 스코프를 걸지 않는다.</b> 고객 열람 링크는 로그인한 요청이 아니라 companyId를
 * 들고 오지 못하고, 대신 토큰이 이미 견적 하나를 특정한다 (11 §7.2).
 * {@code QuoteQueryImpl.getPublicView}와 같은 규약이다 —
 * <b>구성원 요청을 직접 받는 경로에서는 쓰지 않는다.</b>
 *
 * <p><b>{@code REQUIRES_NEW}를 붙이지 않는다</b> — 승인·반려 트랜잭션은 D가 열고 여기가
 * 참여한다 (경계 합의, 11 §5). 별도 커밋되면 D의 토큰만 RESPONDED로 소진됐는데 견적은
 * 그대로이거나 그 반대인 상태가 남는다.
 *
 * <p><b>⚠ 호출자의 트랜잭션이 {@code readOnly}면 변경이 조용히 사라진다.</b>
 * 특히 {@code markViewed}는 <b>조회 엔드포인트에서 불린다</b> — 열람 API가
 * {@code @Transactional(readOnly = true)}로 열려 있으면 여기가 그 트랜잭션에 합류하고,
 * Hibernate가 flush를 건너뛰어 <b>상태 변경이 예외 없이 버려진다.</b>
 * 여기에 {@code REQUIRES_NEW}를 붙여 막을 수도 있지만 그러면 경계 합의가 깨진다
 * (승인이 롤백돼도 견적만 바뀐 상태가 남는다). <b>열람 API의 트랜잭션을 쓰기 가능으로 열어야 한다.</b>
 *
 * <p><b>유효기간은 여기서 보지 않는다.</b> 만료된 견적에 승인이 오는 경로는 링크가 먼저 막는다 —
 * {@code quote_view_token.expires_at}이 {@code valid_until} 당일 23:59:59 KST로 발급 시점에
 * 고정되고(Q-17), 발송 뒤에는 {@code valid_until}을 바꿀 수 없다(DRAFT만 수정 가능, QT-16).
 * 둘이 어긋날 수 없어 D의 410 판정이 곧 유효기간 판정이다.
 *
 * <p><b>여기서 하지 않는 것</b> — 회사 정지 판정(SC-10·Q-27)과 링크 상태 차단(만료 410 ·
 * 응답 완료 409)은 D가 호출 전에 거른다. D가 이미 {@code CompanyQuery}와 토큰을 보고 있어
 * 판정이 두 곳으로 갈리지 않게 한 합의다 (PR #101 코멘트). 알림 발행(AP-12 · NT-03)도 D 몫이다.
 */
@Service
@RequiredArgsConstructor
public class QuoteCommandImpl implements QuoteCommand {

    private final QuoteRepository quoteRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 고객 첫 열람 (AP-02·07) — <b>멱등</b>. 이미 열람했거나 응답 완료면 아무 일도 하지 않는다.
     * 판정은 엔티티가 하고 여기서는 조회만 한다.
     */
    @Override
    @Transactional
    public void markViewed(UUID quoteId) {
        Quote quote = find(quoteId);
        Quote.Status before = quote.getStatus();
        Instant now = Instant.now();
        quote.markViewed(now);

        // 상태가 실제로 움직인 경우 = 첫 열람이다 (AP-07). 재열람·응답 후 열람은 엔티티가 조용히
        // 무동작하는데, 그때도 발행하면 고객이 링크를 새로고침할 때마다 타임라인이 늘어난다.
        if (quote.getStatus() != before) {
            eventPublisher.publishEvent(new QuoteViewed(quote.getCompanyId(), quote.getId(),
                    quote.getDealId(), quote.getQuoteNo(), AuditActor.customerLink(), now));
        }
    }

    /** 고객 승인 (AP-08·19) — 열람됨에서만. 그 밖의 상태는 {@code QUOTE_NOT_RESPONDABLE} */
    @Override
    @Transactional
    public void approve(UUID quoteId, Responder responder) {
        Quote quote = find(quoteId);
        Instant now = Instant.now();
        quote.approve(responder.name(), responder.title(), now);   // 열람됨이 아니면 여기서 막힌다

        eventPublisher.publishEvent(new QuoteApproved(quote.getCompanyId(), quote.getId(),
                quote.getDealId(), quote.getQuoteNo(), responder.name(),
                AuditActor.customerLink(), now));
    }

    /** 고객 반려 (AP-09·10·19) — 사유는 필수다. 08의 {@code @NotBlank}가 웹 계층에서 건다 */
    @Override
    @Transactional
    public void reject(UUID quoteId, String reason, Responder responder) {
        Quote quote = find(quoteId);
        Instant now = Instant.now();
        quote.reject(reason, responder.name(), responder.title(), now);   // 열람됨이 아니면 여기서 막힌다

        eventPublisher.publishEvent(new QuoteRejected(quote.getCompanyId(), quote.getId(),
                quote.getDealId(), quote.getQuoteNo(), responder.name(), reason,
                AuditActor.customerLink(), now));
    }

    /**
     * 주문 전환용 행 잠금 + 스냅샷 (OD-01·02·04) — 계약의 이유는 {@link QuoteCommand} javadoc에 있다.
     *
     * <p><b>{@code MANDATORY}다.</b> 여기서 건 락은 <b>호출자가 커밋할 때</b> 풀려야 의미가 있는데,
     * 트랜잭션 없이 불리면 이 메서드가 끝나는 순간 풀려 아무것도 막지 못한다.
     * 조용히 무력해지느니 그 자리에서 실패하는 편이 낫다 ({@code DocumentNumberService}, #72).
     *
     * <p>이 경로만 <b>회사 스코프를 건다</b> — 열람·승인 3종과 다르다. 저쪽은 토큰이 견적
     * 하나를 특정하는 고객 경로이고, 전환은 로그인한 구성원의 요청이라 SC-01이 걸린다.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ConversionSnapshot lockApprovedForConversion(UUID companyId, UUID quoteId) {
        Quote quote = quoteRepository.findForUpdate(quoteId, companyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        quote.requireApproved();   // 승인됨이 아니면 QUOTE_NOT_APPROVED (OD-02)

        return new ConversionSnapshot(quote.getId(), quote.getQuoteNo(), quote.getDealId(),
                quote.getSupplyAmount(), quote.getVatAmount(), quote.getTotalAmount(),
                quote.getItems().stream()
                        .map(item -> new ConversionSnapshot.Line(item.getName(), item.getUnit(),
                                item.getQuantity(), item.getUnitPrice(), item.getAmount()))
                        .toList());
    }

    /**
     * 없으면 {@code RESOURCE_NOT_FOUND} — 조용히 무동작하지 않는다.
     * 토큰이 견적 하나를 특정하는 자리라 없다는 것은 데이터 이상이고, 넘어가면 고객은
     * 승인했다고 믿는데 견적은 그대로인 상태가 남는다.
     */
    private Quote find(UUID quoteId) {
        return quoteRepository.findById(quoteId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
