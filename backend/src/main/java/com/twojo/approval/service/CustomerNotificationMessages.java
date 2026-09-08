package com.twojo.approval.service;

import org.springframework.stereotype.Component;

/**
 * 고객 응답 흐름이 만드는 인앱 알림 문자열 (NT-03 · NT-04 · NT-10). 완성된 한글 한 줄을
 * {@link com.twojo.boundary.NotificationCommand}에 넘긴다 — {@code notification.message VARCHAR(500)}
 * 초과분 절삭은 {@code NotificationCommandImpl} 몫이라 여기서는 자르지 않는다.
 *
 * <p>문구를 서비스 로직에서 분리해 한곳에서 관리·검증한다. 서비스·레코더 테스트는 이 빈을 목으로
 * 두어 한글 문구에 결합하지 않는다.
 */
@Component
class CustomerNotificationMessages {

    /** NT-03 — 고객이 견적을 열람. */
    String quoteViewed(String quoteNo) {
        return "[" + quoteNo + "] 고객이 견적을 열람했습니다.";
    }

    /** NT-04 — 고객이 견적을 승인 (AP-12). */
    String quoteApproved(String quoteNo, String responderName) {
        return "[" + quoteNo + "] 고객이 견적을 승인했습니다. (응답자: " + responderName + ")";
    }

    /** NT-04 — 고객이 견적을 반려 (AP-12). 사유가 길면 NotificationCommandImpl이 절삭한다. */
    String quoteRejected(String quoteNo, String responderName, String reason) {
        return "[" + quoteNo + "] 고객이 견적을 반려했습니다. (응답자: " + responderName + ") 사유: " + reason;
    }

    /** NT-10 — 고객이 문의를 남김 (Q-20). 문의 조회 API가 없어(Q-42) 이 알림이 접수 내용 통지의 전부다. */
    String inquiryReceived(String quoteNo, String content) {
        return "[" + quoteNo + "] 고객 문의가 접수되었습니다: " + content;
    }
}
