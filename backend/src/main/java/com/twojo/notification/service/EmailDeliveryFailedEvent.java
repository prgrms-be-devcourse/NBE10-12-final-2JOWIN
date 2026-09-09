package com.twojo.notification.service;

import com.twojo.boundary.MailCommand;
import java.util.UUID;

/**
 * "시스템 메일 1건이 최종 실패했다" — {@link MailOutcomeWriter#markFailed}가 {@code email_log}를
 * SCHEDULED&rarr;FAILED로 <b>실제 전이시켰을 때만</b> 같은 트랜잭션에서 발행한다.
 *
 * <p>{@code EmailFailedNotifier}가 AFTER_COMMIT에 받아 담당 구성원에게 인앱 {@code EMAIL_FAILED}
 * 알림을 만든다 (NT-12, docs/03-requirements.md &sect;2.13). 발송 클래스가 알림 수신자 개념을 알지 않도록
 * 실패 사실만 싣는다 — 페이로드는 {@code email_log} 행의 식별 정보뿐이고 도메인 보강은 없다.
 * 수신자 해석은 리스너가 {@code templateType}으로 분기해 계약을 조합한다.
 *
 * <p>{@code emailLogId}는 알림 로직에 쓰이지 않지만 로그 상관키로 남긴다.
 */
record EmailDeliveryFailedEvent(UUID emailLogId, MailCommand.TemplateType templateType,
                                UUID companyId, UUID refId) {
}
