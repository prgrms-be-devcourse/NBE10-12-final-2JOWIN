package com.twojo.deal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.CustomerQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.Role;
import com.twojo.deal.dto.DealRequests;
import com.twojo.deal.entity.Deal;
import com.twojo.deal.repository.DealRepository;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Deal 서비스 — 범위 판정(SC-01·02·05)·참조 ID 검증(검증 노트 #3)·낙관적 락·역할 판정을 검증한다.
 *
 * <p>영업과 기업 관리자를 같은 요청으로 돌려 <b>스코프에 따라 답이 갈리는지</b>를 본다 —
 * 이 도메인에서 가장 자주 깨질 수 있는 규칙이다.
 */
@ExtendWith(MockitoExtension.class)
class DealServiceTest {

    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID SALES_ID = UUID.randomUUID();
    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    private static final AccessContext SALES =
            new AccessContext(COMPANY_ID, SALES_ID, Role.SALES_REP, AccessScope.OWNED_ONLY);
    private static final AccessContext ADMIN =
            new AccessContext(COMPANY_ID, ADMIN_ID, Role.COMPANY_ADMIN, AccessScope.COMPANY_ALL);

    @Mock private DealRepository dealRepository;
    @Mock private CustomerQuery customerQuery;
    @Mock private MemberQuery memberQuery;
    @InjectMocks private DealService dealService;

    private static Deal deal(UUID id, UUID assigneeId) {
        Deal deal = Deal.create(COMPANY_ID, CUSTOMER_ID, assigneeId, "한빛 사무가구 30석", 5_000_000L, null);
        ReflectionTestUtils.setField(deal, "id", id);
        ReflectionTestUtils.setField(deal, "version", 0);
        return deal;
    }

    private void givenCustomerAndMembers(AccessContext ctx, UUID... activeMemberIds) {
        given(customerQuery.get(eq(ctx), any())).willReturn(new CustomerQuery.CustomerSummary(CUSTOMER_ID, "한빛"));
        given(memberQuery.findAllActive(COMPANY_ID)).willReturn(
                java.util.Arrays.stream(activeMemberIds)
                        .map(id -> new MemberQuery.MemberSummary(id, "구성원-" + id.toString().substring(0, 4), true))
                        .toList());
    }

    @Nested
    @DisplayName("생성 (DL-01~04)")
    class Create {

        @Test
        @DisplayName("담당자를 비우면 생성자 본인이 배정된다")
        void 담당자_기본값은_생성자() {
            givenCustomerAndMembers(SALES, SALES_ID);
            given(dealRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

            dealService.create(SALES, new DealRequests.CreateDeal(
                    CUSTOMER_ID, "한빛 사무가구 30석", 5_000_000L, LocalDate.now().plusDays(30), null));

            ArgumentCaptor<Deal> captor = ArgumentCaptor.forClass(Deal.class);
            then(dealRepository).should().save(captor.capture());
            assertThat(captor.getValue().getAssigneeMemberId()).isEqualTo(SALES_ID);
            assertThat(captor.getValue().getStage()).isEqualTo(Deal.Stage.LEAD);   // 항상 리드에서 시작
        }

        @Test
        @DisplayName("회사에 없거나 비활성인 구성원은 배정할 수 없다 — 403이 아니라 404 (검증 노트 #3)")
        void 배정_대상_검증() {
            given(customerQuery.get(eq(SALES), any()))
                    .willReturn(new CustomerQuery.CustomerSummary(CUSTOMER_ID, "한빛"));
            given(memberQuery.findAllActive(COMPANY_ID)).willReturn(List.of());   // 활성 구성원 없음

            assertThatThrownBy(() -> dealService.create(SALES, new DealRequests.CreateDeal(
                    CUSTOMER_ID, "제목", null, null, UUID.randomUUID())))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

            then(dealRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("없는 고객사면 저장하지 않는다 — CustomerQuery가 404를 던진다")
        void 고객사_검증() {
            given(customerQuery.get(eq(SALES), any()))
                    .willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

            assertThatThrownBy(() -> dealService.create(SALES, new DealRequests.CreateDeal(
                    CUSTOMER_ID, "제목", null, null, null)))
                    .isInstanceOf(BusinessException.class);

            then(dealRepository).should(never()).save(any());
        }
    }

    @Nested
    @DisplayName("조회 범위 (SC-02·05·09)")
    class Scope {

        @Test
        @DisplayName("영업은 assigneeId를 무엇으로 넣든 본인 담당만 본다")
        void 영업_목록은_본인_담당으로_고정된다() {
            UUID otherMemberId = UUID.randomUUID();
            given(dealRepository.search(any(), any(), any(), any(), any())).willReturn(new PageImpl<>(List.of()));

            dealService.list(SALES, null, otherMemberId, null, PageRequest.of(0, 20));

            then(dealRepository).should().search(eq(COMPANY_ID), eq(null), eq(SALES_ID), eq(null), any(Pageable.class));
        }

        @Test
        @DisplayName("기업 관리자는 요청한 담당자 필터가 그대로 적용된다 (SC-05)")
        void 관리자_목록은_필터를_그대로_쓴다() {
            given(dealRepository.search(any(), any(), any(), any(), any())).willReturn(new PageImpl<>(List.of()));

            dealService.list(ADMIN, Deal.Stage.QUOTE, SALES_ID, CUSTOMER_ID, PageRequest.of(0, 20));

            then(dealRepository).should()
                    .search(eq(COMPANY_ID), eq(Deal.Stage.QUOTE), eq(SALES_ID), eq(CUSTOMER_ID), any(Pageable.class));
        }

        @Test
        @DisplayName("영업이 남의 Deal 상세를 열면 404다 — 권한이 아니라 존재로 답한다 (SC-09)")
        void 남의_딜은_404() {
            UUID dealId = UUID.randomUUID();
            given(dealRepository.findByIdAndCompanyIdAndDeletedAtIsNull(dealId, COMPANY_ID))
                    .willReturn(Optional.of(deal(dealId, UUID.randomUUID())));   // 다른 사람 담당

            assertThatThrownBy(() -> dealService.get(SALES, dealId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }

        @Test
        @DisplayName("기업 관리자는 남의 Deal 상세도 볼 수 있다 (SC-05)")
        void 관리자는_회사_전체를_본다() {
            UUID dealId = UUID.randomUUID();
            given(dealRepository.findByIdAndCompanyIdAndDeletedAtIsNull(dealId, COMPANY_ID))
                    .willReturn(Optional.of(deal(dealId, SALES_ID)));
            given(customerQuery.get(eq(ADMIN), any())).willReturn(new CustomerQuery.CustomerSummary(CUSTOMER_ID, "한빛"));
            given(memberQuery.get(SALES_ID)).willReturn(new MemberQuery.MemberSummary(SALES_ID, "박지훈", true));

            assertThat(dealService.get(ADMIN, dealId).assigneeMemberName()).isEqualTo("박지훈");
        }

        @Test
        @DisplayName("타사 Deal은 회사 스코프에서 이미 걸린다 (SC-01)")
        void 타사_딜은_조회되지_않는다() {
            UUID dealId = UUID.randomUUID();
            given(dealRepository.findByIdAndCompanyIdAndDeletedAtIsNull(dealId, COMPANY_ID))
                    .willReturn(Optional.empty());

            assertThatThrownBy(() -> dealService.get(ADMIN, dealId))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("수정 (DL-02·03)")
    class Update {

        @Test
        @DisplayName("null 필드는 변경하지 않는다 — 부분 수정")
        void 부분_수정() {
            UUID dealId = UUID.randomUUID();
            Deal target = deal(dealId, SALES_ID);
            given(dealRepository.findByIdAndCompanyIdAndDeletedAtIsNull(dealId, COMPANY_ID))
                    .willReturn(Optional.of(target));
            given(customerQuery.get(eq(SALES), any())).willReturn(new CustomerQuery.CustomerSummary(CUSTOMER_ID, "한빛"));
            given(memberQuery.get(SALES_ID)).willReturn(new MemberQuery.MemberSummary(SALES_ID, "박지훈", true));

            dealService.update(SALES, dealId, new DealRequests.UpdateDeal("제목만 변경", null, null, 0));

            assertThat(target.getTitle()).isEqualTo("제목만 변경");
            assertThat(target.getExpectedAmount()).isEqualTo(5_000_000L);   // 그대로
        }

        @Test
        @DisplayName("version이 다르면 STALE_VERSION — 그 사이 누가 먼저 고쳤다")
        void 낙관적_락() {
            UUID dealId = UUID.randomUUID();
            given(dealRepository.findByIdAndCompanyIdAndDeletedAtIsNull(dealId, COMPANY_ID))
                    .willReturn(Optional.of(deal(dealId, SALES_ID)));

            assertThatThrownBy(() -> dealService.update(SALES, dealId,
                    new DealRequests.UpdateDeal("제목", null, null, 1)))   // 실제 version은 0
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.STALE_VERSION);
        }
    }

    @Nested
    @DisplayName("담당자 변경 (DL-05, SC-06)")
    class ChangeAssignee {

        @Test
        @DisplayName("영업은 담당자를 바꿀 수 없다 — 404가 아니라 403 (Q-43)")
        void 영업은_금지() {
            assertThatThrownBy(() -> dealService.changeAssignee(SALES, UUID.randomUUID(),
                    new DealRequests.ChangeAssignee(UUID.randomUUID(), 0)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.FORBIDDEN);

            then(dealRepository).shouldHaveNoInteractions();   // 조회조차 하지 않는다
        }

        @Test
        @DisplayName("기업 관리자는 활성 구성원으로 담당자를 넘길 수 있다")
        void 관리자는_이관한다() {
            UUID dealId = UUID.randomUUID();
            UUID newAssigneeId = UUID.randomUUID();
            Deal target = deal(dealId, SALES_ID);
            given(dealRepository.findByIdAndCompanyIdAndDeletedAtIsNull(dealId, COMPANY_ID))
                    .willReturn(Optional.of(target));
            givenCustomerAndMembers(ADMIN, newAssigneeId);

            dealService.changeAssignee(ADMIN, dealId, new DealRequests.ChangeAssignee(newAssigneeId, 0));

            assertThat(target.getAssigneeMemberId()).isEqualTo(newAssigneeId);
        }

        @Test
        @DisplayName("비활성 구성원에게는 넘길 수 없다 (SC-06)")
        void 비활성_대상은_거부() {
            UUID dealId = UUID.randomUUID();
            given(dealRepository.findByIdAndCompanyIdAndDeletedAtIsNull(dealId, COMPANY_ID))
                    .willReturn(Optional.of(deal(dealId, SALES_ID)));
            given(memberQuery.findAllActive(COMPANY_ID)).willReturn(List.of());

            assertThatThrownBy(() -> dealService.changeAssignee(ADMIN, dealId,
                    new DealRequests.ChangeAssignee(UUID.randomUUID(), 0)))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
