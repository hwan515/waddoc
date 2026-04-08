package com.waddoc.domain.admin.dto;

import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class GuardianLinkApprovalResponse {

    private String linkId;
    private GuardianLinkStatus status;
    private String patientId;
    private String guardianUserId;
    private String relation;
    private String approvedByUserId;
    private LocalDateTime approvedAt;

    public static GuardianLinkApprovalResponse from(PatientGuardianLink link) {
        return GuardianLinkApprovalResponse.builder()
                .linkId(link.getPublicId())
                .status(link.getStatus())
                .patientId(link.getPatient().getPublicId())
                .guardianUserId(link.getGuardianUser().getPublicId())
                .relation(link.getRelation())
                .approvedByUserId(link.getApprovedByUser() != null ? link.getApprovedByUser().getPublicId() : null)
                .approvedAt(link.getApprovedAt())
                .build();
    }
}
