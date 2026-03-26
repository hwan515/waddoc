package com.waddoc.domain.notification.controller;

import com.waddoc.domain.notification.service.DoctorNotificationSseService;
import com.waddoc.global.error.GlobalExceptionHandler;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DoctorNotificationStreamController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DoctorNotificationStreamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DoctorNotificationSseService doctorNotificationSseService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void subscribe_returnsSseEmitter() throws Exception {
        SseEmitter emitter = new SseEmitter();
        when(doctorNotificationSseService.subscribeDoctor(any())).thenReturn(emitter);

        mockMvc.perform(get("/api/v1/doctors/me/notifications/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(doctorNotificationSseService).subscribeDoctor(any());
    }
}
