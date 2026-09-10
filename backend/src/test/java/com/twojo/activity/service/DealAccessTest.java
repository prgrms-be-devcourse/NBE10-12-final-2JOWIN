package com.twojo.activity.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.Role;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 활동 이력의 범위 판정 — 상담 기록과 할 일이 함께 쓰는 자리라 여기서만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DealAccessTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID DEAL_ID = UUID.randomUUID();

    private static final AccessContext SALES =
            new AccessContext(COMPANY_ID, MEMBER_ID, Role.SALES_REP, AccessScope.OWNED_ONLY);
    private static final AccessContext ADMIN =
            new AccessContext(COMPANY_ID, UUID.randomUUID(), Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);

    @Mock private DealQuery dealQuery;
    @InjectMocks private DealAccess dealAccess;

    @Test
    @DisplayName("타사 Deal은 404 — 존재 여부를 구별하지 않는다 (SC-09)")
    void requireInScope_otherCompany_throwsNotFound() {
        given(dealQuery.summariesByIds(COMPANY_ID, List.of(DEAL_ID))).willReturn(List.of());

        assertThatThrownBy(() -> dealAccess.requireInScope(SALES, DEAL_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("영업은 담당이 아닌 Deal에 404 (SC-02)")
    void requireInScope_notAssignee_throwsNotFound() {
        given(dealQuery.summariesByIds(COMPANY_ID, List.of(DEAL_ID))).willReturn(List.of(summary()));
        given(dealQuery.assigneeIdOf(DEAL_ID)).willReturn(UUID.randomUUID());

        assertThatThrownBy(() -> dealAccess.requireInScope(SALES, DEAL_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    /** 기업 관리자는 회사 전체를 보므로 담당을 묻지 않는다 — strict stub이 그 호출을 잡는다. */
    @Test
    @DisplayName("기업 관리자는 담당을 묻지 않고 통과한다 (SC-05)")
    void requireInScope_admin_doesNotAskAssignee() {
        given(dealQuery.summariesByIds(COMPANY_ID, List.of(DEAL_ID))).willReturn(List.of(summary()));

        assertThatCode(() -> dealAccess.requireInScope(ADMIN, DEAL_ID)).doesNotThrowAnyException();
    }

    private static DealQuery.DealSummary summary() {
        return new DealQuery.DealSummary(
                DEAL_ID, UUID.randomUUID(), "도담건설 신규", "CONSULT", 1_000_000L, null, Instant.now());
    }
}
