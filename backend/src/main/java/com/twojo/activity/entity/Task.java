package com.twojo.activity.entity;

import com.twojo.global.jpa.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 다음 할 일 (AC-09) — 배정 컬럼 없음 (Q-29): deal의 순수 자식.
 * "내 할 일" = 내 담당 Deal의 미완료 할 일. 이관 시 Deal을 따라 자동 이동.
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID dealId;

    private String content;

    private LocalDate dueDate;

    private Instant doneAt;
}
