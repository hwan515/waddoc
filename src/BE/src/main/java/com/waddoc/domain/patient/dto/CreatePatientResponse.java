package com.waddoc.domain.patient.dto;

import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientGender;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CreatePatientResponse {

    private String patientId;
    private String name;
    private String birthDate6;
    private String phone;
    private PatientGender gender;
    private boolean referenceImageRegistered;

    public static CreatePatientResponse from(Patient patient) {
        return CreatePatientResponse.builder()
                .patientId(patient.getPublicId())
                .name(patient.getName())
                .birthDate6(patient.getBirthDate6())
                .phone(patient.getPhone())
                .gender(patient.getGender())
                .referenceImageRegistered(patient.hasReferenceImage())
                .build();
    }
}
