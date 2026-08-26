package com.twojo.onboarding.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 플랫폼 관리자 — 구성원과 별도 계정 (AU-08). 로그인 제한도 동일 적용 (Q-30). */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlatformAdmin extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String email;   // 전역 UNIQUE

    private String passwordHash;

    private String status;
}
