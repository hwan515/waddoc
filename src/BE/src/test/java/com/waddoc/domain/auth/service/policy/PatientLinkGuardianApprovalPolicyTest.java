package com.waddoc.domain.auth.service.policy;

import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.domain.patient.repository.PatientGuardianLinkRepository;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.type.ApprovalStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PatientLinkGuardianApprovalPolicyTest {

    private final PatientGuardianLinkRepository patientGuardianLinkRepository = mock(PatientGuardianLinkRepository.class);
    private final PatientLinkGuardianApprovalPolicy policy =
            new PatientLinkGuardianApprovalPolicy(patientGuardianLinkRepository);

    @Test
    void validateApprovedGuardianThrowsWhenUserApprovalIsPending() {
        User guardian = User.builder()
                .username("guardian_lee")
                .passwordHash("encoded-password")
                .name("이보호자")
                .role(Role.GUARDIAN)
                .build();
        setField(guardian, "id", 2L);

        assertThatThrownBy(() -> policy.validateApprovedGuardian(guardian))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_GUARDIAN_NOT_APPROVED);

        verifyNoInteractions(patientGuardianLinkRepository);
    }

    @Test
    void validateApprovedGuardianThrowsWhenGuardianHasNoApprovedPatientLink() {
        User guardian = User.builder()
                .username("guardian_lee")
                .passwordHash("encoded-password")
                .name("이보호자")
                .role(Role.GUARDIAN)
                .build();
        setField(guardian, "id", 2L);
        setField(guardian, "approvalStatus", ApprovalStatus.APPROVED);

        when(patientGuardianLinkRepository.existsByGuardianUserIdAndStatus(2L, GuardianLinkStatus.APPROVED)).thenReturn(false);

        assertThatThrownBy(() -> policy.validateApprovedGuardian(guardian))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_GUARDIAN_NOT_APPROVED);
    }

    @Test
    void validateApprovedGuardianPassesWhenGuardianHasPatientLink() {
        User guardian = User.builder()
                .username("guardian_lee")
                .passwordHash("encoded-password")
                .name("이보호자")
                .role(Role.GUARDIAN)
                .build();
        setField(guardian, "id", 2L);
        setField(guardian, "approvalStatus", ApprovalStatus.APPROVED);

        when(patientGuardianLinkRepository.existsByGuardianUserIdAndStatus(2L, GuardianLinkStatus.APPROVED)).thenReturn(true);

        policy.validateApprovedGuardian(guardian);

        verify(patientGuardianLinkRepository).existsByGuardianUserIdAndStatus(2L, GuardianLinkStatus.APPROVED);
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
