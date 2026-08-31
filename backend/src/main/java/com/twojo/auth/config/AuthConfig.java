package com.twojo.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** auth 모듈이 필요로 하는 빈. */
@Configuration
public class AuthConfig {

    /**
     * 시드 해시가 BCrypt 형식이므로 인코더도 BCrypt로 맞춘다 (R__demo_seed.sql).
     * SecurityConfig(E 소유)가 아니라 여기 두는 이유는 항목 11에서 그 파일을 한 번만 건드리기 위해서다.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
