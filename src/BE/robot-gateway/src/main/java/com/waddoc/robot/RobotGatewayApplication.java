package com.waddoc.robot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 로봇 명령 API, MQTT 연결, 로봇 SSE를 전담하는 robot-gateway의 시작점이다.
 */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.waddoc")
public class RobotGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RobotGatewayApplication.class, args);
    }
}
