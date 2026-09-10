package com.twojo.activity.service;

import com.twojo.activity.entity.AuditLog;
import com.twojo.boundary.AuditActor;
import com.twojo.deal.DealStageChanged;
import com.twojo.member.event.MemberDeactivated;
import com.twojo.order.OrderCreated;
import com.twojo.quote.QuoteApproved;
import com.twojo.quote.QuoteRejected;
import com.twojo.quote.QuoteViewed;
import com.twojo.quote.QuoteSent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * 도메인 이벤트를 {@code audit_log} 한 행으로 옮긴다 (AC-07).
 *
 * <p>커밋 후에 받는다 — 롤백된 작업이 기록으로 남으면 안 된다.
 * 적재는 {@link AuditLogWriter}가 별도 스레드에서 한다 (이유는 그 클래스 javadoc).
 *
 * <p>{@code entity_type}·{@code event_type}은 <b>여기서 만든다</b>. 발행자가 문자열로 실어 보내면
 * 오타가 그대로 저장되고, 값이 바뀔 때 발행 지점 전부를 찾아다녀야 한다 (#22).
 *
 * <p><b>payload 는 필드를 하나씩 골라 담는다</b> — 이벤트를 통째로 직렬화하지 않는다.
 * 비밀번호·토큰·해시가 들어갈 경로를 만들지 않기 위해서다 (06 규약, #22 5번). 발행자가 새 필드를
 * 늘려도 여기서 고르지 않으면 저장되지 않으므로, 거르는 코드 대신 <b>고르는 구조</b>로 막는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AuditLogListener {

    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(QuoteSent event) {
        write(AuditEventType.QUOTE_SENT.name(), () -> quoteRow(event.companyId(), event.quoteId(),
                AuditEventType.QUOTE_SENT, event.actor(), event.occurredAt(),
                quotePayload(event.dealId(), event.quoteNo(), null, null)));
    }

    /**
     * 단계 전이 — <b>값이 바뀌는 유일한 이벤트</b>라 {@code changes} 봉투가 붙는다 (#22 2번).
     *
     * <p>{@code lostReason}은 부가 필드다. 실패 사유는 전이 전에 존재할 수 없어 {@code before}가
     * 예외 없이 비고, {@code {before, after}} 형식의 절반이 늘 무의미해진다.
     * 실패가 아닌 전이에서는 <b>키 자체를 넣지 않는다</b> — null 이 든 키는 "값이 지워졌다"로 읽힌다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(DealStageChanged event) {
        write(AuditEventType.STAGE_MOVED.name(), () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("dealId", event.dealId());
            // Map.of 는 순서를 보장하지 않는다 — before·after 가 뒤집혀 저장되면 읽는 사람이 헷갈린다
            Map<String, Object> stage = new LinkedHashMap<>();
            stage.put("before", event.beforeStage());
            stage.put("after", event.afterStage());
            payload.put("changes", new LinkedHashMap<>(Map.of("stage", stage)));
            if (event.lostReason() != null) {
                payload.put("lostReason", event.lostReason());
            }
            return AuditLog.of(event.companyId(), "DEAL", event.dealId(),
                    AuditEventType.STAGE_MOVED.name(), event.actor(), event.occurredAt(),
                    objectMapper.writeValueAsString(payload));
        });
    }

    /** 첫 열람 (AP-02·07) — 재열람은 발행 쪽이 이미 걸러 여기 오지 않는다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(QuoteViewed event) {
        write(AuditEventType.QUOTE_VIEWED.name(), () -> quoteRow(event.companyId(), event.quoteId(),
                AuditEventType.QUOTE_VIEWED, event.actor(), event.occurredAt(),
                quotePayload(event.dealId(), event.quoteNo(), null, null)));
    }

    /**
     * 고객 승인 (AP-08·19) — {@code responderName}은 <b>검증되지 않은 자기 신고</b>다 (Q-44).
     * 값은 싣되 화면이 인증된 신원처럼 그리지 않아야 한다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(QuoteApproved event) {
        write(AuditEventType.QUOTE_APPROVED.name(), () -> quoteRow(event.companyId(), event.quoteId(),
                AuditEventType.QUOTE_APPROVED, event.actor(), event.occurredAt(),
                quotePayload(event.dealId(), event.quoteNo(), event.responderName(), null)));
    }

    /** 고객 반려 (AP-09·10·19) — 사유도 발생 전에 없던 값이라 부가 필드다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(QuoteRejected event) {
        write(AuditEventType.QUOTE_REJECTED.name(), () -> quoteRow(event.companyId(), event.quoteId(),
                AuditEventType.QUOTE_REJECTED, event.actor(), event.occurredAt(),
                quotePayload(event.dealId(), event.quoteNo(), event.responderName(), event.reason())));
    }

    /**
     * 주문 전환 (OD-01) — {@code dealId}는 {@code orders}에 컬럼이 없어 견적을 거쳐 얻은 값이다.
     * 타임라인 병합 키라 반드시 실린다 (AC-06).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(OrderCreated event) {
        write(AuditEventType.ORDER_CREATED.name(), () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("dealId", event.dealId());
            payload.put("orderNo", event.orderNo());
            payload.put("quoteId", event.quoteId());
            return AuditLog.of(event.companyId(), "ORDER", event.orderId(),
                    AuditEventType.ORDER_CREATED.name(), event.actor(), event.occurredAt(),
                    objectMapper.writeValueAsString(payload));
        });
    }

    /**
     * 구성원 비활성화 (MB-14) — 유일하게 C 가 아닌 발행자다.
     *
     * <p>이관 내역을 함께 남긴다 (Q-48). 구독자가 {@code deal}을 읽지 못해 여기 없으면 어느 딜이
     * 넘어갔는지 어디서도 복구되지 않는다. {@code transferToMemberId}는 넘어간 딜이 있을 때만 있고,
     * 없으면 <b>키 자체를 넣지 않는다</b> — null 이 든 키는 "값이 지워졌다"로 읽힌다.
     *
     * <p>이벤트 record 가 하위 패키지에 있다 — 발행 모듈마다 위치가 다르다 (#22 코멘트).
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(MemberDeactivated event) {
        write(AuditEventType.MEMBER_DEACTIVATED.name(), () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            if (event.transferToMemberId() != null) {
                payload.put("transferToMemberId", event.transferToMemberId());
            }
            payload.put("dealIds", event.dealIds());
            return AuditLog.of(event.companyId(), "MEMBER", event.memberId(),
                    AuditEventType.MEMBER_DEACTIVATED.name(), event.actor(), event.occurredAt(),
                    objectMapper.writeValueAsString(payload));
        });
    }

    private AuditLog quoteRow(UUID companyId, UUID quoteId, AuditEventType eventType,
                              AuditActor actor, Instant occurredAt, Map<String, Object> payload) {
        return AuditLog.of(companyId, "QUOTE", quoteId, eventType.name(), actor, occurredAt,
                objectMapper.writeValueAsString(payload));
    }

    /** null 인 값은 <b>키를 넣지 않는다</b> — null 이 든 키는 "값이 지워졌다"로 읽힌다. */
    private static Map<String, Object> quotePayload(UUID dealId, String quoteNo,
                                                    String responderName, String reason) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dealId", dealId);
        payload.put("quoteNo", quoteNo);
        if (responderName != null) {
            payload.put("responderName", responderName);
        }
        if (reason != null) {
            payload.put("reason", reason);
        }
        return payload;
    }

    /**
     * 제출이 거부되면 그 자리에서 직접 적재한다.
     *
     * <p>{@code writeAsync}가 {@code @Async}라 이 호출 지점에서 나올 수 있는 예외는 "제출 실패"뿐이다 —
     * 큐 포화({@code TaskRejectedException})든 셧다운 중 실행기 파괴({@code IllegalStateException})든
     * 결과는 같다. 적재를 시도조차 못 했다.
     *
     * <p><b>직접 적재까지 실패하면 삼킨다.</b> {@code AFTER_COMMIT}에서 새는 예외는 커밋된 요청을
     * 500으로 뒤집고, 같은 트랜잭션의 다른 리스너도 실행되지 않는다.
     */
    private void write(String eventType, Supplier<AuditLog> row) {
        AuditLog built;
        try {
            built = row.get();
        } catch (RuntimeException e) {
            // 조립도 try 안이다 — payload 직렬화와 AuditLog.of 의 null 검사가 여기서 터질 수 있고,
            // AFTER_COMMIT 에서 새면 커밋된 요청이 500 이 된다
            log.error("감사 로그를 만들지 못했다 — 이 사건은 기록되지 않는다. eventType={}, {}",
                    eventType, e.getClass().getName());
            return;
        }
        try {
            auditLogWriter.writeAsync(built);
        } catch (RuntimeException e) {
            log.warn("감사 로그 제출 실패 — 직접 적재한다. eventType={}, {}",
                    eventType, e.getClass().getName());
            writeNowQuietly(built);
        }
    }

    private void writeNowQuietly(AuditLog row) {
        try {
            auditLogWriter.writeNow(row);
        } catch (RuntimeException e) {
            log.error("감사 로그 적재 실패 — 이 사건은 기록되지 않는다. eventType={}, entityId={}, {}",
                    row.getEventType(), row.getEntityId(), e.getClass().getName());
        }
    }
}
