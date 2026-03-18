package com.waddoc.domain.admin.dto;

import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class GuardianLinkRejectionResponse {

    private String linkId;
    private GuardianLinkStatus status;
    private String processedByUserId;
    private LocalDateTime processedAt;

    public static GuardianLinkRejectionResponse from(PatientGuardianLink link) {
        return GuardianLinkRejectionResponse.builder()
                .linkId(link.getPublicId())
                .status(link.getStatus())
                .processedByUserId(link.getApprovedByUser() != null ? link.getApprovedByUser().getPublicId() : null)
                .processedAt(link.getApprovedAt())
                .build();
    }
}
