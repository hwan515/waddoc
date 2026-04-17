package com.waddoc.domain.notification.controller;

import com.waddoc.domain.notification.service.DoctorNotificationSseService;
import com.waddoc.global.security.NotificationJwtAuthService;
import com.waddoc.shared.security.JwtPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DoctorNotificationStreamControllerTest {

    private DoctorNotificationSseService doctorNotificationSseService;
    private NotificationJwtAuthService notificationJwtAuthService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        doctorNotificationSseService = Mockito.mock(DoctorNotificationSseService.class);
        notificationJwtAuthService = Mockito.mock(NotificationJwtAuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new DoctorNotificationStreamController(doctorNotificationSseService, notificationJwtAuthService)
        ).build();
    }

    @Test
    void subscribe_returnsSseEmitterForAuthenticatedDoctor() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(notificationJwtAuthService.requireDoctor("Bearer access-token"))
                .thenReturn(new JwtPrincipal("usr_doctor", "DOCTOR"));
        when(doctorNotificationSseService.subscribeDoctor("usr_doctor")).thenReturn(emitter);

        mockMvc.perform(get("/api/v1/doctors/me/notifications/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer access-token")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(notificationJwtAuthService).requireDoctor("Bearer access-token");
        verify(doctorNotificationSseService).subscribeDoctor("usr_doctor");
    }

    @Test
    void subscribe_withoutAuthorizationHeader_returnsUnauthorized() throws Exception {
        when(notificationJwtAuthService.requireDoctor(null))
                .thenThrow(new ResponseStatusException(UNAUTHORIZED, "Invalid access token"));

        mockMvc.perform(get("/api/v1/doctors/me/notifications/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isUnauthorized());

        verify(notificationJwtAuthService).requireDoctor(null);
    }
}
