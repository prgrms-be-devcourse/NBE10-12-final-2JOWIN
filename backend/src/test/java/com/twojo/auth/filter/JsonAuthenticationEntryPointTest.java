package com.twojo.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class JsonAuthenticationEntryPointTest {

    /** 07 부록 · AU-12 — 프론트는 이 코드를 보고 로그인 화면으로 보낸다 */
    @Test
    void 미인증_요청에는_401_과_세션_만료_안내가_나간다() throws Exception {
        // given — 체인이 미인증으로 판정한 요청
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when — EntryPoint 가 응답을 쓰면
        new JsonAuthenticationEntryPoint(JsonMapper.builder().build())
                .commence(new MockHttpServletRequest(), response, null);

        // then — GlobalExceptionHandler 가 만드는 것과 같은 모양이다
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString())
                .contains("\"code\":\"REFRESH_TOKEN_NOT_ACTIVE\"")
                .contains("세션이 만료되었습니다");
    }
}
