package com.twojo.deal.entity;

import com.twojo.global.error.BusinessException;
import com.twojo.global.error.ErrorCode;
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
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

    /**
     * 진행 중 단계 — 리드~협상 (전이표 §5).
     * <p>견적 작성·발송·복제 가능 여부(Q-25), 고객사 삭제 차단(CU-08), 주문 전환 시 자동 성사가
     * 전부 이 집합을 기준으로 갈린다. 종결은 성사(WON)·실패(LOST) 둘뿐이다.
     */
    public static final Set<Stage> OPEN_STAGES =
            Collections.unmodifiableSet(EnumSet.of(Stage.LEAD, Stage.CONSULT, Stage.QUOTE, Stage.NEGOTIATION));

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

    /**
     * Deal 생성 (DL-01~04) — 항상 리드(LEAD)에서 시작한다 (전이표 §5, 단계는 전사 고정 Q-11).
     *
     * <p>참조 ID가 같은 회사 소속인지는 서비스가 검증한다 (검증 노트 #3) — 엔티티는 값만 담는다.
     *
     * @param assigneeMemberId 배정 대상. null이면 생성자 본인을 서비스가 채워 넘긴다 (08 §C)
     * @param expectedAmount   null 허용 — 미정 상태로 둘 수 있다 (DL-02)
     * @param dueDate          null 허용 (DL-03)
     */
    public static Deal create(UUID companyId, UUID customerId, UUID assigneeMemberId,
                              String title, Long expectedAmount, LocalDate dueDate) {
        Deal deal = new Deal();
        deal.companyId = companyId;
        deal.customerId = customerId;
        deal.assigneeMemberId = assigneeMemberId;
        deal.title = title;
        deal.stage = Stage.LEAD;
        deal.expectedAmount = expectedAmount;
        deal.dueDate = dueDate;
        return deal;
    }

    /**
     * 제목·예상 금액·마감일 수정 (DL-02·03) — <b>null은 "변경하지 않음"이다.</b>
     *
     * <p>08의 B 도메인 record가 같은 규칙을 쓴다(PATCH: null 필드는 미변경).
     * 그래서 값을 "미정"으로 되돌리는 경로는 없다 — v1에서는 지원하지 않는다.
     */
    public void update(String newTitle, Long newExpectedAmount, LocalDate newDueDate) {
        if (newTitle != null) {
            this.title = newTitle;
        }
        if (newExpectedAmount != null) {
            this.expectedAmount = newExpectedAmount;
        }
        if (newDueDate != null) {
            this.dueDate = newDueDate;
        }
    }

    /** 담당자 변경 (DL-05, SC-06) — 같은 회사의 활성 구성원인지는 서비스가 검증한다 */
    public void changeAssignee(UUID newAssigneeMemberId) {
        this.assigneeMemberId = newAssigneeMemberId;
    }

    /** 소프트 삭제 (DL-16, §1.5) — 견적 연결 여부(DL-17)는 서비스가 먼저 판정한다 */
    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    /**
     * 낙관적 락 검증 (검증 노트 #4) — 수정·전이 요청이 들고 온 version과 대조한다.
     *
     * <p>JPA {@code @Version}은 flush 시점에 <b>동시 쓰기</b>를 잡지만,
     * "내가 보던 화면이 그 사이 바뀌었는가"는 잡지 못한다. 그 판정이 여기다.
     */
    public void checkVersion(Integer expected) {
        if (!java.util.Objects.equals(this.version, expected)) {
            throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    /** 진행 중(리드~협상) 여부 — 종결 Deal에서 막히는 규칙들의 공통 판정 (Q-25, CU-08) */
    public boolean isOpen() {
        return OPEN_STAGES.contains(stage);
    }
}
