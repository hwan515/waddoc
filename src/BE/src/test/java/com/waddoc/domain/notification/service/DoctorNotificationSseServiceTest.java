package com.waddoc.domain.notification.service;

import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorNotificationSseServiceTest {

    @Mock
    private AccessControlService accessControlService;

    @InjectMocks
    private DoctorNotificationSseService doctorNotificationSseService;

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
}
