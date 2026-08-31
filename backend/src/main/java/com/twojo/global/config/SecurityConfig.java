package com.twojo.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 골격용 임시 보안 설정.
 *
 * <p>TODO(A·E 1주차): 필터 체인 3분리로 교체 (docs/07-api-spec.md 경로 체계 · docs/11-work-breakdown.md §2)
 * <ul>
 *   <li>{@code /api/v1/**} — 구성원 JWT (access Bearer 15분, refresh는 HttpOnly 쿠키 2jo_rt)</li>
 *   <li>{@code /admin/api/v1/**} — 플랫폼 관리자 별도 체인 (쿠키 2jo_admin_rt, AU-08)</li>
 *   <li>{@code /public/api/v1/**} — 무인증 / 링크 토큰 (SC-07~09)</li>
 * </ul>
 * CSRF: refresh 쿠키는 Path 한정 + SameSite=Lax로 방어 (14-tech-stack.md §2-2-1) — 세션 미사용이므로 비활성.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain skeletonFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/public/api/v1/**").permitAll()
                        // Swagger — API 테스트용. TODO(A): 운영 프로필에서는 차단
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // 인증 전에 호출되는 두 경로만 연다. logout은 인증된 사용자만 해야 하므로
                        // /api/v1/auth/** 를 통째로 열지 않는다 (07 §A)
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .anyRequest().authenticated())
                .build();
    }
}
