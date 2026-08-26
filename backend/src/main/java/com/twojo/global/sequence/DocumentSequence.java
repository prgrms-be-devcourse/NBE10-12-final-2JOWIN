package com.twojo.global.sequence;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 표시 번호 채번 (공통·E) — {@code SELECT ... FOR UPDATE} → last_seq+1 → 번호 조립.
 * 행 없으면 INSERT. quote_no·order_no의 UNIQUE(company_id, no)가 최종 방어선.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentSequence extends BaseTimeEntity {

    public enum DocType { QUOTE, ORDER }   // APPLICATION은 제외 (v1.6 — 신청은 id 식별)

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID companyId;

    @Enumerated(EnumType.STRING)
    private DocType docType;

    private String yearMonth;   // 월별 리셋 (예: "2608")

    private int lastSeq;
}
