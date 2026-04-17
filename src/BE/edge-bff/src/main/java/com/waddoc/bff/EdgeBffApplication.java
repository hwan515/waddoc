package com.waddoc.bff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 외부 `/api/**` 요청을 owner 서비스로 라우팅하는 BFF 런타임의 시작점이다.
 */
@SpringBootApplication(scanBasePackages = "com.waddoc")
public class EdgeBffApplication {

    public static void main(String[] args) {
        SpringApplication.run(EdgeBffApplication.class, args);
    }
}
