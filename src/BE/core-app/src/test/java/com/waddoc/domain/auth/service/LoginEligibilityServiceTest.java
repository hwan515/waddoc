package com.waddoc.domain.auth.service;

import com.waddoc.domain.auth.service.policy.LoginEligibilityValidator;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class LoginEligibilityServiceTest {

    @Test
    void validateDispatchesToMatchingRoleValidator() {
        LoginEligibilityValidator adminValidator = mock(LoginEligibilityValidator.class);
        LoginEligibilityValidator doctorValidator = mock(LoginEligibilityValidator.class);
        LoginEligibilityValidator guardianValidator = mock(LoginEligibilityValidator.class);
        User doctor = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded-password")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();

        when(adminValidator.supports()).thenReturn(Role.ADMIN);
        when(doctorValidator.supports()).thenReturn(Role.DOCTOR);
        when(guardianValidator.supports()).thenReturn(Role.GUARDIAN);

        LoginEligibilityService service = new LoginEligibilityService(
                List.of(adminValidator, doctorValidator, guardianValidator)
        );

        service.validate(doctor);

        verify(adminValidator).supports();
        verify(doctorValidator).supports();
        verify(guardianValidator).supports();
        verify(doctorValidator).validate(doctor);
        verifyNoMoreInteractions(adminValidator, doctorValidator, guardianValidator);
    }
}
