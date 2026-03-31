package com.waddoc.global.config;

import com.waddoc.global.util.KstTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "kstDateTimeProvider")
public class JpaConfig {

    @Bean
    public DateTimeProvider kstDateTimeProvider() {
        return () -> Optional.of(KstTime.now());
    }
}
