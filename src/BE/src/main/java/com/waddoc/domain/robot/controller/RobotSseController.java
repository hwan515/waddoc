package com.waddoc.domain.robot.controller;

import com.waddoc.domain.robot.service.RobotSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/robots")
@RequiredArgsConstructor
public class RobotSseController {

    private final RobotSseService robotSseService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    public SseEmitter subscribe() {
        return robotSseService.subscribe();
    }
}
