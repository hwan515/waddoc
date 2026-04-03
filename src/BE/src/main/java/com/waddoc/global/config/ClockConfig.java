package com.waddoc.global.config;

import com.waddoc.global.util.KstTime;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(KstTime.ZONE);
    }
}
