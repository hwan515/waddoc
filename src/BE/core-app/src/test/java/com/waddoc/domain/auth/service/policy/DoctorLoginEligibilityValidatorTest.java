package com.waddoc.domain.auth.service.policy;

import com.waddoc.domain.doctor.repository.DoctorProfileRepository;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DoctorLoginEligibilityValidatorTest {

    private final DoctorProfileRepository doctorProfileRepository = mock(DoctorProfileRepository.class);
    private final DoctorLoginEligibilityValidator validator =
            new DoctorLoginEligibilityValidator(doctorProfileRepository);

    @Test
    void validateThrowsWhenDoctorProfileIsMissing() {
        User doctor = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded-password")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();
        setField(doctor, "id", 1L);

        when(doctorProfileRepository.existsByUserId(1L)).thenReturn(false);

        assertThatThrownBy(() -> validator.validate(doctor))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_DOCTOR_PROFILE_REQUIRED);
    }

    @Test
    void validatePassesWhenDoctorProfileExists() {
        User doctor = User.builder()
                .username("doctor_kim")
                .passwordHash("encoded-password")
                .name("김의사")
                .role(Role.DOCTOR)
                .build();
        setField(doctor, "id", 1L);

        when(doctorProfileRepository.existsByUserId(1L)).thenReturn(true);

        validator.validate(doctor);

        verify(doctorProfileRepository).existsByUserId(1L);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
