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
 *
 * <p><b>회사 · 문서종류 · 연월</b>이 카운터 하나를 정한다 — {@code uk_document_sequence}.
 * 번호 조립과 연월 판정은 {@link DocumentNumberService}가 하고, 이 엔티티는 카운터만 소유한다.
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

    /**
     * 다음 번호를 소비한다 — <b>증가는 이 메서드에만 있다</b>.
     *
     * <p>{@code lastSeq}에 setter를 열면 락 없이 올리는 경로가 생기고, 그건 번호 중복으로
     * 이어진다. 호출자는 {@link DocumentSequenceRepository#findForUpdate}로 행 락을 잡은 뒤
     * 부른다 — 이 메서드 자체는 락을 걸지 않는다.
     *
     * @return 이번에 발급된 순번 (1부터)
     */
    int next() {
        return ++lastSeq;
    }
}
