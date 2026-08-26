package com.twojo.quote.entity;

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
}
