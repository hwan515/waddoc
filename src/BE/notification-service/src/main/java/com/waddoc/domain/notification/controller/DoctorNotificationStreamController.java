package com.waddoc.domain.notification.controller;

import com.waddoc.domain.notification.service.DoctorNotificationSseService;
import com.waddoc.global.security.NotificationJwtAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 의사 클라이언트가 실시간 알림 스트림을 구독하는 SSE 엔드포인트다.
 */
@RestController
@RequestMapping("/api/v1/doctors/me/notifications")
@RequiredArgsConstructor
public class DoctorNotificationStreamController {

    private final DoctorNotificationSseService doctorNotificationSseService;
    private final NotificationJwtAuthService notificationJwtAuthService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return doctorNotificationSseService.subscribeDoctor(
                notificationJwtAuthService.requireDoctor(authorizationHeader).userId()
        );
    }
}
