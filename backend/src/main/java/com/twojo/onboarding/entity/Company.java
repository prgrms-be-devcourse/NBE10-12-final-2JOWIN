package com.twojo.onboarding.entity;

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

/** 회사 — 신청서와 1:1 (application_id UNIQUE = 승인 멱등). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Company extends BaseTimeEntity {

    public enum Status { ACTIVE, SUSPENDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID applicationId;

    private String name;

    private String businessNo;   // 전역 UNIQUE — 사업자번호당 테넌트 1개

    @Enumerated(EnumType.STRING)
    private Status status;

    private String suspendReason;

    /**
     * 승인으로 생기는 회사 (ON-04·07) — 처음부터 운영 중이다.
     *
     * <p>이름·사업자번호는 신청서에서 그대로 복사한다. 사업자번호 전역 중복 검사는
     * 호출 전에 끝난다 — 여기서 걸리면 {@code business_no} 유니크 위반으로 500이 나간다.
     */
    public static Company create(UUID applicationId, String name, String businessNo) {
        Company company = new Company();
        company.applicationId = applicationId;
        company.name = name;
        company.businessNo = businessNo;
        company.status = Status.ACTIVE;
        return company;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /**
     * 정지 (ON-08) — 사유를 남긴다.
     *
     * <p>구성원 차단의 실체는 여기가 아니라 {@code refresh_token} 전 행 폐기다
     * (전이표 §9 · ON-09). 이 메서드는 상태만 넘기고, 폐기는 호출자가 같은 트랜잭션에서 한다.
     */
    public void suspend(String reason) {
        this.status = Status.SUSPENDED;
        this.suspendReason = reason;
    }

    /**
     * 정지 해제 (ON-10) — 데이터는 그대로다.
     *
     * <p>사유를 지우는 것은 "지금은 정지가 아니다"를 한 곳에서 읽히게 하기 위해서다.
     * 남겨두면 {@code ACTIVE}인데 정지 사유가 붙은 행이 생겨 화면이 무엇을 믿을지 갈린다.
     * 정지 이력이 필요해지면 별도 테이블이지 이 컬럼이 아니다.
     *
     * <p>구성원 세션은 되살리지 않는다 — 정지 때 폐기됐으므로 재로그인이 필요하다 (Q-27).
     */
    public void reactivate() {
        this.status = Status.ACTIVE;
        this.suspendReason = null;
    }
}
