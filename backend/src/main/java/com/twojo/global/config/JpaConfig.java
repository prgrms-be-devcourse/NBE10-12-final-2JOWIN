package com.twojo.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** JPA Auditing — BaseTimeEntity의 created_at·updated_at 자동 기록. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
