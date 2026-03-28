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
                .message("가입 요청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다.")
                .build();
    }

    @Getter
    @Builder
    public static class PatientSummary {
        private String nameMasked;
        private String birthDate6Masked;

        public static PatientSummary from(Patient patient) {
            return PatientSummary.builder()
                    .nameMasked(maskName(patient.getName()))
                    .birthDate6Masked(maskBirthDate6(patient.getBirthDate6()))
                    .build();
        }

        private static String maskName(String name) {
            if (name == null || name.isBlank()) {
                return "";
            }
            if (name.length() == 1) {
                return name;
            }
            if (name.length() == 2) {
                return name.charAt(0) + "*";
            }
            return name.charAt(0) + "*" + name.charAt(name.length() - 1);
        }

        private static String maskBirthDate6(String birthDate6) {
            if (birthDate6 == null || birthDate6.length() != 6) {
                return "******";
            }
            return birthDate6.substring(0, 4) + "**";
        }
    }
}
