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
import java.util.Map;
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

    /**
     * 진행 단계의 인접 관계 — 리드 → 상담 → 견적 → 협상 (전이표 §5, DL-07).
     *
     * <p><b>enum 순서로 계산하지 않는다.</b> {@code values()[ordinal + 1]}은 배열 끝의 WON·LOST로
     * 넘어가는 순간 조용히 틀린다 — 종결은 순서가 아니라 성격이 다른 상태이고,
     * 성사는 주문 전환으로만 도달한다(DL-09). 다음 단계가 없는 협상은 이 맵에 키가 없다.
     */
    private static final Map<Stage, Stage> NEXT_STAGE = Map.of(
            Stage.LEAD, Stage.CONSULT,
            Stage.CONSULT, Stage.QUOTE,
            Stage.QUOTE, Stage.NEGOTIATION);

    /** 위 맵의 역방향 — 상담 ~ 협상에서만 되돌릴 수 있다 (DL-08). 리드는 키가 없다 */
    private static final Map<Stage, Stage> PREVIOUS_STAGE = Map.of(
            Stage.CONSULT, Stage.LEAD,
            Stage.QUOTE, Stage.CONSULT,
            Stage.NEGOTIATION, Stage.QUOTE);

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

    /**
     * 다음 단계로 이동 (DL-07) — <b>인접 한 단계씩만</b>이다.
     *
     * <p>협상에서 호출하면 {@code DEAL_WON_REQUIRES_ORDER}다. 성사는 주문 전환이 자동으로만
     * 만드는 상태이고(DL-09), 수동 경로를 열면 "승인된 견적 없이 성사"가 가능해진다.
     */
    public void advance() {
        requireOpen();
        Stage next = NEXT_STAGE.get(stage);
        if (next == null) {   // 협상 — 다음은 성사뿐인데 수동으로 갈 수 없다
            throw new BusinessException(ErrorCode.DEAL_WON_REQUIRES_ORDER);
        }
        this.stage = next;
    }

    /**
     * 견적 발송에 따른 <b>자동 승급</b> (Q-25) — 리드·상담이면 견적(QUOTE)으로 올린다.
     *
     * <p>전이표 §5의 별도 행이다: "리드·상담 → 견적 발송 → 견적(QUOTE) · <b>시스템</b>".
     * {@link #advance}와 세 가지가 다르다.
     * <ul>
     *   <li><b>두 칸을 뛴다</b> — 리드에서 곧장 견적이다. advance를 두 번 부르면 상담을 거친
     *       것처럼 보이고, 타임라인에 없던 단계 이동이 기록된다</li>
     *   <li><b>행위자가 시스템</b>이다 — 담당자의 수동 이동(DL-07)이 아니다</li>
     *   <li><b>이미 견적·협상이면 무동작</b>이다 — 전이표가 "견적 <b>미만이면</b> 승급"으로
     *       규정한다. 협상 딜의 견적을 발송하는 것은 정상이라 예외로 만들면 발송이 막힌다.
     *       협상을 견적으로 <b>내리지도</b> 않는다</li>
     * </ul>
     *
     * <p>종결(WON·LOST) Deal이면 막는다 — 조용히 무동작하면 "종결된 딜에 견적이 발송됐다"는
     * 모순이 아무 흔적 없이 지나간다. 발송 자체를 어디서 막을지는 호출자의 몫이고,
     * 여기서는 그 모순이 도달했을 때 드러나게 한다.
     */
    public void promoteToQuoteStage() {
        requireOpen();
        if (stage == Stage.LEAD || stage == Stage.CONSULT) {
            this.stage = Stage.QUOTE;
        }
    }

    /**
     * 주문 전환에 따른 <b>자동 성사</b> (OD-06) — 진행 중이면 <b>단계와 무관하게</b> 성사(WON)다.
     *
     * <p>전이표 §5: "리드 ~ 협상 → 주문 전환 → 성사(WON) · <b>시스템</b>".
     * {@link #advance}로는 성사에 닿을 수 없다 — 협상에서 호출하면 {@code DEAL_WON_REQUIRES_ORDER}다.
     * <b>승인된 견적 없이 성사될 수 없다</b>는 DL-09를 코드로 강제하는 구조이고,
     * 그 유일한 출구가 여기다.
     *
     * <p><b>이미 성사면 무동작이다 — {@link #promoteToQuoteStage}와 반대다.</b>
     * 발송은 끝난 딜에 <b>새 약속</b>을 만드는 일이라 막아야 하지만, 주문 전환은 이미 성사된 딜에
     * <b>주문을 하나 더</b> 붙이는 일이라 정상 시나리오다 — 성사 전에 발송된 견적은 끝까지 유효하고
     * 두 번째 승인 견적도 전환된다 (Q-25, 07 §C 257행).
     *
     * <p>실패(LOST) 딜은 막는다({@code DEAL_NOT_OPEN}). 실패 처리는 진행 중이던 견적을 기간 만료로
     * 닫으므로(전이표 §5의 효과) 승인 견적이 남아 있을 수 없고, 남아 있다면 그건 표에 없는 상태다.
     */
    public void win() {
        if (stage == Stage.WON) {
            return;   // 멱등 — 주문 추가 생성 (Q-25)
        }
        requireOpen();   // 실패(LOST)는 DEAL_NOT_OPEN
        this.stage = Stage.WON;
    }

    /** 이전 단계로 되돌리기 (DL-08) — 리드에서는 되돌릴 곳이 없다 */
    public void revert() {
        requireOpen();
        Stage previous = PREVIOUS_STAGE.get(stage);
        if (previous == null) {   // 리드 — 이전 단계가 없다
            throw new BusinessException(ErrorCode.DEAL_NO_PREVIOUS_STAGE);
        }
        this.stage = previous;
    }

    /**
     * 실패 처리 (DL-10·11) — 실패 <b>직전 단계</b>를 남겨 재개에 쓴다 (DL-12).
     *
     * <p>진행 중이던 견적과 열람 링크를 만료시키는 부수효과는 서비스가 처리한다 —
     * quote는 다른 모듈이라 엔티티가 건드릴 수 없다 (전이표 §5).
     */
    public void lose(String reason) {
        requireOpen();
        this.lostFromStage = stage.name();
        this.lostReason = reason;
        this.stage = Stage.LOST;
    }

    /**
     * 재개 (DL-12) — 실패 직전 단계로 돌아간다.
     *
     * <p><b>만료된 견적·링크는 복원되지 않는다</b> (전이표 §5). 고객이 이미 만료 안내를 본
     * 링크를 되살리면 "우리가 본 것"과 "고객이 본 것"이 갈린다 (PB-02). 재제안은 복제(QT-19)로 한다.
     */
    public void reopen() {
        if (stage == Stage.WON) {
            throw new BusinessException(ErrorCode.DEAL_ALREADY_WON);
        }
        if (stage != Stage.LOST) {
            throw new BusinessException(ErrorCode.DEAL_NOT_LOST);
        }
        this.stage = Stage.valueOf(lostFromStage);
        this.lostFromStage = null;
        this.lostReason = null;
    }

    /**
     * 진행 중(리드~협상)이어야 하는 전이의 공통 가드.
     *
     * <p>성사와 실패에 다른 코드를 쓰는 이유는 문구다 — {@code DEAL_ALREADY_WON}은
     * "성사된 Deal은 단계를 변경할 수 없습니다"라 실패 Deal에 쓰면 거짓말이 된다 (07 v1.6.7).
     */
    private void requireOpen() {
        if (stage == Stage.WON) {
            throw new BusinessException(ErrorCode.DEAL_ALREADY_WON);
        }
        if (!isOpen()) {
            throw new BusinessException(ErrorCode.DEAL_NOT_OPEN);
        }
    }

    /** 진행 중(리드~협상) 여부 — 종결 Deal에서 막히는 규칙들의 공통 판정 (Q-25, CU-08) */
    public boolean isOpen() {
        return OPEN_STAGES.contains(stage);
    }
}
