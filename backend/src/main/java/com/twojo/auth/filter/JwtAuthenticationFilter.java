package com.twojo.auth.filter;

import com.twojo.auth.jwt.JwtProvider;
import com.twojo.boundary.AccessContext;
import com.twojo.boundary.AccessScope;
import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.MemberQuery;
import com.twojo.boundary.Role;
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
 * access token 인증 — /api/v1 체인에만 붙는다 (07 §경로 체계).
 *
 * <p><b>실패해도 예외를 던지지 않는다.</b> 필터는 DispatcherServlet 밖이라 GlobalExceptionHandler가
 * 잡지 못해 500이 된다. SecurityContext를 비운 채 통과시키고, 판정은 체인의 authenticated()가,
 * 401 응답은 JsonAuthenticationEntryPoint가 맡는다.
 *
 * <p>토큰 부재·위조·만료·구성원 비활성·회사 정지를 응답으로 구별하지 않는다 (SC-09).
 *
 * <p>빈으로 등록하지 않는다 — Boot가 Filter 타입 빈을 서블릿 컨테이너에 자동 등록해
 * 보안 체인 밖에서 한 번 더 돌기 때문이다. SecurityConfig가 직접 생성해 체인에 끼운다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final MemberQuery memberQuery;
    private final CompanyQuery companyQuery;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        authenticate(request).ifPresent(
                ctx -> SecurityContextHolder.getContext().setAuthentication(token(ctx)));
        chain.doFilter(request, response);
    }

    /** 비어 있으면 미인증이다 — 사유는 남기지 않는다. */
    private Optional<AccessContext> authenticate(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        try {
            Claims claims = jwtProvider.parse(header.substring(BEARER_PREFIX.length()));
            UUID memberId = JwtProvider.memberIdOf(claims);

            // 비활성·정지는 토큰 발급 이후에 바뀐다 — claim이 아니라 지금 값을 본다 (09 구현 위치)
            MemberQuery.AuthCredential credential = memberQuery.getCredential(memberId);
            if (!credential.active() || !companyQuery.get(credential.companyId()).active()) {
                return Optional.empty();
            }
            return Optional.of(new AccessContext(credential.companyId(), credential.id(),
                    credential.role(), scopeOf(credential.role())));

        } catch (JwtException | IllegalArgumentException | BusinessException e) {
            // 서명·만료 실패 · claim 형식 이상 · 구성원 행 부재 — 전부 미인증이다
            return Optional.empty();
        }
    }

    /** 11 §1.4 — 담당 판정 축은 deal.assignee_member_id 하나뿐이다. */
    private static AccessScope scopeOf(Role role) {
        return role == Role.COMPANY_ADMIN ? AccessScope.COMPANY_ALL : AccessScope.OWNED_ONLY;
    }

    /**
     * 3인자 생성자여야 isAuthenticated가 true다 — 2인자는 미인증 토큰을 만든다.
     * 권한은 다음 사이클의 역할 검사(403 FORBIDDEN, Q-43)가 읽는다.
     */
    private static UsernamePasswordAuthenticationToken token(AccessContext ctx) {
        return new UsernamePasswordAuthenticationToken(ctx, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + ctx.role().name())));
    }
}
