package com.waddoc.global.config;

import com.waddoc.global.util.KstTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 애플리케이션 전역 시간 기준을 주입 가능하게 만드는 Clock 설정이다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(KstTime.ZONE);
    }
}
