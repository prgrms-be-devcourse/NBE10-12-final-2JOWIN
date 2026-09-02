package com.twojo.notification.service;

import com.twojo.boundary.MailCommand;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * {@link MailCommand} 스텁 — 빈 자리만 채운다.
 *
 * <p>approval({@code ViewTokenCommand.issue})·auth(AU-05)가 이 빈을 주입받아야 컨텍스트가 뜬다.
 * 실구현은 메일 파이프라인 이슈에서 이 {@code throw}를 대체한다 — {@code email_log} SCHEDULED 행
 * 기록(멱등) + 발송 이벤트 발행. 조용히 성공하면 링크만 만들어지고 안내 메일이 안 나간 채로 발송이
 * 끝나므로, 크게 터지는 편이 안전하다 (D가 {@code ViewTokenCommand.issue}를 throw로 둔 것과 같은 판단).
 */
@Service
class MailCommandImpl implements MailCommand {

    @Override
    public void schedule(TemplateType type, UUID companyId, String recipientEmail,
                         UUID refId, String subject, String body) {
        throw new UnsupportedOperationException("MailCommand.schedule — 메일 파이프라인 이슈에서 구현 예정");
    }
}
