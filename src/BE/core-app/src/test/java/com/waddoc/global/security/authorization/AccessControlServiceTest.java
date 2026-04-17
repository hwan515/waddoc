package com.waddoc.global.security.authorization;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.doctor.repository.DoctorProfileRepository;
import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.repository.PatientGuardianLinkRepository;
import com.waddoc.domain.patient.repository.PatientRepository;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessControlServiceTest {

    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    @Mock
    private PatientGuardianLinkRepository patientGuardianLinkRepository;

    @Mock
    private PatientRepository patientRepository;

    @InjectMocks
    private AccessControlService accessControlService;

    @Test
    void getDoctorProfileOrThrowReturnsProfileForDoctorPrincipal() {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser("usr_doctor", Role.DOCTOR);
        DoctorProfile doctorProfile = DoctorProfile.builder()
                .user(buildUser("doctor", "김의사", Role.DOCTOR))
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();

        when(doctorProfileRepository.findByUserPublicId("usr_doctor")).thenReturn(Optional.of(doctorProfile));

        DoctorProfile result = accessControlService.getDoctorProfileOrThrow(authenticatedUser);

        assertThat(result).isSameAs(doctorProfile);
    }

    @Test
    void assertAssignedDoctorOrAdminThrowsForUnassignedDoctor() {
        User assignedDoctorUser = buildUser("assigned", "배정의사", Role.DOCTOR);
        DoctorProfile assignedDoctor = DoctorProfile.builder()
                .user(assignedDoctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();
        setField(assignedDoctor, "id", 1L);

        DoctorProfile otherDoctor = DoctorProfile.builder()
                .user(buildUser("other", "다른의사", Role.DOCTOR))
                .department("INTERNAL_MEDICINE")
                .departmentName("내과")
                .build();
        setField(otherDoctor, "id", 2L);

        CareCase careCase = CareCase.builder()
                .booking(null)
                .patient(buildPatient("홍길동", "01011112222"))
                .doctor(assignedDoctor)
                .intakeSession(null)
                .build();

        when(doctorProfileRepository.findByUserPublicId("usr_other")).thenReturn(Optional.of(otherDoctor));

        assertThatThrownBy(() -> accessControlService.assertAssignedDoctorOrAdmin(
                new AuthenticatedUser("usr_other", Role.DOCTOR),
                careCase
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.AUTH_FORBIDDEN);
    }

    @Test
    void getGuardianLinkedPatientOrThrowReturnsPatientWhenLinkExists() {
        Patient patient = buildPatient("홍길동", "01011112222");
        AuthenticatedUser authenticatedUser = new AuthenticatedUser("usr_guardian", Role.GUARDIAN);

        when(patientGuardianLinkRepository.existsByPatientPublicIdAndGuardianUserPublicIdAndStatus(
                patient.getPublicId(),
                "usr_guardian",
                GuardianLinkStatus.APPROVED
        )).thenReturn(true);
        when(patientRepository.findByPublicId(patient.getPublicId())).thenReturn(Optional.of(patient));

        Patient result = accessControlService.getGuardianLinkedPatientOrThrow(authenticatedUser, patient.getPublicId());

        assertThat(result).isSameAs(patient);
    }

    @Test
    void getGuardianLinkedPatientOrThrowThrowsWhenGuardianIsNotLinked() {
        Patient patient = buildPatient("홍길동", "01011112222");

        when(patientGuardianLinkRepository.existsByPatientPublicIdAndGuardianUserPublicIdAndStatus(
                patient.getPublicId(),
                "usr_guardian",
                GuardianLinkStatus.APPROVED
        )).thenReturn(false);

        assertThatThrownBy(() -> accessControlService.getGuardianLinkedPatientOrThrow(
                new AuthenticatedUser("usr_guardian", Role.GUARDIAN),
                patient.getPublicId()
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.GUARDIAN_NOT_LINKED);
    }

    private User buildUser(String username, String name, Role role) {
        User user = User.builder()
                .username(username)
                .passwordHash("encoded-password")
                .name(name)
                .role(role)
                .build();
        return user;
    }

    private Patient buildPatient(String name, String phone) {
        return Patient.builder()
                .name(name)
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .phone(phone)
                .build();
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
