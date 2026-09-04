package com.twojo.auth.filter;

import com.twojo.auth.entity.ActorType;
import com.twojo.auth.jwt.JwtProvider;
import com.twojo.boundary.PlatformAdminQuery;
import com.twojo.global.error.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 관리자 access token 인증 (AU-08) — /admin/api/v1 체인에만 붙는다.
 *
 * <p>구성원 필터와 같은 모양이다. <b>실패해도 예외를 던지지 않는다</b> — 필터는
 * DispatcherServlet 밖이라 GlobalExceptionHandler가 잡지 못해 500이 된다.
 * SecurityContext를 비운 채 통과시키고, 401 응답은 JsonAuthenticationEntryPoint가 맡는다.
 *
 * <p>다루는 값은 관리자 id 하나다. 관리자는 회사에 속하지 않고 역할이 하나뿐이라
 * AccessContext에 담을 것이 없다.
 *
 * <p>빈으로 등록하지 않는다 — Boot가 Filter 타입 빈을 서블릿 컨테이너에 자동 등록해
 * 보안 체인 밖에서 한 번 더 돌기 때문이다. SecurityConfig가 직접 생성해 체인에 끼운다.
 */
@RequiredArgsConstructor
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final PlatformAdminQuery platformAdminQuery;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        authenticate(request).ifPresent(
                adminId -> SecurityContextHolder.getContext().setAuthentication(token(adminId)));
        chain.doFilter(request, response);
    }

    /** 비어 있으면 미인증이다 — 사유는 남기지 않는다. */
    private Optional<UUID> authenticate(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        try {
            Claims claims = jwtProvider.parse(header.substring(BEARER_PREFIX.length()));

            // 구성원 토큰이 이 체인으로 오는 것을 막는다 — 통과시키면 영업 데이터가 없는
            // 관리자 경로에 구성원이 인증된 채로 들어선다
            if (!JwtProvider.isActor(claims, ActorType.PLATFORM_ADMIN)) {
                return Optional.empty();
            }

            // 비활성은 토큰 발급 이후에 바뀐다 — claim이 아니라 지금 값을 본다
            UUID adminId = JwtProvider.subjectOf(claims);
            if (!platformAdminQuery.isActive(adminId)) {
                return Optional.empty();
            }
            return Optional.of(adminId);

        } catch (JwtException | IllegalArgumentException | BusinessException e) {
            // 서명·만료 실패 · claim 형식 이상 — 전부 미인증이다
            return Optional.empty();
        }
    }

    /**
     * 3인자 생성자여야 isAuthenticated가 true다 — 2인자는 미인증 토큰을 만든다.
     * principal은 관리자 id 하나다. 타입 있는 컨텍스트는 그것을 읽는 엔드포인트가 생길 때 만든다.
     */
    private static UsernamePasswordAuthenticationToken token(UUID adminId) {
        return new UsernamePasswordAuthenticationToken(adminId, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + ActorType.PLATFORM_ADMIN.name())));
    }
}
