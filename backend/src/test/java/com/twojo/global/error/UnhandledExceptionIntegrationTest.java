package com.twojo.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 미처리 예외의 최종 응답 (07 v1.6.8 INTERNAL_ERROR).
 *
 * <p><b>MockMvc로는 성립하지 않는다.</b> 이 버그는 서블릿 컨테이너가 /error로 포워딩하고
 * 그 요청이 Security 필터를 다시 타면서 생긴다. MockMvc에는 컨테이너가 없어 예외가 그대로
 * 테스트로 던져질 뿐, 포워딩도 필터 재진입도 일어나지 않는다.
 *
 * <p>TestRestTemplate 대신 JDK HttpClient를 쓴다 — Spring Boot 4.1에서 별도 모듈로 빠져
 * 이 프로젝트 테스트 클래스패스에 없다. 빌드 파일을 건드리지 않고 같은 검증을 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UnhandledExceptionIntegrationTest {

    @Value("${local.server.port}") private int port;

    /** 07 v1.6.8 — 서버 오류가 403으로 나가면 모니터링이 5xx로 집계하지 못한다 */
    @Test
    void 처리되지_않은_예외는_우리_포맷의_500으로_나간다() throws Exception {
        // given · when — 무인증으로 열려 있지만 안에서 터지는 엔드포인트를 부른다
        HttpResponse<String> 응답 = 보낸다(HttpRequest.newBuilder()
                .uri(주소("/public/api/v1/boom"))
                .GET()
                .build());

        // then — 403이 아니라 500이고, 바디가 Spring 기본형이 아니라 우리 계약이다
        assertThat(응답.statusCode()).isEqualTo(500);
        assertThat(응답.body()).contains("\"code\":\"INTERNAL_ERROR\"");
    }

    /** 폴백이 Spring 표준 웹 예외까지 삼키면 400이어야 할 것이 500이 된다 */
    @Test
    void 깨진_요청_바디는_500이_아니라_400이다() throws Exception {
        // given — JSON이 중간에서 끊긴다
        // when
        HttpResponse<String> 응답 = 보낸다(HttpRequest.newBuilder()
                .uri(주소("/public/api/v1/auth/password-reset-request"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":"))
                .build());

        // then — 부모 핸들러가 먼저 잡아 상태 코드를 지킨다
        assertThat(응답.statusCode()).isEqualTo(400);
    }

    private URI 주소(String 경로) {
        return URI.create("http://localhost:" + port + 경로);
    }

    private HttpResponse<String> 보낸다(HttpRequest 요청) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(요청, HttpResponse.BodyHandlers.ofString());
        }
    }

    /** 예외를 일부러 일으킬 자리 — 프로덕션 라우팅에는 올라가지 않는다. */
    @TestConfiguration
    @RestController
    static class 터지는_엔드포인트 {

        @GetMapping("/public/api/v1/boom")
        void boom() {
            throw new IllegalStateException("의도적으로 터뜨린다");
        }
    }
}
