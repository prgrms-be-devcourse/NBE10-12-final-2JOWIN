package com.twojo.approval.service;

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
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 고객 견적 열람·승인·반려·문의 (public — 토큰이 곧 인증, SC-07~09). {@code PublicQuoteController}가 주입한다.
 *
 * <p><b>트랜잭션 경계</b>
 * <ul>
 *   <li>{@link #view} — <b>무트랜잭션</b>. 조립용 4개 boundary 조회는 각 impl이 자체 readOnly 트랜잭션을
 *       잡았다 놓는다(커넥션 장기 점유 회피). 첫 열람 부수효과만 {@link FirstViewRecorder}의 별
 *       read-write 트랜잭션으로 분리하고, 실패는 삼켜 조회가 500이 되지 않게 한다.</li>
 *   <li>{@link #approve}·{@link #reject}·{@link #createInquiry} — 단일 {@code @Transactional}.
 *       markViewed + 상태 전이 + {@code token.respond()} + 알림이 한 원자 단위다(docs/11 §D).</li>
 * </ul>
 *
 * <p><b>경계 합의</b> — 견적 상태는 {@link QuoteCommand}만 호출해 바꾼다(주체는 C). 링크 상태(404/410/409),
 * 회사 정지(409), DRAFT/WITHDRAWN 조기 404는 D가 C 호출 전에 거른다(PR #101·#136 코멘트).
 */
@Service
@RequiredArgsConstructor
public class CustomerQuoteService {

    private static final Logger log = LoggerFactory.getLogger(CustomerQuoteService.class);

    private final QuoteViewTokenRepository quoteViewTokenRepository;
    private final TokenGenerator tokenGenerator;
    private final QuoteQuery quoteQuery;
    private final QuoteCommand quoteCommand;
    private final CompanyQuery companyQuery;
    private final DealQuery dealQuery;
    private final MemberQuery memberQuery;
    private final NotificationCommand notificationCommand;
    private final CustomerInquiryRepository customerInquiryRepository;
    private final CustomerNotificationMessages messages;
    private final FirstViewRecorder firstViewRecorder;

    /** 열람 조회 (AP-02·07·18). 무트랜잭션 — 첫 열람 부수효과만 {@link FirstViewRecorder}로 분리한다. */
    public PublicQuoteResponse view(String rawToken, Instant now) {
        QuoteViewToken token = resolve(rawToken, now);
        QuoteQuery.PublicQuoteView view = loadView(token.getQuoteId());
        CompanyQuery.CompanySummary company = companyQuery.get(view.companyId());
        MemberQuery.MemberContact assignee = memberQuery.getContact(dealQuery.assigneeIdOf(view.dealId()));

        String status = view.status();
        if ("SENT".equals(status)) {
            try {
                firstViewRecorder.recordFirstView(view);   // 별 read-write 트랜잭션
                status = "VIEWED";                          // 방금 전이시킨 상태를 응답에 반영
            } catch (RuntimeException e) {
                // 계약(Quote.markViewed = 비-SENT면 무동작)을 지키면 여기 안 온다. 오면 C markViewed 회귀 의심.
                log.error("첫 열람 부수효과 실패 - 열람 응답은 그대로 반환. quoteId={}, {}",
                        view.quoteId(), e.toString());
            }
        }

        boolean respondable = company.active()
                && token.isRespondable(now)
                && ("SENT".equals(status) || "VIEWED".equals(status));
        return assemble(view, company, assignee, respondable, status);
    }

    /** 승인 (AP-08·19). 토큰 소진과 한 트랜잭션. */
    @Transactional
    public void approve(String rawToken, ApproveQuoteRequest request, Instant now) {
        Preflight pf = preRespond(rawToken, now);
        QuoteQuery.PublicQuoteView view = pf.view();
        quoteCommand.approve(view.quoteId(),
                new QuoteCommand.Responder(request.responderName(), request.responderTitle()));
        pf.token().respond();
        notificationCommand.notifyForDeal(NotificationType.QUOTE_APPROVED, view.companyId(),
                view.dealId(), messages.quoteApproved(view.quoteNo(), request.responderName()),
                view.quoteId());
    }

    /** 반려 (AP-09·10·19). 토큰 소진과 한 트랜잭션. */
    @Transactional
    public void reject(String rawToken, RejectQuoteRequest request, Instant now) {
        Preflight pf = preRespond(rawToken, now);
        QuoteQuery.PublicQuoteView view = pf.view();
        quoteCommand.reject(view.quoteId(), request.reason(),
                new QuoteCommand.Responder(request.responderName(), request.responderTitle()));
        pf.token().respond();
        notificationCommand.notifyForDeal(NotificationType.QUOTE_REJECTED, view.companyId(),
                view.dealId(),
                messages.quoteRejected(view.quoteNo(), request.responderName(), request.reason()),
                view.quoteId());
    }

    /** 문의 등록 (AP-15, Q-20). 정지 회사는 차단 — 고객 링크는 열람만 (07 §D). */
    @Transactional
    public void createInquiry(String rawToken, CreateInquiryRequest request, Instant now) {
        QuoteViewToken token = resolve(rawToken, now);
        QuoteQuery.PublicQuoteView view = loadView(token.getQuoteId());
        requireCompanyActive(view.companyId());
        customerInquiryRepository.save(CustomerInquiry.of(view.quoteId(), request.content()));
        notificationCommand.notifyForDeal(NotificationType.INQUIRY_RECEIVED, view.companyId(),
                view.dealId(), messages.inquiryReceived(view.quoteNo(), request.content()),
                view.quoteId());
    }

    // ───────────────────────────── 공통 ─────────────────────────────

    /** 해시 조회 실패 404 / {@code !isViewable} 410. {@code token.expire()}는 부르지 않는다(만료 배치는 C, Q-37). */
    private QuoteViewToken resolve(String rawToken, Instant now) {
        QuoteViewToken token = quoteViewTokenRepository.findByTokenHash(tokenGenerator.hash(rawToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!token.isViewable(now)) {
            throw new BusinessException(ErrorCode.LINK_EXPIRED);
        }
        return token;
    }

    /** {@code getPublicView} + DRAFT/WITHDRAWN 조기 404 (4개 EP 일관, C 모듈 도달 전에 튕김 — SC-09). */
    private QuoteQuery.PublicQuoteView loadView(UUID quoteId) {
        QuoteQuery.PublicQuoteView view = quoteQuery.getPublicView(quoteId);
        if ("DRAFT".equals(view.status()) || "WITHDRAWN".equals(view.status())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return view;
    }

    private void requireCompanyActive(UUID companyId) {
        if (!companyQuery.get(companyId).active()) {
            throw new BusinessException(ErrorCode.COMPANY_SUSPENDED);
        }
    }

    /** 승인·반려 공통 전처리 — 링크·회사를 검증하고 markViewed(+첫 열람 NT-03)까지 끝낸다. */
    private Preflight preRespond(String rawToken, Instant now) {
        QuoteViewToken token = resolve(rawToken, now);
        if (token.getStatus() == QuoteViewToken.Status.RESPONDED) {
            throw new BusinessException(ErrorCode.LINK_ALREADY_RESPONDED);   // 재응답 차단 (AP-11)
        }
        QuoteQuery.PublicQuoteView view = loadView(token.getQuoteId());
        requireCompanyActive(view.companyId());

        boolean firstView = "SENT".equals(view.status());
        quoteCommand.markViewed(view.quoteId());   // 무조건 선행 (전이표 §6 — VIEWED에서만 응답). 비-SENT면 C가 무동작
        if (firstView) {
            // GET 경로와 일관 (PR #101·#136). 동시 GET과 같은 SENT 스냅샷을 보면 NT-03이 겹칠 수 있다 —
            // FirstViewRecorder javadoc의 "동시 첫 열람 NT-03 중복" 한계(B-9)와 같은 레이스, v1 허용.
            notificationCommand.notifyForDeal(NotificationType.QUOTE_VIEWED, view.companyId(),
                    view.dealId(), messages.quoteViewed(view.quoteNo()), view.quoteId());
        }
        return new Preflight(token, view);
    }

    private PublicQuoteResponse assemble(QuoteQuery.PublicQuoteView view,
                                        CompanyQuery.CompanySummary company,
                                        MemberQuery.MemberContact assignee,
                                        boolean respondable, String status) {
        List<PublicQuoteResponse.ItemView> items = view.items().stream()
                .sorted(Comparator.comparingInt(QuoteQuery.PublicQuoteView.Item::sortOrder))
                .map(i -> new PublicQuoteResponse.ItemView(
                        i.name(), i.unit(), i.quantity(), i.unitPrice(), i.amount()))
                .toList();
        return new PublicQuoteResponse(
                view.quoteNo(), status, company.name(), company.businessNo(),
                new PublicQuoteResponse.AssigneeInfo(assignee.name(), assignee.email(), assignee.phone()),
                view.vatMode(), view.terms(), view.validUntil(),
                view.supplyAmount(), view.vatAmount(), view.totalAmount(), items, respondable);
    }

    private record Preflight(QuoteViewToken token, QuoteQuery.PublicQuoteView view) {}
}
