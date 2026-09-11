package com.twojo.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.twojo.activity.entity.AuditLog;
import com.twojo.activity.repository.AuditLogRepository;
import com.twojo.boundary.AuditActor;
import com.twojo.boundary.AuditQuery;
import java.time.Instant;
import java.time.LocalDate;
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
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    /** KST 로 끊는다 — 9/1 0시부터 10/1 0시 직전까지가 "9월" 이다. */
    private static final Instant FROM_AT = Instant.parse("2026-08-31T15:00:00Z");
    private static final Instant TO_EXCLUSIVE = Instant.parse("2026-09-30T15:00:00Z");

    @Mock private AuditLogRepository auditLogRepository;

    private AuditQueryImpl auditQuery;

    @BeforeEach
    void setUp() {
        auditQuery = new AuditQueryImpl(auditLogRepository, new ObjectMapper());
    }

    @Test
    @DisplayName("payload 의 changes 에서 before·after 를 꺼내 준다 — 저장 형식은 나가지 않는다")
    void stageChanges_extractsBeforeAndAfter() {
        given(auditLogRepository.findByCompanyIdAndEventTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                COMPANY_ID, "STAGE_MOVED", FROM_AT, TO_EXCLUSIVE))
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
        given(auditLogRepository.findByCompanyIdAndEventTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                COMPANY_ID, "STAGE_MOVED", FROM_AT, TO_EXCLUSIVE))
                .willReturn(List.of(broken(), stageMoved("QUOTE", "WON")));

        List<AuditQuery.StageChange> changes = auditQuery.stageChanges(COMPANY_ID, FROM, TO);

        assertThat(changes).singleElement()
                .extracting(AuditQuery.StageChange::afterStage).isEqualTo("WON");
    }

    /**
     * <b>날짜를 KST 시각으로 끊어 넘긴다.</b> 상한은 그날을 포함해야 하므로 <b>다음 날</b> 0시가
     * 되고, 조회는 그 값 미만으로 끊는다 — 그래야 인접한 두 기간이 겹치지도 비지도 않는다.
     * 시간대를 서버 기본값으로 끊으면 자정 부근 전이가 옆 달로 샌다.
     */
    @Test
    @DisplayName("한국 날짜를 시각 경계로 끊어 넘긴다 — 상한은 다음 날 0시다")
    void stageChanges_convertsDatesToSeoulBoundaries() {
        given(auditLogRepository.findByCompanyIdAndEventTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                COMPANY_ID, "STAGE_MOVED", FROM_AT, TO_EXCLUSIVE)).willReturn(List.of());

        auditQuery.stageChanges(COMPANY_ID, FROM, TO);

        then(auditLogRepository).should().findByCompanyIdAndEventTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAsc(
                COMPANY_ID, "STAGE_MOVED", FROM_AT, TO_EXCLUSIVE);
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
