package com.twojo.customer.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 고객사 담당자 — 대표 1명 부분 유니크 (CU-11). company_id 없음(부모 경유 격리).
 * 발송 이력 있으면 삭제 불가 (CU-14 — ViewTokenQuery.existsForContact 경유 판정).
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerContact extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID customerId;

    private String name;

    private String title;

    private String email;

    private String phone;

    private boolean isPrimary;
}
