package com.twojo.activity.service;

import com.twojo.activity.entity.AuditLog;
import com.twojo.activity.repository.AuditLogRepository;
import com.twojo.boundary.AuditQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@link AuditQuery} 구현 — 타 도메인이 감사 이력을 얻는 유일한 통로다.
 *
 * <p>계약의 판단 근거는 인터페이스 javadoc에 있다. 여기는 payload에서 값을 꺼내는 일만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class AuditQueryImpl implements AuditQuery {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<StageChange> stageChanges(UUID companyId, LocalDate from, LocalDate to) {
        return auditLogRepository.findByCompanyIdAndEventTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                        companyId, AuditEventType.STAGE_MOVED.name(),
                        AuditPeriod.startOfDay(from), AuditPeriod.startOfNextDay(to)).stream()
                .map(this::toStageChange)
                .filter(Objects::nonNull)
                .toList();
    }

    /** 읽히지 않으면 그 행만 버린다 — 한 행 때문에 기간 전체가 실패하면 안 된다. */
    private StageChange toStageChange(AuditLog auditLog) {
        try {
            JsonNode root = objectMapper.readTree(auditLog.getPayload());
            JsonNode stage = root.path("changes").path("stage");
            String before = stage.path("before").asString(null);
            String after = stage.path("after").asString(null);
            if (before == null || after == null) {
                return null;
            }
            return new StageChange(auditLog.getEntityId(), before, after, auditLog.getOccurredAt());
        } catch (JacksonException e) {
            log.warn("감사 로그 {}의 payload 를 읽지 못해 전이 이력에서 뺀다", auditLog.getId(), e);
            return null;
        }
    }
}
