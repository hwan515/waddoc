package com.waddoc.domain.admin.dto;

import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientGender;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminPatientSummaryResponse {

    private String patientId;
    private String name;
    private String birthDate6;
    private String phone;
    private String regionCode;
    private String address;
    private PatientGender gender;

    public static AdminPatientSummaryResponse from(Patient patient) {
        return AdminPatientSummaryResponse.builder()
                .patientId(patient.getPublicId())
                .name(patient.getName())
                .birthDate6(patient.getBirthDate6())
                .phone(patient.getPhone())
                .regionCode(patient.getRegionCode())
                .address(patient.getAddress())
                .gender(patient.getGender())
                .build();
    }
}
