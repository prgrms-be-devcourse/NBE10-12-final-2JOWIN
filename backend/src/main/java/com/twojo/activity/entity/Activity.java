package com.twojo.activity.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
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

/**
 * 상담 기록 (수동, AC-01~05) — 자동 기록은 audit_log가 담당하고 타임라인에서 병합된다.
 * author는 수정·삭제 판정용(AC-04·05, 본인만) — 조회 경로가 아니다.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Activity extends BaseTimeEntity {

    public enum Channel { CALL, MEETING, EMAIL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID companyId;

    private UUID dealId;

    private UUID authorMemberId;

    @Enumerated(EnumType.STRING)
    private Channel channel;

    @Column(columnDefinition = "text")
    private String content;

    private Instant occurredAt;

    private Instant deletedAt;
}
