package com.waddoc.domain.notification.controller;

import com.waddoc.domain.notification.service.DoctorNotificationSseService;
import com.waddoc.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/doctors/me/notifications")
@RequiredArgsConstructor
public class DoctorNotificationStreamController {

    private final DoctorNotificationSseService doctorNotificationSseService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('DOCTOR')")
    public SseEmitter subscribe(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return doctorNotificationSseService.subscribeDoctor(authenticatedUser);
    }
}
