package com.twojo;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 컨텍스트 로드 스모크 테스트 — 실제 PostgreSQL(twojo_test)에 Flyway를 태워 검증한다.
 * <p>{@code ./gradlew test}에는 포함되지 않는다 — {@code ./gradlew integrationTest}로만 실행.
 * <p>로컬: {@code docker compose -f infra/docker-compose.yml up -d} 선행 필요 · CI: 서비스 컨테이너 PG.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

    @Test
    void contextLoads() {
    }
}
