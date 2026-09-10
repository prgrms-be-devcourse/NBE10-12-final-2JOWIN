package com.twojo.activity.service;

/**
 * {@code audit_log.event_type} 값과 타임라인 표시 문장 (AC-07).
 *
 * <p><b>두 쓰임이 한 자리에 있어야 한다.</b> 적재하는 쪽이 쓰는 문자열과 타임라인이 문장을 찾는
 * 키가 같아야 하는데, 따로 두면 한쪽만 고쳐지는 날이 온다 — 그때 화면에는 문장 대신 코드가 나간다.
 *
 * <p>발행자는 이 값을 싣지 않는다. 이벤트 타입에서 적재 리스너가 매핑한다 (#22 — 오타 위험 제거).
 * 문자열이 곧 저장값이라 <b>상수 이름을 바꾸면 이미 쌓인 행과 갈라진다.</b>
 */
enum AuditEventType {

    STAGE_MOVED("단계를 이동했습니다"),
    QUOTE_SENT("견적을 발송했습니다"),
    QUOTE_VIEWED("고객이 견적을 열람했습니다"),
    QUOTE_APPROVED("고객이 견적을 승인했습니다"),
    QUOTE_REJECTED("고객이 견적을 반려했습니다"),
    ORDER_CREATED("주문으로 전환했습니다"),
    MEMBER_DEACTIVATED("구성원을 비활성화했습니다");

    private final String sentence;

    AuditEventType(String sentence) {
        this.sentence = sentence;
    }

    String sentence() {
        return sentence;
    }

    /**
     * 모르는 값이면 {@code null} — 호출자가 코드를 그대로 쓴다.
     * 인증 6종처럼 아직 여기 없는 이벤트가 섞여도 화면이 빈 줄을 그리지 않게 한다.
     */
    static AuditEventType of(String eventType) {
        for (AuditEventType type : values()) {
            if (type.name().equals(eventType)) {
                return type;
            }
        }
        return null;
    }
}
