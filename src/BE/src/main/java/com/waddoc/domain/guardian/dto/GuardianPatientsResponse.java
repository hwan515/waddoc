package com.waddoc.domain.guardian.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class GuardianPatientsResponse {

    private List<GuardianPatientResponse> patients;

    public static GuardianPatientsResponse of(List<GuardianPatientResponse> patients) {
        return GuardianPatientsResponse.builder()
                .patients(patients)
                .build();
    }
}
