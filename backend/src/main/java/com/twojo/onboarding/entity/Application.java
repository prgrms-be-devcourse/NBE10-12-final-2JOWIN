package com.twojo.onboarding.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 가입 신청 — 반려 이력 보존·재신청 허용 (Q-15). 번호 없음, id로 식별 (v1.6). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application extends BaseTimeEntity {

    public enum Status { PENDING, APPROVED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String companyName;

    private String businessNo;   // 승인 시 company로 복사 — 여기엔 유니크 없음(재신청 허용)

    private String email;

    private String applicantName;   // 승인 시 member.name으로 복사 (ON-07, 08 v1.6.11)

    @Enumerated(EnumType.STRING)
    private Status status;

    private String rejectReason;   // ON-14 — 반려 시 필수 (서비스 검증)

    private Instant decidedAt;

    /**
     * 접수 (ON-01) — 검토 대기로 시작한다.
     *
     * <p>같은 이메일의 대기 신청 유무와 기존 구성원 여부는 호출 전에 확인된다.
     * 여기서 보면 리포지토리를 엔티티가 알아야 한다.
     */
    public static Application submit(String companyName, String businessNo, String email,
                                     String applicantName) {
        Application application = new Application();
        application.companyName = companyName;
        application.businessNo = businessNo;
        application.email = email;
        application.applicantName = applicantName;
        application.status = Status.PENDING;
        return application;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    /**
     * 승인 (ON-04) · 반려 (ON-05).
     *
     * <p>대기 상태인지 검사하지 않는다 — 초대(Invitation)와 같은 이유다. 검사를 여기 두면
     * 서비스와 두 곳에서 같은 판정을 하게 되고, 어느 쪽 예외가 나가는지가 호출 순서를 탄다.
     *
     * <p>심사자를 남기지 않는다. {@code application} 테이블에 {@code decided_by}가 없고
     * 07 §A도 요구하지 않는다 — 필요해지면 컬럼부터 늘린다.
     */
    public void approve(Instant now) {
        this.status = Status.APPROVED;
        this.decidedAt = now;
    }

    /** 사유는 필수다 (ON-14). 빈 값 차단은 요청 DTO의 {@code @NotBlank}가 맡는다. */
    public void reject(String reason, Instant now) {
        this.status = Status.REJECTED;
        this.rejectReason = reason;
        this.decidedAt = now;
    }
}
