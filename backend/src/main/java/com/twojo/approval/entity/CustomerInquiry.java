package com.twojo.approval.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 고객 문의 — 기록만 (Q-20). 답변은 메일 등 외부 수단, 조회 API는 v1에 없다 (Q-42). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerInquiry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID quoteId;

    @Column(columnDefinition = "text")
    private String content;

    /** 문의 생성 — 기록만 (Q-20). 상태·전이 없음. */
    public static CustomerInquiry of(UUID quoteId, String content) {
        CustomerInquiry inquiry = new CustomerInquiry();
        inquiry.quoteId = Objects.requireNonNull(quoteId, "quoteId");
        inquiry.content = Objects.requireNonNull(content, "content");
        return inquiry;
    }
}
