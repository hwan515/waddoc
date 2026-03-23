package com.waddoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스프링 백엔드의 시작점이다.
 * 비동기 작업과 스케줄링 기능도 함께 활성화한다.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class WaddocApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaddocApplication.class, args);
    }
}
