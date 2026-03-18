package com.waddoc.domain.patient.service;

import com.waddoc.domain.patient.dto.CreatePatientRequest;
import com.waddoc.domain.patient.dto.CreatePatientResponse;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.repository.PatientRepository;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.domain.user.repository.UserRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientCommandServiceTest {

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatientReferenceImageStorageService patientReferenceImageStorageService;

    @InjectMocks
    private PatientCommandService patientCommandService;

    @Test
    void createPatientWithoutReferenceImageReturnsCreatedResponse() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        User adminUser = buildAdminUser();
        CreatePatientRequest request = buildRequest();

        when(userRepository.findByPublicId("usr_admin")).thenReturn(Optional.of(adminUser));
        when(patientRepository.findByPhone("01012345678")).thenReturn(Optional.empty());
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePatientResponse response = patientCommandService.createPatient(admin, request);

        assertThat(response.getPatientId()).startsWith("pat_");
        assertThat(response.getName()).isEqualTo("Hong Gil-dong");
        assertThat(response.getBirthDate6()).isEqualTo("580315");
        assertThat(response.getPhone()).isEqualTo("01012345678");
        assertThat(response.isReferenceImageRegistered()).isFalse();
        verify(accessControlService).assertAdmin(admin);
    }

    @Test
    void createPatientWithReferenceImageStoresFileAndMarksRegistered() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        User adminUser = buildAdminUser();
        CreatePatientRequest request = buildRequest();
        request.setReferenceImage(new MockMultipartFile(
                "referenceImage",
                "face.jpg",
                "image/jpeg",
                "image-bytes".getBytes()
        ));

        when(userRepository.findByPublicId("usr_admin")).thenReturn(Optional.of(adminUser));
        when(patientRepository.findByPhone("01012345678")).thenReturn(Optional.empty());
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(patientReferenceImageStorageService.save(any(), any()))
                .thenReturn("patients/pat_example/reference.jpg");

        CreatePatientResponse response = patientCommandService.createPatient(admin, request);

        assertThat(response.isReferenceImageRegistered()).isTrue();
        verify(patientReferenceImageStorageService).save(any(), any());
    }

    @Test
    void createPatientWithDuplicatePhoneThrowsConflict() {
        AuthenticatedUser admin = new AuthenticatedUser("usr_admin", Role.ADMIN);
        User adminUser = buildAdminUser();
        CreatePatientRequest request = buildRequest();

        when(userRepository.findByPublicId("usr_admin")).thenReturn(Optional.of(adminUser));
        when(patientRepository.findByPhone("01012345678"))
                .thenReturn(Optional.of(Patient.builder()
                        .name("Existing")
                        .birthDate(LocalDate.of(1950, 1, 1))
                        .regionCode("ULLEUNG")
                        .address("Existing address")
                        .phone("01012345678")
                        .build()));

        assertThatThrownBy(() -> patientCommandService.createPatient(admin, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PATIENT_PHONE_CONFLICT);

        verify(patientRepository, never()).save(any(Patient.class));
        verify(patientReferenceImageStorageService, never()).save(any(), any());
    }

    private CreatePatientRequest buildRequest() {
        CreatePatientRequest request = new CreatePatientRequest();
        request.setName("Hong Gil-dong");
        request.setBirthDate(LocalDate.of(1958, 3, 15));
        request.setPhone("01012345678");
        request.setRegionCode("ULLEUNG");
        request.setAddress("Ulleung-eup");
        return request;
    }

    private User buildAdminUser() {
        User user = User.builder()
                .username("admin")
                .passwordHash("encoded-password")
                .name("Admin")
                .role(Role.ADMIN)
                .build();
        setField(user, "publicId", "usr_admin");
        return user;
    }

    private void setField(Object target, String fieldName, Object value) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("Field not found: " + fieldName);
    }
}
