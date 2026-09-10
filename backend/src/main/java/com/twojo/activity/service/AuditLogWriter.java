package com.twojo.activity.service;

import com.twojo.activity.entity.AuditLog;
import com.twojo.activity.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그를 실제로 쓰는 쪽 — <b>수신 리스너와 반드시 다른 Bean 이어야 한다</b>.
 *
 * <p>한 메서드에 {@code @Async}와 {@code @TransactionalEventListener}를 함께 붙이면 제출 자체가
 * 프록시에서 일어나 거부 예외가 메서드 본문 밖에서 난다 — 그 메서드 안의 {@code try/catch}로는
 * 잡을 수 없다 ({@code AsyncConfig} javadoc). 그래서 받는 쪽과 쓰는 쪽을 나눈다.
 *
 * <p><b>{@code REQUIRES_NEW}다.</b> {@code AFTER_COMMIT} 시점에는 원래 트랜잭션의 동기화 정리가
 * 아직 끝나지 않았을 수 있어, 합류하려 들면 커밋되지 않거나 예외가 난다.
 */
@Component
@RequiredArgsConstructor
class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    /** 별도 스레드에서 적재한다 — 원 작업의 응답을 붙잡지 않는다. */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeAsync(AuditLog row) {
        auditLogRepository.save(row);
    }

    /**
     * 제출이 거부됐을 때 <b>호출 스레드에서</b> 적재한다.
     *
     * <p>메일과 다르다 — 그쪽은 {@code email_log = SCHEDULED} 행이 먼저 저장돼 있어 거부를
     * {@code FAILED}로 닫으면 유실이 지표에 남는다. 감사 로그에는 그런 선행 기록이 없어,
     * 거부를 흘리면 그 사건은 어디에도 남지 않는다. 늦더라도 남기는 쪽을 택한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeNow(AuditLog row) {
        auditLogRepository.save(row);
    }
}
