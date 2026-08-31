package com.twojo.auth.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.twojo.global.error.ErrorCode;
import com.twojo.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 미인증 요청의 401을 쓰는 단 한 곳.
 *
 * <p>코드는 REFRESH_TOKEN_NOT_ACTIVE를 재사용한다 — 07 부록에 access token 전용 코드가 없고,
 * 문구("세션이 만료되었습니다")와 AU-12 동작(로그인 화면 이동)이 그대로 맞는다.
 * 새 코드를 만들면 문서 PR이 먼저다.
 *
 * <p>authException의 사유는 쓰지 않는다 — 응답에 반영하면 SC-09가 깨진다.
 */
@Component
@RequiredArgsConstructor
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final ErrorCode CODE = ErrorCode.REFRESH_TOKEN_NOT_ACTIVE;

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        response.setStatus(CODE.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(CODE));
    }
}
