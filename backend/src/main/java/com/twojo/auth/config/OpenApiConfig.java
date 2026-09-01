package com.twojo.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 인증 설정 — Authorize 버튼 (springdoc 3.1.0).
 *
 * <p>이게 없으면 Swagger UI에서 Authorization 헤더를 붙일 방법이 없어 인증이 필요한
 * 엔드포인트를 전부 curl로 검증해야 한다. 검증 캡처가 사이클마다 필요한 구조라 비용이 반복된다.
 *
 * <p>global이 아니라 auth에 둔다 — global/config는 E 소유이고(CODEOWNERS),
 * 여기서 기술하는 것이 access token 전달 방식(07 "Authorization: Bearer")이라 auth의 관심사다.
 * 필터 체인을 같은 이유로 auth/config에 둔 것과 같은 판단이다.
 *
 * <p>refresh는 여기 없다. HttpOnly 쿠키라 브라우저가 자동으로 붙이며,
 * Swagger에서 값을 넣을 대상이 아니다 (07 쿠키 규약표).
 */
@Configuration
public class OpenApiConfig {

    /** 스킴 이름 — 정의할 때와 요구할 때 같은 문자열이어야 한다. 어긋나면 조용히 안 붙는다. */
    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("2JO API").version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
