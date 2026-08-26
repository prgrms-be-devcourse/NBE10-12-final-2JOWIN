package com.twojo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 스키마 검증 스모크 테스트 — 실제 PostgreSQL(twojo_test)에 Flyway를 태우고,
 * ddl-auto=validate로 엔티티 25개 ↔ 스키마 정합을 대조한다 (13-dev-workflow.md §3).
 * <p>로컬: {@code docker compose -f infra/docker-compose.yml up -d} 선행 필요 · CI: 서비스 컨테이너 PG.
 */
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
