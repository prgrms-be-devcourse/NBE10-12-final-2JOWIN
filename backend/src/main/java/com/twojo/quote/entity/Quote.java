package com.twojo.quote.entity;

import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 견적 — 7상태 (전이표 §6). 발송 후 불변 (QT-16) · 금액 3분리는 항상 서버 계산 (QT-08·22).
 * 상태 변경 주체는 항상 이 모듈 — D는 QuoteCommand(markViewed·approve·reject)만 호출한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quote extends BaseTimeEntity {

    public enum Status { DRAFT, SENT, VIEWED, APPROVED, REJECTED, WITHDRAWN, EXPIRED }

    public enum VatMode { EXCLUDED, INCLUDED }   // 기본 EXCLUDED (Q-16)

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID companyId;

    private UUID dealId;

    private String quoteNo;   // document_sequence 채번 — UNIQUE(company_id, quote_no)

    @Enumerated(EnumType.STRING)
    private Status status;

    @Enumerated(EnumType.STRING)
    private VatMode vatMode;

    private Long supplyAmount;

    private Long vatAmount;

    private Long totalAmount;

    private LocalDate validUntil;   // = 링크 만료 (Q-17)

    @Column(columnDefinition = "text")
    private String terms;   // QT-10

    private UUID clonedFromQuoteId;   // 복제 계보 (Q-18) · QT-28 대체 이동

    private Instant sentAt;

    private Instant firstViewedAt;   // AP-07

    private Instant respondedAt;

    private String rejectReason;   // AP-10

    private String responderName;    // AP-19 — 자기 신고, 검증 없음 (Q-44). 응답 전 NULL

    private String responderTitle;

    @Version
    private Integer version;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<QuoteItem> items = new ArrayList<>();   // DRAFT PUT 전체 갱신 시 교체 (하드 삭제)

    /**
     * 작성 시작 — DRAFT 생성 (QT-01). 부가세 기본값은 별도 (Q-16).
     *
     * <p><b>번호는 받아서 가진다 — 엔티티가 채번하지 않는다.</b> 채번은 카운터 행에 배타 락을
     * 걸어야 해서 트랜잭션과 리포지토리가 필요한데, 엔티티가 그걸 주입받으면 이 클래스의
     * 단위 테스트에 DB가 딸려온다. 발급은 서비스가 하고({@code DocumentNumberService}),
     * 여기서는 받은 값을 검증만 한다.
     */
    public static Quote draft(UUID companyId, UUID dealId, String quoteNo, LocalDate validUntil) {
        if (quoteNo == null || quoteNo.isBlank()) {
            throw new IllegalArgumentException("견적 번호 없이 견적을 만들 수 없습니다.");
        }
        if (validUntil == null) {
            throw new IllegalArgumentException("유효기간 없이 견적을 만들 수 없습니다.");   // quote.valid_until NOT NULL
        }
        Quote quote = new Quote();
        quote.companyId = companyId;
        quote.dealId = dealId;
        quote.quoteNo = quoteNo;
        quote.validUntil = validUntil;
        quote.status = Status.DRAFT;
        quote.vatMode = VatMode.EXCLUDED;
        quote.applyAmounts(QuoteAmounts.of(0L));
        return quote;
    }

    /**
     * 복제 (QT-19) — 원본을 새 작성 중(DRAFT) 견적으로 베낀다. <b>원본은 그대로다</b> (전이표 §6).
     *
     * <p><b>원본의 상태를 보지 않는다.</b> 전이표가 "모든 상태 → 복제"로 적고 있다 —
     * 반려된 견적을 고쳐 다시 보내는 것이 이 기능의 주 용도이고(Q-18), 회수·만료된 건도
     * 재제안 출발점이 된다(DL-12 재개 시 "재제안은 복제로"). 막는 축은 <b>종결 Deal 하나뿐</b>이고
     * 그 판정은 서비스가 한다 (Q-25 — 딜 상태는 이 엔티티가 모른다).
     *
     * <p><b>베끼는 것과 베끼지 않는 것</b>:
     * <ul>
     *   <li>베낀다 — 항목(단가·수량·순서·카탈로그 단가 스냅샷)·{@code vatMode}·{@code terms}.
     *       다시 보내려고 만드는 견적이라 내용이 같아야 손이 덜 간다</li>
     *   <li>베끼지 않는다 — 번호·유효기간·발송/열람/응답 이력·반려 사유·응답자.
     *       번호와 유효기간은 서비스가 새로 정하고(채번·오늘 기준), 나머지는
     *       <b>원본에게 일어난 일</b>이라 새 견적의 사실이 아니다</li>
     * </ul>
     *
     * <p><b>계보는 한 단계만 기록한다</b> — {@code clonedFromQuoteId}는 직전 원본이다.
     * 복제의 복제면 그 중간 견적을 가리킨다. 뿌리까지 거슬러 두면 "무엇을 고쳐 다시 보냈나"라는
     * 이 필드의 쓰임과 어긋난다.
     *
     * @param quoteNo    서비스가 채번한 새 번호 ({@link #draft}와 같은 이유로 엔티티가 만들지 않는다)
     * @param validUntil 서비스가 오늘 기준으로 새로 정한 유효기간 — 원본 것은 이미 낡았을 수 있다
     */
    public Quote cloneAsDraft(String quoteNo, LocalDate validUntil) {
        Quote copy = draft(companyId, dealId, quoteNo, validUntil);
        copy.vatMode = this.vatMode;
        copy.terms = this.terms;
        copy.clonedFromQuoteId = this.id;
        copy.replaceItems(items.stream()
                .map(item -> QuoteItem.of(item.getProductId(), item.getName(), item.getUnit(),
                        item.getQuantity(), item.getUnitPrice(),
                        item.getCatalogPriceAtCreation(), item.getSortOrder()))
                .toList());
        return copy;
    }

    /**
     * 작성 중 본문 갱신 (QT-09·10·23) — 항목은 {@link #replaceItems}가 따로 맡는다.
     *
     * <p><b>PUT이므로 전부 덮어쓴다.</b> {@code terms}에 null이 오면 조건 문구를 지우는 것이다 —
     * 08의 {@code UpdateQuoteRequest}가 부분 수정이 아니라 전체 갱신이라 null이 "미변경"이 아니다.
     */
    public void update(LocalDate validUntil, VatMode vatMode, String terms) {
        requireDraft();
        this.validUntil = validUntil;
        this.vatMode = vatMode;   // 금액에 영향 없음 (Q-46)
        this.terms = terms;
    }

    /**
     * 요청이 들고 온 version이 지금 값과 같은지 본다 — <b>낡은 화면</b>을 잡는 검사다 (Q-38).
     *
     * <p>둘이 같은 version을 읽고 <b>동시에</b> 커밋하는 경우는 여기서 안 걸린다.
     * 그건 JPA {@code @Version}이 flush 시점에 잡고, 진 쪽은
     * {@code ObjectOptimisticLockingFailureException}으로 같은 409 STALE_VERSION이 된다.
     * Deal의 {@code checkVersion}과 같은 구조다 (#61).
     */
    public void checkVersion(Integer expected) {
        if (expected != null && !expected.equals(this.version)) {
            throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    /**
     * 작성 중 항목 전체 교체 (QT-02~07) — 기존 항목은 하드 삭제된다.
     * <p>교체 후 금액을 재계산한다. 발송된 견적은 불변이다 (QT-14·16).
     */
    public void replaceItems(List<QuoteItem> newItems) {
        requireDraft();
        items.clear();
        newItems.forEach(item -> {
            item.assignTo(this);
            items.add(item);
        });
        recalculateAmounts();
    }

    /**
     * 부가세 별도/포함 지정 (QT-23) — <b>금액은 변하지 않는다</b> (Q-46).
     *
     * <p>{@code vat_mode}는 견적서에 어떻게 표시할지를 정하는 플래그이고, 단가는 모드와 무관하게
     * 항상 세전이다. 그래서 재계산할 것이 없다 — 근거는 {@link QuoteAmounts} javadoc에 있다.
     */
    public void changeVatMode(VatMode newVatMode) {
        requireDraft();
        this.vatMode = newVatMode;
    }

    /**
     * 고객 첫 열람 (AP-02·07) — 발송됨(SENT) → 열람됨(VIEWED), 첫 열람 시각을 남긴다.
     *
     * <p><b>멱등이다.</b> SENT가 아니면 아무 일도 하지 않는다 — 예외를 던지지 않는다.
     * <ul>
     *   <li>이미 VIEWED — 두 번째 열람이다. {@code firstViewedAt}을 덮으면 "<b>첫</b> 열람 시각"이라는
     *       값의 의미가 사라진다 (AP-07)</li>
     *   <li>응답 완료(APPROVED·REJECTED) — 응답한 링크의 <b>열람은 허용</b>이다.
     *       차단되는 것은 재응답뿐이다 (전이표 §7, v1.6.1)</li>
     * </ul>
     *
     * <p>열람은 고객이 링크를 여는 것뿐이라 실패할 이유가 없다. 여기서 예외를 던지면
     * 정상적인 재열람이 오류 화면이 된다.
     */
    public void markViewed(Instant viewedAt) {
        if (status != Status.SENT) {
            return;
        }
        this.status = Status.VIEWED;
        this.firstViewedAt = viewedAt;
    }

    /**
     * 고객 승인 (AP-08·19) — 열람됨(VIEWED) → 승인됨(APPROVED).
     *
     * <p>응답자 이름·직책은 <b>검증하지 않는다</b>. 계정 없는 고객이 직접 밝히는 자기 신고이고
     * (Q-44), 시스템이 확인할 방법이 없다는 사실을 화면이 안내한다. 길이 제한은 08의
     * {@code @Size(max = 50)}가 웹 계층에서 건다.
     */
    public void approve(String responderName, String responderTitle, Instant respondedAt) {
        requireRespondable();
        this.status = Status.APPROVED;
        recordResponder(responderName, responderTitle, respondedAt);
    }

    /**
     * 고객 반려 (AP-09·10·19) — 열람됨(VIEWED) → 반려됨(REJECTED). 종결이다.
     *
     * <p>재제안은 복제(QT-19)로 새 견적을 만든다 — 반려된 견적은 되살아나지 않는다 (전이표 §6).
     */
    public void reject(String reason, String responderName, String responderTitle, Instant respondedAt) {
        requireRespondable();
        this.status = Status.REJECTED;
        this.rejectReason = reason;
        recordResponder(responderName, responderTitle, respondedAt);
    }

    private void recordResponder(String responderName, String responderTitle, Instant respondedAt) {
        this.responderName = responderName;
        this.responderTitle = responderTitle;
        this.respondedAt = respondedAt;
    }

    /**
     * 승인·반려가 열리는 상태 — <b>열람됨(VIEWED)뿐이다</b> (전이표 §6).
     *
     * <p>SENT에서 바로 승인하는 행은 표에 없다. 고객이 링크를 열면 열람 API가
     * {@code markViewed}를 먼저 부르므로 정상 흐름에 그 경로가 없고, 온다면 호출 순서가
     * 뒤바뀐 것이라 드러나는 편이 낫다.
     *
     * <p>만료·회수·이미 응답한 견적도 여기서 막힌다. 다만 <b>링크 상태로 걸러지는 것들</b>
     * (만료 링크 410 · 응답 완료 링크 409)은 열람 API가 먼저 잡으므로, 여기 닿는 것은
     * 링크는 멀쩡한데 견적 상태가 어긋난 경우다.
     */
    private void requireRespondable() {
        if (status != Status.VIEWED) {
            throw new BusinessException(ErrorCode.QUOTE_NOT_RESPONDABLE);
        }
    }

    /**
     * 주문 전환이 열리는 상태 — <b>승인됨(APPROVED)뿐이다</b> (OD-02, 전이표 §5).
     *
     * <p>발송·열람 중인 견적은 고객이 아직 동의하지 않았고, 반려·회수·만료는 합의가 없다.
     * 작성 중은 고객이 본 적조차 없다. <b>주문은 되돌릴 수 없다</b> — v1에 취소가 없어
     * (OD-11·12 제외, Q-09) 잘못 만든 주문을 지울 방법이 없으므로 여기서 좁게 연다.
     */
    public void requireApproved() {
        if (status != Status.APPROVED) {
            throw new BusinessException(ErrorCode.QUOTE_NOT_APPROVED);
        }
    }

    /**
     * 발송 가능한지 검사한다 (QT-14~16, Q-17) — <b>상태는 바꾸지 않는다.</b>
     *
     * <p>여기서 보는 것은 <b>견적 자체의 조건</b> 셋뿐이다.
     * <ul>
     *   <li>작성 중(DRAFT)이어야 한다 (QT-14·16)</li>
     *   <li><b>항목이 1개 이상</b>이어야 한다 (QT-15) — 빈 견적은 보낼 것이 없다</li>
     *   <li><b>유효기간이 지나지 않았어야</b> 한다 (Q-17) — 입력은 {@code @Future}가 막지만
     *       <b>저장된 값이 낡는 것은 못 막는다.</b> 오늘 만든 견적을 다음 주에 보내면
     *       이미 만료된 링크가 나간다</li>
     * </ul>
     *
     * <p><b>전이({@link #markSent})와 나눈 이유는 순서 합의다.</b>
     * {@code ViewTokenCommand.issue}는 "issue 시점의 status는 아직 DRAFT"를 계약으로 두고 있다
     * (Q-40) — 링크를 먼저 발급하고 그 다음 SENT로 바꾼다. 그런데 검증까지 뒤로 미루면
     * <b>빈 견적에 링크를 발급한 뒤에야 실패</b>하게 된다. 그래서 검증만 앞으로 뺀다.
     *
     * <p><b>종결 Deal 차단(Q-25)·수신인 검증은 여기서 하지 않는다.</b> 둘 다 견적 밖의
     * 사실을 물어야 해서 서비스가 경계 계약으로 판정한다 — 엔티티가 그걸 알면 테스트에
     * DB가 딸려온다.
     */
    public void requireSendable(LocalDate today) {
        requireDraft();
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.QUOTE_EMPTY_ITEMS);   // QT-15
        }
        if (validUntil.isBefore(today)) {
            throw new BusinessException(ErrorCode.QUOTE_VALID_UNTIL_PASSED);   // Q-17
        }
    }

    /**
     * 발송 확정 (QT-13) — 작성 중(DRAFT) → 발송됨(SENT), 발송 시각을 남긴다.
     *
     * <p><b>{@link #requireSendable}을 먼저 부른 뒤</b> 링크 발급이 성공하면 호출한다.
     * 여기서 {@code requireDraft}를 다시 보는 것은 순서를 건너뛴 호출을 막기 위한 것이지
     * 검증을 대신하려는 것이 아니다 — 항목·유효기간은 앞에서 이미 봤다.
     */
    public void markSent(Instant sentAt) {
        requireDraft();
        this.status = Status.SENT;
        this.sentAt = sentAt;
    }

    /**
     * 회수 (QT-17) — 발송됨·열람됨 → 회수됨(WITHDRAWN). <b>종결이다.</b>
     *
     * <p>링크 즉시 만료는 서비스가 {@code ViewTokenCommand.expire(WITHDRAWN)}으로 처리한다
     * (전이표 §6의 효과).
     *
     * <p><b>종결 Deal에서도 회수된다</b> — 발송과 반대다. 발송은 새 약속을 만드는 행위라
     * 끝난 딜에서 할 일이 아니지만, 회수는 이미 나간 링크를 닫는 뒷정리라 오히려
     * 종결 딜에서 필요하다 (07 §C "종결 Deal에서도 가능 — 정리 목적").
     */
    public void withdraw() {
        if (status != Status.SENT && status != Status.VIEWED) {
            throw new BusinessException(ErrorCode.QUOTE_NOT_WITHDRAWABLE);
        }
        this.status = Status.WITHDRAWN;
    }

    /**
     * 수신인을 바꿔 다시 보낼 수 있는지 (AP-13) — <b>발송됨·열람됨에서만</b>.
     *
     * <p><b>판정 축이 링크가 아니라 견적 상태다</b> ({@code QUOTE_NOT_RESENDABLE} 주석).
     * 수동 만료(AP-14)로 링크를 닫은 뒤 다른 수신인에게 다시 보내는 흐름을 막지 않으려는 것이다 —
     * 링크가 없다는 것이 재발송 불가의 이유가 되면 그 흐름이 죽는다.
     *
     * <p><b>종결 Deal 여부를 따로 보지 않는다.</b> 실패(LOST)한 딜의 진행 중 견적은 이미
     * 기간 만료(EXPIRED)로 닫혀 있고(전이표 §5의 효과), 성사(WON)한 딜의 발송된 견적은
     * 끝까지 유효하다(Q-25). 두 경우 모두 <b>견적 상태만 보면 답이 나온다.</b>
     */
    public void requireResendable() {
        if (status != Status.SENT && status != Status.VIEWED) {
            throw new BusinessException(ErrorCode.QUOTE_NOT_RESENDABLE);
        }
    }

    /**
     * 금액 3분리 재계산 (QT-08·22) — 항목 합계가 계산의 <b>유일한</b> 입력이다.
     * <p>단가가 항상 세전이므로 항목 합계가 곧 공급가액이다 (Q-46).
     */
    private void recalculateAmounts() {
        long itemsTotal = items.stream()
                .mapToLong(QuoteItem::getAmount)
                .reduce(0L, Math::addExact);
        applyAmounts(QuoteAmounts.of(itemsTotal));
    }

    private void applyAmounts(QuoteAmounts amounts) {
        this.supplyAmount = amounts.supplyAmount();
        this.vatAmount = amounts.vatAmount();
        this.totalAmount = amounts.totalAmount();
    }

    private void requireDraft() {
        if (status != Status.DRAFT) {
            throw new BusinessException(ErrorCode.QUOTE_NOT_DRAFT);   // QT-14·16, 전이표 §6
        }
    }
}
