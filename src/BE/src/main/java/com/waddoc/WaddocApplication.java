package com.waddoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class WaddocApplication {

    public static void main(String[] args) {
        SpringApplication.run(WaddocApplication.class, args);
    }
}
