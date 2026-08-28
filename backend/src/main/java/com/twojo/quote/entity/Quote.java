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

    /** 작성 시작 — DRAFT 생성 (QT-01). 부가세 기본값은 별도 (Q-16) */
    public static Quote draft(UUID companyId, UUID dealId) {
        Quote quote = new Quote();
        quote.companyId = companyId;
        quote.dealId = dealId;
        quote.status = Status.DRAFT;
        quote.vatMode = VatMode.EXCLUDED;
        quote.applyAmounts(QuoteAmounts.of(0L, VatMode.EXCLUDED));
        return quote;
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

    /** 부가세 별도/포함 지정 (QT-23) — 변경 시 금액을 재계산한다 */
    public void changeVatMode(VatMode newVatMode) {
        requireDraft();
        this.vatMode = newVatMode;
        recalculateAmounts();
    }

    /**
     * 금액 3분리 재계산 (QT-08·22) — 항목 합계가 계산의 유일한 입력이다.
     * <p>EXCLUDED면 항목 합계가 공급가액, INCLUDED면 세포함 합계로 해석된다.
     */
    private void recalculateAmounts() {
        long itemsTotal = items.stream()
                .mapToLong(QuoteItem::getAmount)
                .reduce(0L, Math::addExact);
        applyAmounts(QuoteAmounts.of(itemsTotal, vatMode));
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
