package com.twojo.customer.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객사 — 회사 공유 자원, 담당 개념 없음 (SC-03).
 * created_by는 생성자 기록용 — 접근 판정에 쓰지 않는다. 소프트 삭제.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID companyId;

    private UUID createdByMemberId;

    private String name;

    private String industry;

    private String size;

    @Column(columnDefinition = "text")
    private String note;

    private Instant deletedAt;
}
