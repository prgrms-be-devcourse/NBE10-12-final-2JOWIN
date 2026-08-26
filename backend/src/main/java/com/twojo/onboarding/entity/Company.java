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
}
