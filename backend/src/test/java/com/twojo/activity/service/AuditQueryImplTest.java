package com.twojo.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.twojo.activity.entity.AuditLog;
import com.twojo.activity.repository.AuditLogRepository;
import com.twojo.boundary.AuditActor;
import com.twojo.boundary.AuditQuery;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

/**
 * 단계 전이 이력 창구 — payload 를 밖으로 내보내지 않고 before·after 를 꺼내 준다.
 */
@ExtendWith(MockitoExtension.class)
class AuditQueryImplTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID DEAL_ID = UUID.randomUUID();
    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-09-30T00:00:00Z");

    @Mock private AuditLogRepository auditLogRepository;

    private AuditQueryImpl auditQuery;

    @BeforeEach
    void setUp() {
        auditQuery = new AuditQueryImpl(auditLogRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("payload 의 changes 에서 before·after 를 꺼내 준다 — 저장 형식은 나가지 않는다")
    void stageChanges_extractsBeforeAndAfter() {
        given(auditLogRepository.findStageChanges(COMPANY_ID, "STAGE_MOVED", FROM, TO))
                .willReturn(List.of(stageMoved("CONSULT", "QUOTE")));

        List<AuditQuery.StageChange> changes = auditQuery.stageChanges(COMPANY_ID, FROM, TO);

        assertThat(changes).singleElement().satisfies(change -> {
            assertThat(change.dealId()).isEqualTo(DEAL_ID);
            assertThat(change.beforeStage()).isEqualTo("CONSULT");
            assertThat(change.afterStage()).isEqualTo("QUOTE");
        });
    }

    /**
     * 한 행의 payload 가 깨졌다고 기간 전체 집계가 실패하면 안 된다 — 그 행만 빠진다.
     * 전환율은 비어 보일지언정 500 으로 죽지 않는다.
     */
    @Test
    @DisplayName("읽히지 않는 payload 행은 결과에서 빠진다 — 예외를 던지지 않는다")
    void stageChanges_brokenPayload_isSkipped() {
        given(auditLogRepository.findStageChanges(COMPANY_ID, "STAGE_MOVED", FROM, TO))
                .willReturn(List.of(broken(), stageMoved("QUOTE", "WON")));

        List<AuditQuery.StageChange> changes = auditQuery.stageChanges(COMPANY_ID, FROM, TO);

        assertThat(changes).singleElement()
                .extracting(AuditQuery.StageChange::afterStage).isEqualTo("WON");
    }

    @Test
    @DisplayName("기간을 그대로 리포지토리에 넘긴다 — 서비스가 값을 바꾸지 않는다")
    void stageChanges_passesRangeThrough() {
        given(auditLogRepository.findStageChanges(COMPANY_ID, "STAGE_MOVED", FROM, TO)).willReturn(List.of());

        auditQuery.stageChanges(COMPANY_ID, FROM, TO);

        then(auditLogRepository).should().findStageChanges(COMPANY_ID, "STAGE_MOVED", FROM, TO);
    }

    private static AuditLog stageMoved(String before, String after) {
        String payload = "{\"dealId\":\"" + DEAL_ID + "\",\"changes\":{\"stage\":"
                + "{\"before\":\"" + before + "\",\"after\":\"" + after + "\"}}}";
        return AuditLog.of(COMPANY_ID, "DEAL", DEAL_ID, "STAGE_MOVED",
                AuditActor.system(), Instant.parse("2026-09-10T02:00:00Z"), payload);
    }

    private static AuditLog broken() {
        return AuditLog.of(COMPANY_ID, "DEAL", DEAL_ID, "STAGE_MOVED",
                AuditActor.system(), Instant.parse("2026-09-10T01:00:00Z"), "{깨진 json");
    }
}
