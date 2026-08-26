package com.twojo.deal.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Deal — 고정 6단계 (Q-11). assignee_member_id가 조회 범위의 축 (SC-02).
 * 상태 전이는 전이표 §5의 것만 — 엔티티 메서드로 구현하고 표에 없는 전이는 차단한다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deal extends BaseTimeEntity {

    public enum Stage { LEAD, CONSULT, QUOTE, NEGOTIATION, WON, LOST }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID companyId;

    private UUID customerId;

    private UUID assigneeMemberId;   // SC-02 담당 축 — 견적·주문·할 일의 범위가 여기서 파생

    private String title;

    @Enumerated(EnumType.STRING)
    private Stage stage;

    private Long expectedAmount;   // DL-02 — null 허용(미정). 성사 후 표시는 주문 합계 (DL-18)

    private LocalDate dueDate;

    private String lostReason;

    private String lostFromStage;   // 재개용 (DL-12)

    @Version
    private Integer version;   // 낙관적 락 — 불일치 409 STALE_VERSION

    private Instant deletedAt;
}
