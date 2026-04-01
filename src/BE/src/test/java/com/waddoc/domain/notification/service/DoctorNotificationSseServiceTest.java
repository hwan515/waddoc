package com.waddoc.domain.notification.service;

import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import com.waddoc.global.util.KstTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorNotificationSseServiceTest {

    @Mock
    private AccessControlService accessControlService;

    private DoctorNotificationSseService doctorNotificationSseService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), KstTime.ZONE);

    @BeforeEach
    void setUp() {
        doctorNotificationSseService = new DoctorNotificationSseService(accessControlService, clock);
    }

    @Test
    void subscribeDoctor_registersEmitterForDoctorProfile() {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser("usr_doctor", Role.DOCTOR);
        DoctorProfile doctorProfile = DoctorProfile.builder()
                .user(User.builder()
                        .username("doctor")
                        .passwordHash("encoded")
                        .name("Doctor Kim")
                        .role(Role.DOCTOR)
                        .build())
                .department("INTERNAL_MEDICINE")
                .departmentName("Internal Medicine")
                .build();

        when(accessControlService.getDoctorProfileOrThrow(authenticatedUser)).thenReturn(doctorProfile);

        SseEmitter emitter = doctorNotificationSseService.subscribeDoctor(authenticatedUser);

        assertThat(emitter).isNotNull();
        assertThat(doctorNotificationSseService.countConnections(doctorProfile.getPublicId())).isEqualTo(1);
    }

    @Test
    void sendHeartbeat_removesEmitterWhenClientDisconnects() throws Exception {
        String doctorProfileId = "doc_test";
        emitterStore()
                .computeIfAbsent(doctorProfileId, ignored -> new java.util.concurrent.ConcurrentHashMap<>())
                .put("conn_test", new BrokenPipeSseEmitter());

        doctorNotificationSseService.sendHeartbeat();

        assertThat(doctorNotificationSseService.countConnections(doctorProfileId)).isZero();
    }

    @SuppressWarnings("unchecked")
    private ConcurrentMap<String, ConcurrentMap<String, SseEmitter>> emitterStore() throws Exception {
        Field field = DoctorNotificationSseService.class.getDeclaredField("emittersByDoctorId");
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
