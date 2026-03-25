package com.waddoc.domain.dispatch.service;

import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

@Slf4j
@Component
public class RobotWaypointCommandClient {

    private final WebClient webClient;
    private final long timeoutMs;

    public RobotWaypointCommandClient(
            WebClient.Builder webClientBuilder,
            @Value("${robot.command-base-url}") String commandBaseUrl,
            @Value("${robot.command-timeout-ms:5000}") long timeoutMs
    ) {
        this.webClient = webClientBuilder.baseUrl(commandBaseUrl).build();
        this.timeoutMs = timeoutMs;
    }

    public void dispatchToWaypoint(int targetWaypointNumber) {
        try {
            webClient.post()
                    .uri("/api/cmd/waypoint/{target}", targetWaypointNumber)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofMillis(timeoutMs));
            log.info("Robot waypoint dispatch requested. targetWaypointNumber={}", targetWaypointNumber);
        } catch (RuntimeException e) {
            log.error("Robot waypoint dispatch failed. targetWaypointNumber={}", targetWaypointNumber, e);
            throw new BusinessException(ErrorCode.ROBOT_COMMAND_REQUEST_FAILED);
        }
    }
}
