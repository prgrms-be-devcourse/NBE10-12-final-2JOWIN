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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 감사 로그 조회 (AC-11) — 기업 관리자 전용이다.
 *
 * <p>역할 위반은 403 {@code FORBIDDEN}이다 (Q-43). 리소스 범위가 아니라 행위 자체가 역할로
 * 갈리므로 존재를 숨길 이유가 없다 — 09 매트릭스가 감사 로그를 기업 관리자 ⭕ / 영업 ✕로 둔다.
 *
 * <p><b>09 구현 위치 표는 이 판정을 권한 어노테이션 층에 두고 감사 로그 조회를 그 예로 든다.</b>
 * 그 어노테이션이 아직 없어(09 다음 단계 2번) 서비스에서 판정한다 —
 * {@code ProductService}·{@code MemberAdminService}가 같은 형태이고, 어노테이션이 나오면 대체된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final MemberQuery memberQuery;
    private final ObjectMapper objectMapper;

    /**
     * 목록 (AC-11) — payload는 싣지 않는다 (07 §B). 안 보낸 필터는 조건에서 빠진다.
     *
     * <p>기간은 <b>한국 날짜</b>로 받는다 (#289). 화면이 날짜 선택기이고, 날짜를 시각으로 끊는
     * 규칙은 {@code occurred_at}을 소유한 이 모듈이 갖는다({@link AuditPeriod}) — 호출자가 끊어
     * 넘기면 클라이언트마다 경계가 갈린다. {@code to}는 그날을 <b>포함</b>한다.
     */
    public PageResponse<AuditLogResponse> list(AccessContext ctx, String entityType,
                                               LocalDate from, LocalDate to, Pageable pageable) {
        requireAdmin(ctx);
        return PageResponse.from(
                auditLogRepository.search(ctx.companyId(), entityType,
                                AuditPeriod.startOfDay(from), AuditPeriod.startOfNextDay(to), pageable)
                        .map(this::toResponse));
    }

    /** 상세 (AC-11) — payload를 changes로 펼친다. 없거나 타사 것이면 404 (SC-09). */
    public AuditLogDetailResponse get(AccessContext ctx, UUID auditLogId) {
        requireAdmin(ctx);
        AuditLog auditLog = auditLogRepository.findByIdAndCompanyId(auditLogId, ctx.companyId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return new AuditLogDetailResponse(auditLog.getId(), auditLog.getEntityType(),
                auditLog.getEntityId(), auditLog.getEventType(), auditLog.getActorType().name(),
                auditLog.getActorId(), actorName(auditLog),
                changesOf(auditLog.getPayload(), auditLog.getId()), auditLog.getOccurredAt());
    }

    /**
     * 행위자 이름 — <b>구성원일 때만 있다.</b>
     *
     * <p>고객 링크와 자동 전이는 계정이 없어 {@code actorId}가 비고, 플랫폼 관리자는
     * 이름 컬럼 자체가 없다({@code PlatformAdminQuery} — "관리자에게는 이름 컬럼이 없다").
     * 그래서 나머지 셋은 이름을 채울 원천이 없다.
     */
    private String actorName(AuditLog auditLog) {
        if (auditLog.getActorType() != AuditActorType.MEMBER || auditLog.getActorId() == null) {
            return null;
        }
        try {
            return memberQuery.get(auditLog.getActorId()).name();
        } catch (BusinessException e) {
            if (e.getErrorCode() != ErrorCode.RESOURCE_NOT_FOUND) {
                throw e;   // 계약이 약속한 것은 "없으면 RESOURCE_NOT_FOUND"뿐이다 (MemberQuery:13)
            }
            // audit_log.actor_id 에는 FK 가 없다 (V1:361). 고객사 상세가 memberQuery 를 그냥 부를 수
            // 있는 것은 created_by_member_id 가 NOT NULL FK 라서인데 여기엔 그 보증이 없다 —
            // 한 행의 구성원이 없으면 목록 전체가 404 가 된다. 이름만 비우고 나머지 행을 살린다.
            log.warn("감사 로그 {}의 행위자 {}를 찾지 못해 이름을 비운다",
                    auditLog.getId(), auditLog.getActorId(), e);
            return null;
        }
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
    private Map<String, AuditLogDetailResponse.FieldChange> changesOf(String payload, UUID auditLogId) {
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
            // 깨진 payload 와 정상 발생형 이벤트가 응답에서 똑같이 빈 맵이다 — 로그가 없으면 구별할 수 없다
            log.warn("감사 로그 {}의 payload 를 읽지 못해 변경 내역을 비운다", auditLogId, e);
            return Map.of();
        }
    }

    /**
     * 값 타입이 제각각이라 자바 원시값으로 되돌린다. 없는 키는 null이다.
     *
     * <p>{@code JsonNode}를 그대로 담으면 Jackson 타입이 응답 JSON 에 실려 나간다 —
     * 직렬화 결과가 라이브러리 구현에 묶인다.
     */
    private Object value(JsonNode node) {
        return node.isMissingNode() || node.isNull() ? null : objectMapper.convertValue(node, Object.class);
    }

    private AuditLogResponse toResponse(AuditLog auditLog) {
        return new AuditLogResponse(auditLog.getId(), auditLog.getEntityType(), auditLog.getEntityId(),
                auditLog.getEventType(), auditLog.getActorType().name(), auditLog.getActorId(),
                actorName(auditLog), auditLog.getOccurredAt());
    }

    /**
     * 감사 로그 조회는 기업 관리자만 (09 매트릭스 감사 로그 행).
     * 컨트롤러가 아니라 여기서 판정한다 — 웹 계층 검사는 다른 호출 경로가 생기면 뚫린다.
     */
    private void requireAdmin(AccessContext ctx) {
        if (ctx.role() != Role.COMPANY_ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
