package com.waddoc.domain.robot.controller;

import com.waddoc.domain.robot.service.RobotSseService;
import com.waddoc.global.security.RobotJwtAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 운영 콘솔이 로봇 상태 스트림을 구독할 때 사용하는 SSE 진입점이다.
 */
@RestController
@RequestMapping("/api/v1/robots")
@RequiredArgsConstructor
public class RobotSseController {

    private final RobotSseService robotSseService;
    private final RobotJwtAuthService robotJwtAuthService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        robotJwtAuthService.requireAdminOrDoctor(authorizationHeader);
        return robotSseService.subscribe();
    }
}
