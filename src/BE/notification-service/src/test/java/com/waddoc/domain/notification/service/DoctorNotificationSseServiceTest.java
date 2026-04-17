package com.waddoc.domain.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;

class DoctorNotificationSseServiceTest {

    private DoctorNotificationSseService doctorNotificationSseService;

    @BeforeEach
    void setUp() {
        doctorNotificationSseService = new DoctorNotificationSseService();
    }

    @Test
    void subscribeDoctor_registersEmitterByDoctorUserId() {
        SseEmitter emitter = doctorNotificationSseService.subscribeDoctor("usr_doctor");

        assertThat(emitter).isNotNull();
        assertThat(doctorNotificationSseService.countConnections("usr_doctor")).isEqualTo(1);
    }

    @Test
    void sendHeartbeat_removesEmitterWhenClientDisconnects() throws Exception {
        String doctorUserId = "usr_doctor";
        emitterStore()
                .computeIfAbsent(doctorUserId, ignored -> new java.util.concurrent.ConcurrentHashMap<>())
                .put("conn_test", new BrokenPipeSseEmitter());

        doctorNotificationSseService.sendHeartbeat();

        assertThat(doctorNotificationSseService.countConnections(doctorUserId)).isZero();
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<String, ConcurrentMap<String, SseEmitter>> emitterStore() throws Exception {
        Field field = DoctorNotificationSseService.class.getDeclaredField("emittersByDoctorUserId");
        field.setAccessible(true);
        return (ConcurrentMap<String, ConcurrentMap<String, SseEmitter>>) field.get(doctorNotificationSseService);
    }

    private static final class BrokenPipeSseEmitter extends SseEmitter {
        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            throw new IOException("Broken pipe");
        }
    }
}
