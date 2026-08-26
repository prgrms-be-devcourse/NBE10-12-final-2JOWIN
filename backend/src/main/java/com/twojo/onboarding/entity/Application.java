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

    @Enumerated(EnumType.STRING)
    private Status status;

    private String rejectReason;   // ON-14 — 반려 시 필수 (서비스 검증)

    private Instant decidedAt;
}
