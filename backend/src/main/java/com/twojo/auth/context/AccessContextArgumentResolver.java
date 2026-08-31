package com.twojo.auth.context;

import com.twojo.boundary.AccessContext;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * 컨트롤러 파라미터가 AccessContext면 SecurityContext에서 꺼내 넣는다 (11 §1.4).
 *
 * <p>여기서 판정하지 않는다 — 인증은 JwtAuthenticationFilter가 끝냈고, 미인증 요청은
 * 체인이 막아 이 지점에 오지 않는다. 꺼내서 넘기는 것이 전부다.
 *
 * <p>상태가 없어 빈으로 두지 않는다 — AuthConfig가 직접 생성해 등록한다.
 */
public class AccessContextArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return AccessContext.class.equals(parameter.getParameterType());
    }

    @Override
    public AccessContext resolveArgument(MethodParameter parameter,
                                         ModelAndViewContainer mavContainer,
                                         NativeWebRequest webRequest,
                                         WebDataBinderFactory binderFactory) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal() instanceof AccessContext ctx)) {
            // permitAll 경로에 이 파라미터를 선언하면 여기 온다 — 사용자 입력이 아니라 배선 실수다
            throw new IllegalStateException(
                    "AccessContext는 인증된 경로에서만 주입된다: " + parameter.getMethod());
        }
        return ctx;
    }
}
