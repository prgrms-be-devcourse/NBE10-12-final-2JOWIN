package com.twojo.activity.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.twojo.activity.dto.AuditLogDetailResponse;
import com.twojo.activity.dto.AuditLogResponse;
import com.twojo.activity.entity.AuditLog;
import com.twojo.activity.repository.AuditLogRepository;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AuditActorType;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.response.PageResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 조회 (AC-11) — 기업 관리자 전용이다.
 *
 * <p>역할 위반은 403 {@code FORBIDDEN}이다 (09 구현 위치, Q-43). 리소스 범위가 아니라
 * 행위 자체가 역할로 갈리므로 존재를 숨길 이유가 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final MemberQuery memberQuery;
    private final ObjectMapper objectMapper;

    /** 목록 (AC-11) — payload는 싣지 않는다 (07 §B). 안 보낸 필터는 조건에서 빠진다. */
    public PageResponse<AuditLogResponse> list(AccessContext ctx, String entityType,
                                               Instant from, Instant to, Pageable pageable) {
        requireAdmin(ctx);
        return PageResponse.from(
                auditLogRepository.search(ctx.companyId(), entityType, from, to, pageable)
                        .map(this::toResponse));
    }

    /** 상세 (AC-11) — payload를 changes로 펼친다. 없거나 타사 것이면 404 (SC-09). */
    public AuditLogDetailResponse get(AccessContext ctx, UUID auditLogId) {
        requireAdmin(ctx);
        AuditLog log = auditLogRepository.findByIdAndCompanyId(auditLogId, ctx.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return new AuditLogDetailResponse(log.getId(), log.getEntityType(), log.getEntityId(),
                log.getEventType(), log.getActorType().name(), log.getActorId(),
                actorName(log), changesOf(log.getPayload()), log.getOccurredAt());
    }

    /**
     * 행위자 이름 — <b>구성원일 때만 있다.</b>
     *
     * <p>고객 링크와 자동 전이는 계정이 없어 {@code actorId}가 비고, 플랫폼 관리자는
     * 이름 컬럼 자체가 없다({@code PlatformAdminQuery} — "관리자에게는 이름 컬럼이 없다").
     * 그래서 나머지 셋은 이름을 채울 원천이 없다.
     */
    private String actorName(AuditLog log) {
        if (log.getActorType() != AuditActorType.MEMBER || log.getActorId() == null) {
            return null;
        }
        return memberQuery.get(log.getActorId()).name();
    }

    /**
     * payload의 {@code changes}만 꺼내 필드별 before·after로 펼친다 (#22 §2).
     *
     * <p><b>{@code changes} 키가 없는 payload가 정상이다.</b> 견적 발송처럼 없던 일이 생긴
     * 이벤트는 before에 넣을 값이 없어 규약이 그 키를 빼도록 정했다. 그런 행은 빈 맵이다.
     *
     * <p>payload 자체가 비었거나 형태가 어긋나도 조회는 끝까지 간다 — 감사 기록을 보러 온
     * 화면이 데이터 한 줄 때문에 통째로 막히면 안 된다.
     */
    private Map<String, AuditLogDetailResponse.FieldChange> changesOf(String payload) {
        if (payload == null || payload.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode changes = objectMapper.readTree(payload).path("changes");
            Map<String, AuditLogDetailResponse.FieldChange> result = new LinkedHashMap<>();
            changes.properties().forEach(entry -> result.put(entry.getKey(),
                    new AuditLogDetailResponse.FieldChange(
                            value(entry.getValue().path("before")),
                            value(entry.getValue().path("after")))));
            return result;
        } catch (JacksonException e) {
            return Map.of();
        }
    }

    /** 값 타입이 제각각이라 JSON 그대로 살린다. 없는 키는 null이다. */
    private static Object value(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null
                : node.isTextual() ? node.asText() : (Object) node;
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getEntityType(), log.getEntityId(),
                log.getEventType(), log.getActorType().name(), log.getActorId(),
                actorName(log), log.getOccurredAt());
    }

    /**
     * 감사 로그 조회는 기업 관리자만 (09).
     * 컨트롤러가 아니라 여기서 판정한다 — 웹 계층 검사는 다른 호출 경로가 생기면 뚫린다.
     */
    private void requireAdmin(AccessContext ctx) {
        if (ctx.role() != Role.COMPANY_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
