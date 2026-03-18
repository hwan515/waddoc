package com.waddoc.domain.auth.dto;

import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GuardianSignupResponse {

    private String userId;
    private String linkId;
    private String status;
    private PatientSummary patient;
    private String message;

    public static GuardianSignupResponse of(PatientGuardianLink link) {
        return GuardianSignupResponse.builder()
                .userId(link.getGuardianUser().getPublicId())
                .linkId(link.getPublicId())
                .status(link.getStatus().name())
                .patient(PatientSummary.from(link.getPatient()))
                .message("관리자 승인 후 로그인할 수 있습니다.")
                .build();
    }

    @Getter
    @Builder
    public static class PatientSummary {
        private String patientId;
        private String name;
        private String birthDate6;

        public static PatientSummary from(Patient patient) {
            return PatientSummary.builder()
                    .patientId(patient.getPublicId())
                    .name(patient.getName())
                    .birthDate6(patient.getBirthDate6())
                    .build();
        }
    }
}
