package com.twojo.activity.service;

import com.twojo.activity.entity.AuditLog;
import com.twojo.activity.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 한 행을 <b>새 트랜잭션</b>으로 저장한다 — 트랜잭션 경계만 담당하는 빈이다 (#328).
 *
 * <p>{@link AuditLogListener}와 다른 빈이어야 한다. {@code @Transactional}은 프록시가 메서드
 * <b>바깥</b>에서 시작·커밋을 하므로, 리스너 메서드에 직접 붙이면 트랜잭션 시작 실패(커넥션 풀
 * 고갈)와 커밋 시 flush 실패(id 가 {@code GenerationType.UUID}라 INSERT 는 커밋 때 나간다)가
 * 리스너의 {@code try} 를 비껴 {@code AFTER_COMMIT} 경로로 새고, 이미 커밋된 요청이 500 이 된다.
 * 이 빈의 {@code save()} 한 호출에 시작·INSERT·커밋이 전부 들어가므로, 리스너는 그 호출을
 * {@code try} 로 감싸기만 하면 된다 — {@code AsyncConfig} javadoc 이 {@code @Async} 제출에 대해
 * 적어둔 것과 같은 원리다(프록시가 하는 일은 메서드 안의 {@code try} 로 못 잡는다).
 *
 * <p><b>{@code REQUIRES_NEW}다.</b> {@code AFTER_COMMIT} 시점에는 원래 트랜잭션의 동기화 정리가
 * 아직 끝나지 않았을 수 있어, 합류하려 들면 커밋되지 않거나 예외가 난다.
 *
 * <p>비동기가 아니다 — 같은 스레드에서 돈다. 비동기를 두지 않는 이유는 {@link AuditLogListener}.
 */
@Component
@RequiredArgsConstructor
class AuditLogWriter {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(AuditLog row) {
        auditLogRepository.save(row);
    }
}
