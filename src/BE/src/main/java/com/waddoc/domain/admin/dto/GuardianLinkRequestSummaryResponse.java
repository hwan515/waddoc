package com.waddoc.domain.admin.dto;

import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class GuardianLinkRequestSummaryResponse {

    private String linkId;
    private GuardianLinkStatus status;
    private String guardianUserId;
    private String guardianName;
    private String patientId;
    private String patientName;
    private String patientPhone;
    private String relation;
    private LocalDateTime requestedAt;

    public static GuardianLinkRequestSummaryResponse from(PatientGuardianLink link) {
        return GuardianLinkRequestSummaryResponse.builder()
                .linkId(link.getPublicId())
                .status(link.getStatus())
                .guardianUserId(link.getGuardianUser().getPublicId())
                .guardianName(link.getGuardianUser().getName())
                .patientId(link.getPatient().getPublicId())
                .patientName(link.getPatient().getName())
                .patientPhone(link.getPatient().getPhone())
                .relation(link.getRelation())
                .requestedAt(link.getRequestedAt())
                .build();
    }
}
