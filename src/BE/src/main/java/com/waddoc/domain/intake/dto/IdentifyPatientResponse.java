package com.waddoc.domain.intake.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.waddoc.domain.patient.entity.Patient;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class IdentifyPatientResponse {

    private boolean identified;
    private PatientSummary patient;

    @Getter
    @Builder
    public static class PatientSummary {
        private String patientId;
        private String name;
        private String birthDate6;
        private String regionCode;
    }

    public static IdentifyPatientResponse identified(Patient patient) {
        return IdentifyPatientResponse.builder()
                .identified(true)
                .patient(PatientSummary.builder()
                        .patientId(patient.getPublicId())
                        .name(patient.getName())
                        .birthDate6(patient.getBirthDate6())
                        .regionCode(patient.getRegionCode())
                        .build())
                .build();
    }

    public static IdentifyPatientResponse notIdentified() {
        return IdentifyPatientResponse.builder()
                .identified(false)
                .patient(null)
                .build();
    }
}
