package com.twojo.auth.config;

import com.twojo.auth.filter.JsonAuthenticationEntryPoint;
import com.twojo.auth.filter.JwtAuthenticationFilter;
import com.twojo.auth.jwt.JwtProvider;
import com.twojo.boundary.CompanyQuery;
import com.twojo.boundary.MemberQuery;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 인증 필터 체인 3분리 (07 §경로 체계 · 11 §2 · auth/package-info).
 *
 * <ul>
 *   <li>{@code /api/v1/**} — 구성원 JWT (access Bearer 15분, refresh는 HttpOnly 쿠키 2jo_rt)</li>
 *   <li>{@code /admin/api/v1/**} — 플랫폼 관리자 별도 체인 (쿠키 2jo_admin_rt, AU-08)</li>
 *   <li>{@code /public/api/v1/**} — 무인증 / 링크 토큰 (SC-07~09)</li>
 * </ul>
 *
 * <p>global(E 소유)이 아니라 auth에 둔다. auth가 이미 global.error에 의존하므로 반대 방향
 * 참조는 모듈 순환이 되어 Modulith 검증이 CI에서 막는다.
 *
 * <p>CSRF: refresh 쿠키는 Path 한정 + SameSite=Lax로 방어 (14 §2-2-1) — 세션 미사용이므로 비활성.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 구성원 API — 로그인·재발급만 열고 나머지는 access token을 요구한다 (07 §A). */
    @Bean
    @Order(1)
    SecurityFilterChain memberChain(HttpSecurity http, JwtProvider jwtProvider,
                                    MemberQuery memberQuery, CompanyQuery companyQuery,
                                    JsonAuthenticationEntryPoint entryPoint) throws Exception {
        return stateless(http)
                .securityMatcher("/api/v1/**")
                .authorizeHttpRequests(auth -> auth
                        // 로그아웃도 쿠키가 곧 자격 증명이다 — access 만료 뒤에야말로 확실히 끊어야 한다
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/logout").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider, memberQuery, companyQuery),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .build();
    }

    /**
     * 플랫폼 관리자 API — 인증 수단(AU-08)이 아직 없어 auth 외 경로는 전부 401이 정상이다.
     * 그래도 지금 분리한다. 없으면 /admin/**이 구성원 JWT로 열려 ON-11이 뚫린다.
     */
    @Bean
    @Order(2)
    SecurityFilterChain adminChain(HttpSecurity http,
                                   JsonAuthenticationEntryPoint entryPoint) throws Exception {
        return stateless(http)
                .securityMatcher("/admin/api/v1/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/admin/api/v1/auth/login",
                                "/admin/api/v1/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
                .build();
    }

    /** 방문자·고객 열람 링크 — 인증 없음. 링크 토큰 검증은 컨트롤러·서비스의 몫이다 (SC-07~09). */
    @Bean
    @Order(3)
    SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
        return stateless(http)
                .securityMatcher("/public/api/v1/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }

    /** 위 셋에 안 걸리는 나머지 — 헬스체크와 Swagger만 열고 전부 막는다. */
    @Bean
    @Order(4)
    SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        return stateless(http)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        // Swagger — API 테스트용. TODO(A): 운영 프로필에서는 차단
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // 미처리 예외를 서블릿이 여기로 포워딩한다. 막으면 500이 403으로 둔갑한다
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                .build();
    }

    /**
     * 오리진은 정확히 명시한다 — 와일드카드는 Allow-Credentials와 함께 쓸 수 없다 (07 §A · 14 §3-9).
     * 값은 프로필별 프로퍼티다. 도메인이 확보되면 문자열만 바뀐다 (14 §1.4).
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** 네 체인이 공유하는 설정 — 빠뜨리면 그 체인만 조용히 다르게 동작한다. */
    private static HttpSecurity stateless(HttpSecurity http) throws Exception {
        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    }
}
