package com.twojo.approval.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CustomerNotificationMessagesTest {

    private final CustomerNotificationMessages messages = new CustomerNotificationMessages();

    @Test
    void 열람_알림은_견적번호를_대괄호로_감싼다() {
        assertThat(messages.quoteViewed("Q-2609-001"))
                .isEqualTo("[Q-2609-001] 고객이 견적을 열람했습니다.");
    }

    @Test
    void 승인_알림은_응답자_이름을_담는다() {
        assertThat(messages.quoteApproved("Q-2609-001", "박지훈"))
                .isEqualTo("[Q-2609-001] 고객이 견적을 승인했습니다. (응답자: 박지훈)");
    }

    @Test
    void 반려_알림은_응답자와_사유를_담는다() {
        assertThat(messages.quoteRejected("Q-2609-001", "박지훈", "예산 초과"))
                .isEqualTo("[Q-2609-001] 고객이 견적을 반려했습니다. (응답자: 박지훈) 사유: 예산 초과");
    }

    @Test
    void 문의_알림은_문의_내용을_이어붙인다() {
        assertThat(messages.inquiryReceived("Q-2609-001", "배송 일정을 알고 싶습니다"))
                .isEqualTo("[Q-2609-001] 고객 문의가 접수되었습니다: 배송 일정을 알고 싶습니다");
    }

    @Test
    void 절삭은_하지_않는다_긴_사유도_그대로_반환한다() {
        String longReason = "가".repeat(600);
        assertThat(messages.quoteRejected("Q-2609-001", "박지훈", longReason)).endsWith(longReason);
    }
}
