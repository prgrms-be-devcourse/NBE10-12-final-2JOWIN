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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 파라미터 타입 불일치의 응답 포맷 (07 부록 · 08 §0).
 *
 * <p><b>MockMvc로는 부족하다.</b> 여기서 보려는 것이 상태 코드가 아니라 <b>Content-Type과
 * 바디 스키마</b>인데, 그 둘은 메시지 컨버터를 지나야 정해진다. {@code UnhandledException
 * IntegrationTest}와 같은 이유로 실제 컨테이너를 띄우고 JDK HttpClient로 부른다.
 *
 * <p>고치기 전에는 이 경로가 {@code ResponseEntityExceptionHandler}의 기본 처리로 떨어져
 * {@code application/problem+json}(RFC 7807)으로 나갔다 — 같은 400인데 {@code code} 필드가
 * 없어, 그것으로 분기하는 프론트 공통 핸들러가 아예 알아보지 못했다.
 *
 * <p>실제 엔드포인트({@code /api/v1/invitations?status=} ·
 * {@code /admin/api/v1/applications?status=})는 인증이 필요해 여기서 부르지 않는다.
 * 그쪽은 기동 후 실측으로 확인한다 — 이 테스트가 지키는 것은 <b>핸들러의 계약</b>이다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class TypeMismatchIntegrationTest {

    @Value("${local.server.port}") private int port;

    @Test
    void 없는_enum_값은_우리_포맷의_400이다() throws Exception {
        HttpResponse<String> 응답 = 보낸다("/public/api/v1/type-mismatch-probe?status=BAD");

        assertThat(응답.statusCode()).isEqualTo(400);
        assertThat(응답.headers().firstValue("Content-Type").orElseThrow())
                .startsWith("application/json");
        assertThat(응답.body()).contains("\"code\":\"VALIDATION_FAILED\"");
    }

    /** 어느 파라미터가 틀렸는지 알려준다 — 07 부록이 이 코드에 "fieldErrors 참조"라고 적는다. */
    @Test
    void 틀린_파라미터_이름이_fieldErrors에_담긴다() throws Exception {
        HttpResponse<String> 응답 = 보낸다("/public/api/v1/type-mismatch-probe?status=BAD");

        assertThat(응답.body()).contains("\"field\":\"status\"");
    }

    /**
     * 보낸 값을 응답에 되돌려주지 않는다 — 반사형 XSS 표면을 만들지 않기 위해서다.
     * 고치기 전 ProblemDetail은 {@code "Failed to convert 'status' with value: 'BAD'"}로
     * 그대로 실어 보냈다.
     */
    @Test
    void 보낸_값을_응답에_되돌려주지_않는다() throws Exception {
        HttpResponse<String> 응답 =
                보낸다("/public/api/v1/type-mismatch-probe?status=%3Cscript%3E");

        assertThat(응답.body()).doesNotContain("script");
    }

    /** 빈 값은 오류가 아니다 — Spring이 null로 바꾸고, 그것이 "전체 조회"의 신호다. */
    @Test
    void 빈_값은_null로_들어간다() throws Exception {
        assertThat(보낸다("/public/api/v1/type-mismatch-probe?status=").body()).isEqualTo("null");
        assertThat(보낸다("/public/api/v1/type-mismatch-probe").body()).isEqualTo("null");
    }

    private HttpResponse<String> 보낸다(String 경로) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest.newBuilder().uri(URI.create("http://localhost:" + port + 경로))
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }

    /** enum 파라미터를 받는 자리 — 프로덕션 라우팅에는 올라가지 않는다. */
    @TestConfiguration
    @RestController
    static class 파라미터를_받는_엔드포인트 {

        enum Probe { PENDING, APPROVED }

        @GetMapping("/public/api/v1/type-mismatch-probe")
        String probe(@RequestParam(required = false) Probe status) {
            return String.valueOf(status);
        }
    }
}
