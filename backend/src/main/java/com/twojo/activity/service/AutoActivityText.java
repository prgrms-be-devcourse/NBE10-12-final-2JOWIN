package com.twojo.activity.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 자동 기록 한 줄의 표시 문구 (AC-07) — 타임라인은 이 문장을 그대로 그린다 (10 §5.3 목업).
 *
 * <p><b>화면이 만들 수 없어 서버가 만든다.</b> 08 {@code ActivityResponse}에 이벤트 종류 필드가
 * 없어 {@code content}가 표시 문자열의 유일한 자리다. 수동 기록의 {@code content}(사람이 쓴 상담
 * 내용)와 같은 칸을 쓰는 이유가 그것이다.
 *
 * <p>문구에 실을 값은 {@code payload}의 부가 필드에서 꺼낸다 (#22 2번) — {@code quoteNo}·
 * {@code orderNo}·{@code changes.stage}. payload가 비었거나 읽히지 않으면 <b>값 없는 문장</b>으로
 * 물러선다. 타임라인 한 줄 때문에 조회 전체가 실패하면 안 된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class AutoActivityText {

    private final ObjectMapper objectMapper;

    /**
     * {@code eventType}과 {@code payload}로 표시 문장을 만든다.
     *
     * <p>모르는 {@code eventType}은 코드를 그대로 쓴다 — 인증 6종처럼 딜과 무관한 이벤트가
     * 병합 키를 갖게 되는 날에도 화면이 빈 줄을 그리지 않게 한다.
     */
    String of(String eventType, String payload, UUID auditLogId) {
        AuditEventType type = AuditEventType.of(eventType);
        String sentence = type == null ? eventType : type.sentence();
        String detail = detailOf(eventType, payload, auditLogId);
        return detail == null ? sentence : sentence + " — " + detail;
    }

    /** 문장 뒤에 붙는 값 — 단계 전이는 "이전 → 이후", 견적·주문은 문서 번호다. */
    private String detailOf(String eventType, String payload, UUID auditLogId) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(payload);
        } catch (JacksonException e) {
            log.warn("감사 로그 {}의 payload 를 읽지 못해 표시 문구에서 값을 뺀다", auditLogId, e);
            return null;
        }

        if (AuditEventType.STAGE_MOVED.name().equals(eventType)) {
            JsonNode stage = root.path("changes").path("stage");
            String before = text(stage.path("before"));
            String after = text(stage.path("after"));
            return before == null || after == null ? null : before + " → " + after;
        }
        String quoteNo = text(root.path("quoteNo"));
        return quoteNo != null ? quoteNo : text(root.path("orderNo"));
    }

    private static String text(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : node.asString();
    }
}
