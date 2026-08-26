package com.twojo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 2JO · Deal-to-Order SaaS.
 *
 * <p>모듈 구조: {@code com.twojo.{도메인}} 최상위 패키지 = Spring Modulith 모듈.
 * 타 도메인 접근은 모듈 루트의 공개 인터페이스로만 (docs/11-work-breakdown.md §7).
 *
 * <p>스케줄링: 만료 전이 배치(C, Q-37) · 알림 배치(D, NT-05·06) · 보존 삭제 배치 — 단일 인스턴스 전제 (14-tech-stack.md §1.2).
 */
@EnableScheduling
@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
