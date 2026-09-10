package com.twojo.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.twojo.activity.entity.AuditLog;
import com.twojo.boundary.AuditActor;
import com.twojo.deal.DealStageChanged;
import com.twojo.member.event.MemberDeactivated;
import com.twojo.boundary.AuditActorType;
import com.twojo.order.OrderCreated;
import com.twojo.quote.QuoteApproved;
import com.twojo.quote.QuoteRejected;
import com.twojo.quote.QuoteSent;
import com.twojo.quote.QuoteViewed;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;
import tools.jackson.databind.ObjectMapper;

/**
 * 자동 기록 적재 (AC-07) — 이벤트를 감사 로그 한 행으로 옮기는 매핑의 검증.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogListenerTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID DEAL_ID = UUID.randomUUID();
    private static final UUID QUOTE_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final Instant OCCURRED_AT = Instant.parse("2026-09-10T02:00:00Z");

    @Mock private AuditLogWriter auditLogWriter;
    @Captor private ArgumentCaptor<AuditLog> saved;

    /** payload 조립이 검증 대상이라 ObjectMapper 는 목이 아니라 실제 인스턴스를 쓴다. */
    private AuditLogListener auditLogListener;

    @BeforeEach
    void setUp() {
        auditLogListener = new AuditLogListener(auditLogWriter, new ObjectMapper());
    }

    @Test
    @DisplayName("발송 이벤트를 QUOTE_SENT 행으로 옮긴다 — 대상은 견적, 병합 키는 payload의 dealId")
    void quoteSent_mapsToAuditLogRow() {
        auditLogListener.on(new QuoteSent(COMPANY_ID, QUOTE_ID, DEAL_ID, "Q-2608-014",
                AuditActor.member(MEMBER_ID), OCCURRED_AT));

        then(auditLogWriter).should().writeAsync(saved.capture());
        AuditLog row = saved.getValue();
        assertThat(row.getCompanyId()).isEqualTo(COMPANY_ID);
        assertThat(row.getEntityType()).isEqualTo("QUOTE");
        assertThat(row.getEntityId()).isEqualTo(QUOTE_ID);
        assertThat(row.getEventType()).isEqualTo("QUOTE_SENT");
        assertThat(row.getActorType()).isEqualTo(AuditActorType.MEMBER);
        assertThat(row.getActorId()).isEqualTo(MEMBER_ID);
        assertThat(row.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(row.getPayload())
                .contains("\"dealId\":\"" + DEAL_ID + "\"")
                .contains("\"quoteNo\":\"Q-2608-014\"");
    }

    /**
     * 메일과 달리 감사 로그에는 {@code email_log = SCHEDULED} 같은 선행 내구 기록이 없다.
     * 제출 거부를 흘리면 그 사건은 어디에도 남지 않는다 — 비동기가 안 되면 동기로라도 남긴다.
     */
    @Test
    @DisplayName("제출이 거부되면 그 자리에서 직접 적재한다")
    void submitRejected_writesDirectly() {
        willThrow(new TaskRejectedException("큐 포화"))
                .given(auditLogWriter).writeAsync(any(AuditLog.class));

        auditLogListener.on(new QuoteSent(COMPANY_ID, QUOTE_ID, DEAL_ID, "Q-2608-014",
                AuditActor.member(MEMBER_ID), OCCURRED_AT));

        then(auditLogWriter).should().writeNow(saved.capture());
        assertThat(saved.getValue().getEventType()).isEqualTo("QUOTE_SENT");
    }

    /**
     * {@code AFTER_COMMIT}에서 새는 예외는 <b>커밋된 요청을 500으로 뒤집는다</b>.
     * 같은 트랜잭션의 다른 리스너도 실행되지 않는다 — 적재 실패는 여기서 끝낸다.
     */
    @Test
    @DisplayName("직접 적재까지 실패해도 예외를 밖으로 내보내지 않는다")
    void directWriteFails_swallows() {
        willThrow(new TaskRejectedException("큐 포화"))
                .given(auditLogWriter).writeAsync(any(AuditLog.class));
        willThrow(new IllegalStateException("커넥션 없음"))
                .given(auditLogWriter).writeNow(any(AuditLog.class));

        assertThatCode(() -> auditLogListener.on(new QuoteSent(COMPANY_ID, QUOTE_ID, DEAL_ID,
                "Q-2608-014", AuditActor.member(MEMBER_ID), OCCURRED_AT)))
                .doesNotThrowAnyException();
    }

    /** 값이 바뀌는 유일한 이벤트다 — 나머지는 없던 일이 생긴 것이라 changes 키가 없다 (#22 2번). */
    @Test
    @DisplayName("단계 전이는 changes 안에 before·after를 담는다 — 대상은 딜")
    void dealStageChanged_wrapsChanges() {
        auditLogListener.on(new DealStageChanged(COMPANY_ID, DEAL_ID,
                AuditActor.system(), OCCURRED_AT, "CONSULT", "QUOTE", null));

        then(auditLogWriter).should().writeAsync(saved.capture());
        AuditLog row = saved.getValue();
        assertThat(row.getEntityType()).isEqualTo("DEAL");
        assertThat(row.getEntityId()).isEqualTo(DEAL_ID);
        assertThat(row.getEventType()).isEqualTo("STAGE_MOVED");
        assertThat(row.getActorType()).isEqualTo(AuditActorType.SYSTEM);
        assertThat(row.getActorId()).isNull();
        assertThat(row.getPayload())
                .contains("\"changes\":{\"stage\":{\"before\":\"CONSULT\",\"after\":\"QUOTE\"}}");
    }

    /**
     * 실패 사유는 전이 전에 존재할 수 없어 before 가 예외 없이 빈다 — 그래서 changes 가 아니라
     * 부가 필드다 (#22 2번 기준).
     */
    @Test
    @DisplayName("실패 전이의 사유는 changes가 아니라 부가 필드로 실린다")
    void dealStageChanged_lostReasonIsExtraField() {
        auditLogListener.on(new DealStageChanged(COMPANY_ID, DEAL_ID,
                AuditActor.member(MEMBER_ID), OCCURRED_AT, "NEGOTIATION", "LOST", "경쟁사 선정"));

        then(auditLogWriter).should().writeAsync(saved.capture());
        assertThat(saved.getValue().getPayload())
                .contains("\"lostReason\":\"경쟁사 선정\"")
                .doesNotContain("\"lostReason\":null");
    }

    @Test
    @DisplayName("실패가 아닌 전이에는 사유 키 자체가 없다")
    void dealStageChanged_noLostReasonKey() {
        auditLogListener.on(new DealStageChanged(COMPANY_ID, DEAL_ID,
                AuditActor.system(), OCCURRED_AT, "CONSULT", "QUOTE", null));

        then(auditLogWriter).should().writeAsync(saved.capture());
        assertThat(saved.getValue().getPayload()).doesNotContain("lostReason");
    }

    @Test
    @DisplayName("첫 열람은 QUOTE_VIEWED — 계정이 없어 행위자 id가 비고 CUSTOMER_LINK 다")
    void quoteViewed_hasNoActorId() {
        auditLogListener.on(new QuoteViewed(COMPANY_ID, QUOTE_ID, DEAL_ID, "Q-2608-014",
                AuditActor.customerLink(), OCCURRED_AT));

        then(auditLogWriter).should().writeAsync(saved.capture());
        AuditLog row = saved.getValue();
        assertThat(row.getEventType()).isEqualTo("QUOTE_VIEWED");
        assertThat(row.getActorType()).isEqualTo(AuditActorType.CUSTOMER_LINK);
        assertThat(row.getActorId()).isNull();
    }

    /** 응답자 이름은 검증되지 않은 자기 신고다 (Q-44) — 값은 싣되 인증된 신원처럼 쓰지 않는다. */
    @Test
    @DisplayName("승인은 응답자 이름을 부가 필드로 싣는다 — changes 는 없다")
    void quoteApproved_carriesResponderName() {
        auditLogListener.on(new QuoteApproved(COMPANY_ID, QUOTE_ID, DEAL_ID, "Q-2608-014",
                "김철수", AuditActor.customerLink(), OCCURRED_AT));

        then(auditLogWriter).should().writeAsync(saved.capture());
        assertThat(saved.getValue().getEventType()).isEqualTo("QUOTE_APPROVED");
        assertThat(saved.getValue().getPayload())
                .contains("\"responderName\":\"김철수\"")
                .doesNotContain("changes");
    }

    @Test
    @DisplayName("반려는 사유까지 싣는다")
    void quoteRejected_carriesReason() {
        auditLogListener.on(new QuoteRejected(COMPANY_ID, QUOTE_ID, DEAL_ID, "Q-2608-014",
                "김철수", "예산 초과", AuditActor.customerLink(), OCCURRED_AT));

        then(auditLogWriter).should().writeAsync(saved.capture());
        assertThat(saved.getValue().getEventType()).isEqualTo("QUOTE_REJECTED");
        assertThat(saved.getValue().getPayload()).contains("\"reason\":\"예산 초과\"");
    }

    /** orders 에 deal_id 컬럼이 없어 견적을 거쳐 얻은 값이다 — 타임라인 병합 키라 반드시 실린다. */
    @Test
    @DisplayName("주문 전환은 대상이 주문이고 payload 에 dealId·orderNo·quoteId 가 있다")
    void orderCreated_mapsToOrderRow() {
        UUID orderId = UUID.randomUUID();
        auditLogListener.on(new OrderCreated(COMPANY_ID, orderId, DEAL_ID, "O-2609-001",
                QUOTE_ID, AuditActor.member(MEMBER_ID), OCCURRED_AT));

        then(auditLogWriter).should().writeAsync(saved.capture());
        AuditLog row = saved.getValue();
        assertThat(row.getEntityType()).isEqualTo("ORDER");
        assertThat(row.getEntityId()).isEqualTo(orderId);
        assertThat(row.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(row.getPayload())
                .contains("\"dealId\":\"" + DEAL_ID + "\"")
                .contains("\"orderNo\":\"O-2609-001\"")
                .contains("\"quoteId\":\"" + QUOTE_ID + "\"");
    }

    /**
     * 이관 내역은 이 한 건에 묶어 남는다 (Q-48) — 구독자가 {@code deal}을 읽지 못해
     * 여기 없으면 어느 딜이 넘어갔는지 어디서도 복구되지 않는다.
     */
    @Test
    @DisplayName("구성원 비활성화는 이관 내역까지 남긴다 — 대상은 구성원")
    void memberDeactivated_carriesTransfer() {
        UUID target = UUID.randomUUID();
        UUID transferTo = UUID.randomUUID();
        auditLogListener.on(new MemberDeactivated(COMPANY_ID, target,
                AuditActor.member(MEMBER_ID), OCCURRED_AT, transferTo, List.of(DEAL_ID)));

        then(auditLogWriter).should().writeAsync(saved.capture());
        AuditLog row = saved.getValue();
        assertThat(row.getEntityType()).isEqualTo("MEMBER");
        assertThat(row.getEntityId()).isEqualTo(target);
        assertThat(row.getEventType()).isEqualTo("MEMBER_DEACTIVATED");
        assertThat(row.getPayload())
                .contains("\"transferToMemberId\":\"" + transferTo + "\"")
                .contains("\"dealIds\":[\"" + DEAL_ID + "\"]");
    }

    /** 넘어간 딜이 없으면 대상 키 자체가 없다 — null 이 든 키는 "지웠다"로 읽힌다. */
    @Test
    @DisplayName("이관이 없었으면 대상 키가 없고 딜 목록은 빈 배열이다")
    void memberDeactivated_noTransfer() {
        auditLogListener.on(new MemberDeactivated(COMPANY_ID, UUID.randomUUID(),
                AuditActor.member(MEMBER_ID), OCCURRED_AT, null, List.of()));

        then(auditLogWriter).should().writeAsync(saved.capture());
        assertThat(saved.getValue().getPayload())
                .doesNotContain("transferToMemberId")
                .contains("\"dealIds\":[]");
    }

    /**
     * 조립이 {@code try} 밖에 있으면 여기서 난 예외가 {@code AFTER_COMMIT} 을 타고 올라가
     * <b>커밋된 요청을 500 으로 뒤집는다</b>. 같은 트랜잭션의 다른 리스너도 실행되지 않는다.
     */
    @Test
    @DisplayName("행을 만들지 못해도 예외를 밖으로 내보내지 않는다")
    void buildFails_swallows() {
        auditLogListener = new AuditLogListener(auditLogWriter, brokenMapper());

        assertThatCode(() -> auditLogListener.on(new QuoteSent(COMPANY_ID, QUOTE_ID, DEAL_ID,
                "Q-2608-014", AuditActor.member(MEMBER_ID), OCCURRED_AT)))
                .doesNotThrowAnyException();

        then(auditLogWriter).shouldHaveNoInteractions();
    }

    /** 직렬화가 터지는 상황을 만든다 — 실제로는 순환 참조나 직렬화 불가 타입이 그렇다. */
    private static ObjectMapper brokenMapper() {
        ObjectMapper mapper = mock(ObjectMapper.class);
        given(mapper.writeValueAsString(any())).willThrow(new IllegalStateException("직렬화 실패"));
        return mapper;
    }
}
