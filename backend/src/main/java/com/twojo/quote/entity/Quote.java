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
