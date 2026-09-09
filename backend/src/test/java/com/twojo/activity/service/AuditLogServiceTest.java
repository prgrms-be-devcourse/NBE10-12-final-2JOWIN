package com.twojo.activity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.activity.dto.AuditLogDetailResponse;
import com.twojo.activity.dto.AuditLogResponse;
import com.twojo.activity.entity.AuditLog;
import com.twojo.activity.repository.AuditLogRepository;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import tools.jackson.databind.ObjectMapper;
import com.twojo.boundary.AuditActor;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.response.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * 감사 로그 조회 (AC-11) — 기업 관리자 전용이고 회사 스코프가 항상 걸린다.
 *
 * <p>역할 위반은 403 {@code FORBIDDEN}이다 (09 구현 위치, Q-43). 리소스 범위 위반이 아니라
 * 행위 자체가 역할로 갈리므로 404가 아니다.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();

    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final Instant OCCURRED = Instant.parse("2026-08-24T01:00:00Z");

    private static final AccessContext ADMIN =
            new AccessContext(COMPANY_ID, MEMBER_ID, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);
    private static final AccessContext SALES =
            new AccessContext(COMPANY_ID, MEMBER_ID, Role.SALES_REP, AccessScope.OWNED_ONLY);

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private MemberQuery memberQuery;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();
    @InjectMocks private AuditLogService auditLogService;

    @Test
    @DisplayName("영업 담당자의 목록 조회는 403이고 저장소까지 가지 않는다 (AC-11, Q-43)")
    void list_salesRep_forbidden() {
        assertThatThrownBy(() -> auditLogService.list(SALES, null, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        then(auditLogRepository).should(never()).search(any(), any(), any(), any(), any());
    }

    /** 자동 전이 행위자다 — actorId가 없어 이름 조회를 타지 않는다. 이름은 아래 전용 테스트에서 본다. */
    private static AuditLog 로그(String payload) {
        return AuditLog.of(COMPANY_ID, "DEAL", UUID.randomUUID(), "STAGE_MOVED",
                AuditActor.system(), OCCURRED, payload);
    }

    @Test
    @DisplayName("목록은 회사 스코프로 조회한다 — 안 보낸 필터는 null 그대로 넘어간다 (SC-01)")
    void list_admin_scopedAndOptionalFilters() {
        given(auditLogRepository.search(eq(COMPANY_ID), isNull(), isNull(), isNull(), any()))
                .willReturn(new PageImpl<>(List.of(로그("{}"))));

        PageResponse<AuditLogResponse> response =
                auditLogService.list(ADMIN, null, null, null, PageRequest.of(0, 20));

        then(auditLogRepository).should().search(eq(COMPANY_ID), isNull(), isNull(), isNull(), any());
        assertThat(response.content()).hasSize(1);
    }

    @Test
    @DisplayName("타사 로그 상세는 404 — 존재 여부를 구별해서 말하지 않는다 (SC-09)")
    void detail_otherCompany_notFound() {
        UUID logId = UUID.randomUUID();
        given(auditLogRepository.findByIdAndCompanyId(logId, COMPANY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> auditLogService.get(ADMIN, logId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("payload의 changes를 before·after로 펼친다 (#22 §2)")
    void detail_changes_unwrapped() {
        UUID logId = UUID.randomUUID();
        given(auditLogRepository.findByIdAndCompanyId(logId, COMPANY_ID)).willReturn(Optional.of(
                로그("""
                        {"dealId":"5d000000-0000-4000-8000-000000000008",
                         "changes":{"stage":{"before":"CONSULT","after":"QUOTE"}}}
                        """)));

        AuditLogDetailResponse response = auditLogService.get(ADMIN, logId);

        assertThat(response.changes()).containsOnlyKeys("stage");
        assertThat(response.changes().get("stage").before()).isEqualTo("CONSULT");
        assertThat(response.changes().get("stage").after()).isEqualTo("QUOTE");
    }

    @Test
    @DisplayName("changes 키가 없는 발생형 payload는 빈 맵이다 — 없는 키를 파고들지 않는다 (#22 §2)")
    void detail_noChangesKey_emptyMap() {
        UUID logId = UUID.randomUUID();
        given(auditLogRepository.findByIdAndCompanyId(logId, COMPANY_ID)).willReturn(Optional.of(
                로그("{\"dealId\":\"5d000000-0000-4000-8000-000000000008\",\"quoteNo\":\"Q-2608-014\"}")));

        assertThat(auditLogService.get(ADMIN, logId).changes()).isEmpty();
    }

    @Test
    @DisplayName("행위자가 구성원이면 이름을 채운다 — 그 밖에는 이름이 없다 (08 §B actorName)")
    void detail_memberActor_hasName() {
        UUID logId = UUID.randomUUID();
        AuditLog 구성원_로그 = AuditLog.of(COMPANY_ID, "MEMBER", UUID.randomUUID(), "MEMBER_DEACTIVATED",
                AuditActor.member(ACTOR_ID), OCCURRED, "{}");
        given(auditLogRepository.findByIdAndCompanyId(logId, COMPANY_ID))
                .willReturn(Optional.of(구성원_로그));
        given(memberQuery.get(ACTOR_ID))
                .willReturn(new MemberQuery.MemberSummary(ACTOR_ID, "박지훈", true));

        assertThat(auditLogService.get(ADMIN, logId).actorName()).isEqualTo("박지훈");
    }

    @Test
    @DisplayName("계정 없는 행위자는 이름 조회를 하지 않는다 — 고객 링크·시스템은 actorId가 없다")
    void detail_customerLinkActor_noNameLookup() {
        UUID logId = UUID.randomUUID();
        AuditLog log = AuditLog.of(COMPANY_ID, "QUOTE", UUID.randomUUID(), "QUOTE_VIEWED",
                AuditActor.customerLink(), OCCURRED, "{}");
        given(auditLogRepository.findByIdAndCompanyId(logId, COMPANY_ID)).willReturn(Optional.of(log));

        assertThat(auditLogService.get(ADMIN, logId).actorName()).isNull();
        then(memberQuery).should(never()).get(any());
    }

    @Test
    @DisplayName("영업 담당자의 상세 조회도 403이다 — 목록만 막으면 우회된다 (AC-11)")
    void detail_salesRep_forbidden() {
        assertThatThrownBy(() -> auditLogService.get(SALES, UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        then(auditLogRepository).should(never()).findByIdAndCompanyId(any(), any());
    }

    @Test
    @DisplayName("보낸 필터는 그대로 넘어간다 — 서비스가 값을 바꾸거나 떨어뜨리지 않는다")
    void list_filtersPassedThrough() {
        Instant from = Instant.parse("2026-08-25T00:00:00Z");
        Instant to = Instant.parse("2026-08-26T00:00:00Z");
        given(auditLogRepository.search(eq(COMPANY_ID), eq("QUOTE"), eq(from), eq(to), any()))
                .willReturn(new PageImpl<>(List.of()));

        auditLogService.list(ADMIN, "QUOTE", from, to, PageRequest.of(0, 20));

        then(auditLogRepository).should().search(eq(COMPANY_ID), eq("QUOTE"), eq(from), eq(to), any());
    }

    @Test
    @DisplayName("행위자를 못 찾아도 그 행만 이름이 비고 조회는 성공한다 — actor_id 에 FK 가 없다")
    void detail_missingActor_nameOnlyBlank() {
        UUID logId = UUID.randomUUID();
        AuditLog 구성원_로그 = AuditLog.of(COMPANY_ID, "MEMBER", UUID.randomUUID(), "MEMBER_DEACTIVATED",
                AuditActor.member(ACTOR_ID), OCCURRED, "{}");
        given(auditLogRepository.findByIdAndCompanyId(logId, COMPANY_ID))
                .willReturn(Optional.of(구성원_로그));
        given(memberQuery.get(ACTOR_ID)).willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        AuditLogDetailResponse response = auditLogService.get(ADMIN, logId);

        assertThat(response.actorName()).isNull();
        assertThat(response.actorType()).isEqualTo("MEMBER");
    }

    @Test
    @DisplayName("entityType 만 보내면 기간은 null 로 넘어간다")
    void list_entityTypeOnly() {
        given(auditLogRepository.search(eq(COMPANY_ID), eq("QUOTE"), isNull(), isNull(), any()))
                .willReturn(new PageImpl<>(List.of()));

        auditLogService.list(ADMIN, "QUOTE", null, null, PageRequest.of(0, 20));

        then(auditLogRepository).should().search(eq(COMPANY_ID), eq("QUOTE"), isNull(), isNull(), any());
    }

    @Test
    @DisplayName("기간만 보내면 entityType 은 null 로 넘어간다")
    void list_periodOnly() {
        Instant from = Instant.parse("2026-08-25T00:00:00Z");
        given(auditLogRepository.search(eq(COMPANY_ID), isNull(), eq(from), isNull(), any()))
                .willReturn(new PageImpl<>(List.of()));

        auditLogService.list(ADMIN, null, from, null, PageRequest.of(0, 20));

        then(auditLogRepository).should().search(eq(COMPANY_ID), isNull(), eq(from), isNull(), any());
    }
}
