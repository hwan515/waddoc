package com.waddoc.domain.guardian.dto;

import com.waddoc.domain.patient.entity.PatientGender;
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class GuardianPatientResponse {

    private String patientId;
    private String name;
    private String birthDate6;
    private String phone;
    private String regionCode;
    private String address;
    private PatientGender gender;
    private String relation;
    private LocalDate approvedAt;

    public static GuardianPatientResponse from(PatientGuardianLink link) {
        return GuardianPatientResponse.builder()
                .patientId(link.getPatient().getPublicId())
                .name(link.getPatient().getName())
                .birthDate6(link.getPatient().getBirthDate6())
                .phone(link.getPatient().getPhone())
                .regionCode(link.getPatient().getRegionCode())
                .address(link.getPatient().getAddress())
                .gender(link.getPatient().getGender())
                .relation(link.getRelation())
                .approvedAt(link.getApprovedAt() != null ? link.getApprovedAt().toLocalDate() : null)
                .build();
    }
}
