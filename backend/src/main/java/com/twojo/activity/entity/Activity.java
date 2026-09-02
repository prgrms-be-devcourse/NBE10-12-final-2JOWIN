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
import java.util.Objects;
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

    /**
     * 상담 기록 작성 (AC-01~03).
     *
     * <p>{@code authorMemberId}는 <b>수정·삭제 판정 축</b>이다 (AC-04·05) — 조회 축이 아니다.
     * 조회는 딜 담당자({@code deal.assignee_member_id}) 기준이라, 담당이 이관돼도
     * 새 담당자가 이전 담당자의 기록을 읽을 수 있다 (AC-08 · PB-05).
     *
     * <p>{@code occurredAt}은 기록 시각이 아니라 <b>상담이 실제로 일어난 시각</b>이다 (AC-03).
     */
    public static Activity create(UUID companyId, UUID dealId, UUID authorMemberId,
                                  Channel channel, String content, Instant occurredAt) {
        Activity activity = new Activity();
        activity.companyId = Objects.requireNonNull(companyId, "companyId");
        activity.dealId = Objects.requireNonNull(dealId, "dealId");
        activity.authorMemberId = Objects.requireNonNull(authorMemberId, "authorMemberId");
        activity.channel = Objects.requireNonNull(channel, "channel");
        activity.content = Objects.requireNonNull(content, "content");
        activity.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        return activity;
    }

    /**
     * 상담 기록 수정 (AC-04) — <b>null로 온 필드는 바꾸지 않는다</b> (08 §B의 PATCH 주석).
     *
     * <p>작성자 본인인지는 서비스가 먼저 판정한다 — 아니면 404 {@code ACTIVITY_NOT_AUTHOR}다.
     * 엔티티는 값만 바꾼다.
     */
    public void update(Channel channel, String content, Instant occurredAt) {
        if (channel != null) {
            this.channel = channel;
        }
        if (content != null) {
            this.content = content;
        }
        if (occurredAt != null) {
            this.occurredAt = occurredAt;
        }
    }

    /**
     * 소프트 삭제 (AC-05) — 이미 삭제됐으면 무동작이라 최초 시각이 유지된다.
     * 본인 작성분만 지울 수 있고, 그 판정은 서비스가 한다.
     */
    public void softDelete(Instant now) {
        if (deletedAt == null) {
            this.deletedAt = Objects.requireNonNull(now, "now");
        }
    }
}
