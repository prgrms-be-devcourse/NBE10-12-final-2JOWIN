package com.twojo.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.twojo.approval.dto.ApproveQuoteRequest;
import com.twojo.approval.dto.CreateInquiryRequest;
import com.twojo.approval.dto.PublicQuoteResponse;
import com.twojo.approval.dto.RejectQuoteRequest;
import com.twojo.approval.entity.CustomerInquiry;
import com.twojo.approval.entity.QuoteViewToken;
import com.twojo.approval.repository.CustomerInquiryRepository;
import com.twojo.approval.repository.QuoteViewTokenRepository;
import com.twojo.approval.token.TokenGenerator;
import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.DealQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.NotificationCommand;
import com.twojo.boundary.NotificationCommand.NotificationType;
import com.twojo.boundary.QuoteCommand;
import com.twojo.boundary.QuoteQuery;
import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link CustomerQuoteService} — 링크 상태 방어(404/410/409), 조립, 첫 열람 부수효과 위임,
 * 승인·반려·문의의 호출 순서·차단을 목으로 고정한다. 실 PG·트랜잭션은 통합 테스트가 커버한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CustomerQuoteServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");
    private static final UUID QUOTE_ID = UUID.fromString("a0000000-0000-4000-8000-000000000001");
    private static final UUID DEAL_ID = UUID.fromString("b0000000-0000-4000-8000-000000000001");
    private static final UUID COMPANY_ID = UUID.fromString("c0000000-0000-4000-8000-000000000001");
    private static final UUID ASSIGNEE_ID = UUID.fromString("d0000000-0000-4000-8000-000000000001");
    private static final UUID CONTACT_ID = UUID.fromString("e0000000-0000-4000-8000-000000000001");
    private static final String QUOTE_NO = "Q-2609-014";
    private static final String COMPANY_NAME = "한빛오피스";
    private static final String BUSINESS_NO = "1234567890123";

    @Mock
    private QuoteViewTokenRepository quoteViewTokenRepository;
    @Mock
    private QuoteQuery quoteQuery;
    @Mock
    private QuoteCommand quoteCommand;
    @Mock
    private CompanyQuery companyQuery;
    @Mock
    private DealQuery dealQuery;
    @Mock
    private MemberQuery memberQuery;
    @Mock
    private NotificationCommand notificationCommand;
    @Mock
    private CustomerInquiryRepository customerInquiryRepository;
    @Mock
    private CustomerNotificationMessages messages;
    @Mock
    private FirstViewRecorder firstViewRecorder;

    private CustomerQuoteService service;

    @BeforeEach
    void setUp() {
        // TokenGenerator는 의존성이 없어 실객체 — 조회는 findByTokenHash(anyString())로 목킹한다.
        service = new CustomerQuoteService(quoteViewTokenRepository, new TokenGenerator(), quoteQuery,
                quoteCommand, companyQuery, dealQuery, memberQuery, notificationCommand,
                customerInquiryRepository, messages, firstViewRecorder);
    }

    // ─────────────── 토큰 해석 (공통) ───────────────

    @Test
    @DisplayName("토큰 해시가 안 맞으면 404 RESOURCE_NOT_FOUND")
    void 해시_불일치면_404() {
        given(quoteViewTokenRepository.findByTokenHash(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.view("garbage", NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("EXPIRED 상태 토큰이면 410 LINK_EXPIRED")
    void EXPIRED_상태면_410() {
        givenToken(expiredStatusToken());

        assertThatThrownBy(() -> service.view("raw", NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LINK_EXPIRED));
    }

    @Test
    @DisplayName("유효기간이 지난 토큰이면 410 LINK_EXPIRED (배치 지연과 무관)")
    void 시간_경과면_410() {
        givenToken(timePassedToken());

        assertThatThrownBy(() -> service.view("raw", NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LINK_EXPIRED));
    }

    // ─────────────── GET 열람 ───────────────

    @Test
    @DisplayName("첫 열람(SENT)이면 FirstViewRecorder에 위임하고 응답 status를 VIEWED로 보정한다")
    void 첫_열람이면_위임하고_status_VIEWED() {
        givenToken(activeToken());
        givenView(view("SENT"));
        givenCompany(true);
        givenAssignee();

        PublicQuoteResponse res = service.view("raw", NOW);

        verify(firstViewRecorder).recordFirstView(any(QuoteQuery.PublicQuoteView.class));
        assertThat(res.status()).isEqualTo("VIEWED");
        assertThat(res.respondable()).isTrue();
    }

    @Test
    @DisplayName("재열람(VIEWED)이면 FirstViewRecorder를 부르지 않는다")
    void 재열람이면_위임_안_함() {
        givenToken(activeToken());
        givenView(view("VIEWED"));
        givenCompany(true);
        givenAssignee();

        PublicQuoteResponse res = service.view("raw", NOW);

        verifyNoInteractions(firstViewRecorder);
        assertThat(res.status()).isEqualTo("VIEWED");
    }

    @Test
    @DisplayName("RESPONDED 링크도 열람은 200으로 허용하고 respondable=false")
    void RESPONDED_링크도_열람_허용() {
        givenToken(respondedToken());
        givenView(view("APPROVED"));
        givenCompany(true);
        givenAssignee();

        PublicQuoteResponse res = service.view("raw", NOW);

        assertThat(res.status()).isEqualTo("APPROVED");
        assertThat(res.respondable()).isFalse();
        verifyNoInteractions(firstViewRecorder);
    }

    @Test
    @DisplayName("회사 정지 중이면 열람은 되고 respondable=false")
    void 정지_회사면_열람은_되고_respondable_false() {
        givenToken(activeToken());
        givenView(view("VIEWED"));
        givenCompany(false);
        givenAssignee();

        PublicQuoteResponse res = service.view("raw", NOW);

        assertThat(res.respondable()).isFalse();
    }

    @Test
    @DisplayName("견적 status가 DRAFT면 loadView 단계에서 404 (회사 조회 전)")
    void DRAFT_견적이면_404() {
        givenToken(activeToken());
        givenView(view("DRAFT"));

        assertThatThrownBy(() -> service.view("raw", NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        verifyNoInteractions(companyQuery, dealQuery, memberQuery);
    }

    @Test
    @DisplayName("견적 status가 WITHDRAWN이면 404")
    void WITHDRAWN_견적이면_404() {
        givenToken(activeToken());
        givenView(view("WITHDRAWN"));

        assertThatThrownBy(() -> service.view("raw", NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Test
    @DisplayName("첫 열람 부수효과가 예외를 던져도 열람 응답은 200으로 반환한다 (status는 보정 안 됨)")
    void 부수효과_실패해도_뷰는_반환() {
        givenToken(activeToken());
        givenView(view("SENT"));
        givenCompany(true);
        givenAssignee();
        willThrow(new RuntimeException("boom")).given(firstViewRecorder).recordFirstView(any());

        PublicQuoteResponse res = service.view("raw", NOW);

        assertThat(res.status()).isEqualTo("SENT");
        assertThat(res.respondable()).isTrue();
    }

    @Test
    @DisplayName("조립 — 회사·담당자·금액·항목(sortOrder 정렬)을 응답에 채운다")
    void 조립_필드_매핑과_항목_정렬() {
        givenToken(activeToken());
        givenView(view("VIEWED", List.of(
                new QuoteQuery.PublicQuoteView.Item("B품목", "개", 1, 100L, 100L, 2),
                new QuoteQuery.PublicQuoteView.Item("A품목", "대", 3, 50L, 150L, 1))));
        givenCompany(true);
        givenAssignee();

        PublicQuoteResponse res = service.view("raw", NOW);

        assertThat(res.items()).extracting(PublicQuoteResponse.ItemView::name)
                .containsExactly("A품목", "B품목");
        assertThat(res.quoteNo()).isEqualTo(QUOTE_NO);
        assertThat(res.companyName()).isEqualTo(COMPANY_NAME);
        assertThat(res.companyBusinessNo()).isEqualTo(BUSINESS_NO);
        assertThat(res.assignee().name()).isEqualTo("김담당");
        assertThat(res.assignee().email()).isEqualTo("manager@hanbit.co.kr");
        assertThat(res.vatMode()).isEqualTo("EXCLUDED");
        assertThat(res.totalAmount()).isEqualTo(3_355_000L);
    }

    // ─────────────── 승인 ───────────────

    @Test
    @DisplayName("승인 해피패스 — markViewed → approve → 알림 순서, 토큰은 RESPONDED로 소진")
    void 승인_해피패스_순서와_토큰_소진() {
        QuoteViewToken token = activeToken();
        givenToken(token);
        givenView(view("VIEWED"));
        givenCompany(true);
        given(messages.quoteApproved(QUOTE_NO, "박지훈")).willReturn("APPROVE_MSG");

        service.approve("raw", new ApproveQuoteRequest("박지훈", "팀장"), NOW);

        InOrder order = inOrder(quoteCommand, notificationCommand);
        order.verify(quoteCommand).markViewed(QUOTE_ID);
        order.verify(quoteCommand).approve(eq(QUOTE_ID), any(QuoteCommand.Responder.class));
        order.verify(notificationCommand).notifyForDeal(
                NotificationType.QUOTE_APPROVED, COMPANY_ID, DEAL_ID, "APPROVE_MSG", QUOTE_ID);
        assertThat(token.getStatus()).isEqualTo(QuoteViewToken.Status.RESPONDED);
    }

    @Test
    @DisplayName("승인 — 응답자 이름·직책을 C에 그대로 전달한다")
    void 승인_응답자_전달() {
        givenToken(activeToken());
        givenView(view("VIEWED"));
        givenCompany(true);
        given(messages.quoteApproved(QUOTE_NO, "박지훈")).willReturn("APPROVE_MSG");

        service.approve("raw", new ApproveQuoteRequest("박지훈", "팀장"), NOW);

        ArgumentCaptor<QuoteCommand.Responder> captor =
                ArgumentCaptor.forClass(QuoteCommand.Responder.class);
        verify(quoteCommand).approve(eq(QUOTE_ID), captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("박지훈");
        assertThat(captor.getValue().title()).isEqualTo("팀장");
    }

    @Test
    @DisplayName("승인 — pre-state가 SENT면 NT-03(열람)과 NT-04(승인)를 모두 발사한다")
    void 승인_pre_state_SENT면_NT03과_NT04_모두() {
        givenToken(activeToken());
        givenView(view("SENT"));
        givenCompany(true);
        given(messages.quoteViewed(QUOTE_NO)).willReturn("VIEW_MSG");
        given(messages.quoteApproved(QUOTE_NO, "박지훈")).willReturn("APPROVE_MSG");

        service.approve("raw", new ApproveQuoteRequest("박지훈", null), NOW);

        verify(notificationCommand).notifyForDeal(
                NotificationType.QUOTE_VIEWED, COMPANY_ID, DEAL_ID, "VIEW_MSG", QUOTE_ID);
        verify(notificationCommand).notifyForDeal(
                NotificationType.QUOTE_APPROVED, COMPANY_ID, DEAL_ID, "APPROVE_MSG", QUOTE_ID);
    }

    @Test
    @DisplayName("승인 — pre-state가 VIEWED면 NT-04만 발사한다")
    void 승인_pre_state_VIEWED면_NT04만() {
        givenToken(activeToken());
        givenView(view("VIEWED"));
        givenCompany(true);
        given(messages.quoteApproved(QUOTE_NO, "박지훈")).willReturn("APPROVE_MSG");

        service.approve("raw", new ApproveQuoteRequest("박지훈", null), NOW);

        verify(notificationCommand, never())
                .notifyForDeal(eq(NotificationType.QUOTE_VIEWED), any(), any(), any(), any());
        verify(notificationCommand).notifyForDeal(
                NotificationType.QUOTE_APPROVED, COMPANY_ID, DEAL_ID, "APPROVE_MSG", QUOTE_ID);
    }

    @Test
    @DisplayName("RESPONDED 링크 승인이면 409 LINK_ALREADY_RESPONDED, C는 부르지 않는다")
    void RESPONDED_링크_승인이면_409() {
        givenToken(respondedToken());

        assertThatThrownBy(() -> service.approve("raw", new ApproveQuoteRequest("박지훈", null), NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LINK_ALREADY_RESPONDED));
        verifyNoInteractions(quoteCommand);
        verifyNoInteractions(quoteQuery);
    }

    @Test
    @DisplayName("정지 회사 승인이면 409 COMPANY_SUSPENDED, C는 부르지 않는다")
    void 정지_회사_승인이면_409() {
        givenToken(activeToken());
        givenView(view("VIEWED"));
        givenCompany(false);

        assertThatThrownBy(() -> service.approve("raw", new ApproveQuoteRequest("박지훈", null), NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMPANY_SUSPENDED));
        verifyNoInteractions(quoteCommand);
    }

    @Test
    @DisplayName("C가 QUOTE_NOT_RESPONDABLE을 던지면 전파하고 토큰은 소진되지 않는다")
    void C가_QUOTE_NOT_RESPONDABLE_던지면_전파_토큰_유지() {
        QuoteViewToken token = activeToken();
        givenToken(token);
        givenView(view("VIEWED"));
        givenCompany(true);
        willThrow(new BusinessException(ErrorCode.QUOTE_NOT_RESPONDABLE))
                .given(quoteCommand).approve(eq(QUOTE_ID), any());

        assertThatThrownBy(() -> service.approve("raw", new ApproveQuoteRequest("박지훈", null), NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.QUOTE_NOT_RESPONDABLE));
        assertThat(token.getStatus()).isEqualTo(QuoteViewToken.Status.ACTIVE);
        verify(notificationCommand, never())
                .notifyForDeal(eq(NotificationType.QUOTE_APPROVED), any(), any(), any(), any());
    }

    // ─────────────── 반려 ───────────────

    @Test
    @DisplayName("반려 해피패스 — 사유를 C에 전달하고 NT-04(반려)를 발사, 토큰 소진")
    void 반려_해피패스() {
        QuoteViewToken token = activeToken();
        givenToken(token);
        givenView(view("VIEWED"));
        givenCompany(true);
        given(messages.quoteRejected(QUOTE_NO, "박지훈", "예산 초과")).willReturn("REJECT_MSG");

        service.reject("raw", new RejectQuoteRequest("예산 초과", "박지훈", "팀장"), NOW);

        verify(quoteCommand).reject(eq(QUOTE_ID), eq("예산 초과"), any(QuoteCommand.Responder.class));
        verify(notificationCommand).notifyForDeal(
                NotificationType.QUOTE_REJECTED, COMPANY_ID, DEAL_ID, "REJECT_MSG", QUOTE_ID);
        assertThat(token.getStatus()).isEqualTo(QuoteViewToken.Status.RESPONDED);
    }

    // ─────────────── 문의 ───────────────

    @Test
    @DisplayName("문의 해피패스 — CustomerInquiry 저장 + NT-10(문의 접수) 발사")
    void 문의_해피패스() {
        givenToken(activeToken());
        givenView(view("VIEWED"));
        givenCompany(true);
        given(messages.inquiryReceived(QUOTE_NO, "배송 일정을 알고 싶습니다")).willReturn("INQ_MSG");

        service.createInquiry("raw", new CreateInquiryRequest("배송 일정을 알고 싶습니다"), NOW);

        ArgumentCaptor<CustomerInquiry> captor = ArgumentCaptor.forClass(CustomerInquiry.class);
        verify(customerInquiryRepository).save(captor.capture());
        assertThat(captor.getValue().getQuoteId()).isEqualTo(QUOTE_ID);
        assertThat(captor.getValue().getContent()).isEqualTo("배송 일정을 알고 싶습니다");
        verify(notificationCommand).notifyForDeal(
                NotificationType.INQUIRY_RECEIVED, COMPANY_ID, DEAL_ID, "INQ_MSG", QUOTE_ID);
    }

    @Test
    @DisplayName("정지 회사 문의면 409 COMPANY_SUSPENDED, 저장·알림 안 함")
    void 정지_회사_문의면_409() {
        givenToken(activeToken());
        givenView(view("VIEWED"));
        givenCompany(false);

        assertThatThrownBy(() -> service.createInquiry("raw", new CreateInquiryRequest("문의"), NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.COMPANY_SUSPENDED));
        verifyNoInteractions(customerInquiryRepository);
        verifyNoInteractions(notificationCommand);
    }

    @Test
    @DisplayName("만료 링크 문의면 410 LINK_EXPIRED, 저장·알림 안 함 (열람 못 하면 문의도 못 함)")
    void 만료_링크_문의면_410() {
        givenToken(expiredStatusToken());

        assertThatThrownBy(() -> service.createInquiry("raw", new CreateInquiryRequest("문의"), NOW))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.LINK_EXPIRED));
        verifyNoInteractions(customerInquiryRepository, notificationCommand, quoteQuery);
    }

    @Test
    @DisplayName("문의 — RESPONDED 링크에서도 접수된다 (열람 허용 상태)")
    void 문의_RESPONDED_링크에서도_접수() {
        givenToken(respondedToken());
        givenView(view("APPROVED"));
        givenCompany(true);
        given(messages.inquiryReceived(QUOTE_NO, "추가 문의")).willReturn("INQ_MSG");

        assertThatCode(() -> service.createInquiry("raw", new CreateInquiryRequest("추가 문의"), NOW))
                .doesNotThrowAnyException();
        verify(customerInquiryRepository).save(any(CustomerInquiry.class));
    }

    // ─────────────── 헬퍼 ───────────────

    private void givenToken(QuoteViewToken token) {
        given(quoteViewTokenRepository.findByTokenHash(anyString())).willReturn(Optional.of(token));
    }

    private void givenView(QuoteQuery.PublicQuoteView view) {
        given(quoteQuery.getPublicView(QUOTE_ID)).willReturn(view);
    }

    private void givenCompany(boolean active) {
        given(companyQuery.get(COMPANY_ID))
                .willReturn(new CompanyQuery.CompanySummary(COMPANY_ID, COMPANY_NAME, BUSINESS_NO, active));
    }

    private void givenAssignee() {
        given(dealQuery.assigneeIdOf(DEAL_ID)).willReturn(ASSIGNEE_ID);
        given(memberQuery.getContact(ASSIGNEE_ID)).willReturn(
                new MemberQuery.MemberContact("김담당", "manager@hanbit.co.kr", "010-1234-5678"));
    }

    private static QuoteViewToken activeToken() {
        return QuoteViewToken.issue(QUOTE_ID, CONTACT_ID, "hash", NOW.plusSeconds(3600));
    }

    private static QuoteViewToken respondedToken() {
        QuoteViewToken token = activeToken();
        token.respond();
        return token;
    }

    private static QuoteViewToken expiredStatusToken() {
        QuoteViewToken token = activeToken();
        token.expire(QuoteViewToken.ExpiredReason.MANUAL);
        return token;
    }

    private static QuoteViewToken timePassedToken() {
        return QuoteViewToken.issue(QUOTE_ID, CONTACT_ID, "hash", NOW.minusSeconds(10));
    }

    private static QuoteQuery.PublicQuoteView view(String status) {
        return view(status, List.of(
                new QuoteQuery.PublicQuoteView.Item("에어컨", "대", 2, 500_000L, 1_000_000L, 0)));
    }

    private static QuoteQuery.PublicQuoteView view(String status,
                                                  List<QuoteQuery.PublicQuoteView.Item> items) {
        return new QuoteQuery.PublicQuoteView(QUOTE_ID, QUOTE_NO, status, "EXCLUDED",
                "설치는 납품일로부터 3일 이내", LocalDate.of(2026, 9, 20),
                3_050_000L, 305_000L, 3_355_000L, items, DEAL_ID, COMPANY_ID);
    }
}
