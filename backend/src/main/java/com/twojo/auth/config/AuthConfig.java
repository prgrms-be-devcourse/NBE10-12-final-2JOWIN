package com.twojo.auth.config;

import com.twojo.auth.context.AccessContextArgumentResolver;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** auth 모듈이 필요로 하는 빈과 MVC 설정. */
@Configuration
public class AuthConfig implements WebMvcConfigurer {

    /**
     * 시드 해시가 BCrypt 형식이므로 인코더도 BCrypt로 맞춘다 (R__demo_seed.sql).
     * SecurityConfig(E 소유)가 아니라 여기 두는 이유는 항목 11에서 그 파일을 한 번만 건드리기 위해서다.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AccessContext 주입을 MVC에 등록한다 (11 §1.4).
     * global(E 소유)이 아니라 여기 두는 이유는 PasswordEncoder와 같다.
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new AccessContextArgumentResolver());
    }
}
