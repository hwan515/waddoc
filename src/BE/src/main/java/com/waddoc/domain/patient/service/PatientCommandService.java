package com.waddoc.domain.patient.service;

import com.waddoc.domain.patient.dto.CreatePatientRequest;
import com.waddoc.domain.patient.dto.CreatePatientResponse;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.repository.PatientRepository;
import com.waddoc.domain.user.entity.User;
import com.waddoc.domain.user.repository.UserRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PatientCommandService {

    private final AccessControlService accessControlService;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final PatientReferenceImageStorageService patientReferenceImageStorageService;

    @Transactional
    public CreatePatientResponse createPatient(AuthenticatedUser authenticatedUser, CreatePatientRequest request) {
        User adminUser = getAdminUser(authenticatedUser);
        String phone = request.getPhone().trim();

        if (patientRepository.findByPhone(phone).isPresent()) {
            throw new BusinessException(ErrorCode.PATIENT_PHONE_CONFLICT);
        }

        Patient patient = patientRepository.save(
                Patient.builder()
                        .name(request.getName().trim())
                        .birthDate(request.getBirthDate())
                        .regionCode(request.getRegionCode().trim())
                        .address(normalizeNullable(request.getAddress()))
                        .phone(phone)
                        .build()
        );

        MultipartFile referenceImage = request.getReferenceImage();
        if (referenceImage != null && !referenceImage.isEmpty()) {
            String referenceImagePath = patientReferenceImageStorageService.save(patient.getPublicId(), referenceImage);
            patient.updateReferenceImage(referenceImagePath, adminUser);
        }

        return CreatePatientResponse.from(patient);
    }

    private User getAdminUser(AuthenticatedUser authenticatedUser) {
        accessControlService.assertAdmin(authenticatedUser);
        return userRepository.findByPublicId(authenticatedUser.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
