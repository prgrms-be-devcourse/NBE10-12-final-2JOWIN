package com.twojo.activity.service;

import com.twojo.activity.entity.AuditLog;
import com.twojo.activity.repository.AuditLogRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

/**
 * 도메인 이벤트를 {@code audit_log} 한 행으로 옮긴다 (AC-07).
 *
 * <p>커밋 후에 받는다 — 롤백된 작업이 기록으로 남으면 안 된다.
 *
 * <p><b>동기로 쓴다.</b> 비동기로 넘기면 스레드 풀 거부·큐에 든 채 서버 사망이라는 실패 지점이
 * 둘 더 생기는데, 감사 로그에는 메일의 {@code email_log = SCHEDULED} 같은 선행 내구 기록이 없어
 * 그 유실을 되짚을 수단이 없다. 얻는 것은 응답 몇 밀리초인데, 행 하나 {@code INSERT}라
 * 사용자가 느끼지 못한다. 메일이 비동기인 것은 SMTP 를 기다리기 때문이고 여기는 다르다.
 *
 * <p><b>{@code REQUIRES_NEW}다.</b> {@code AFTER_COMMIT} 시점에는 원래 트랜잭션의 동기화 정리가
 * 아직 끝나지 않았을 수 있어, 합류하려 들면 커밋되지 않거나 예외가 난다.
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

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(QuoteApproved event) {
        write(AuditEventType.QUOTE_APPROVED.name(), () -> quoteRow(event.companyId(), event.quoteId(),
                AuditEventType.QUOTE_APPROVED, event.actor(), event.occurredAt(),
                quotePayload(event.dealId(), event.quoteNo(), event.responderName(), null)));
    }

    /** 고객 반려 (AP-09·10·19) — 사유도 발생 전에 없던 값이라 부가 필드다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
     * 만들고 저장한다 — <b>둘 다 이 메서드 안에서 끝낸다.</b>
     *
     * <p>{@code AFTER_COMMIT}에서 새는 예외는 커밋된 요청을 500으로 뒤집고, 같은 트랜잭션의
     * 다른 리스너도 실행되지 않는다. 조립(payload 직렬화·null 검사)과 저장을 한 {@code try}로
     * 감싸는 이유가 그것이다.
     *
     * <p><b>실패하면 삼킨다.</b> 남길 곳이 없어 로그만 남긴다 — 사용자 요청은 이미 성공했고,
     * 여기서 되돌릴 수 있는 것이 없다.
     */
    private void write(String eventType, Supplier<AuditLog> row) {
        try {
            auditLogRepository.save(row.get());
        } catch (RuntimeException e) {
            log.error("감사 로그 적재 실패 — 이 사건은 기록되지 않는다. eventType={}, {}",
                    eventType, e.getClass().getName());
        }
    }
}
