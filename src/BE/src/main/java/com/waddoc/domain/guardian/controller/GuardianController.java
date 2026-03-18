package com.waddoc.domain.guardian.controller;

import com.waddoc.domain.guardian.dto.GuardianConsultationSummariesResponse;
import com.waddoc.domain.guardian.dto.GuardianPatientsResponse;
import com.waddoc.domain.guardian.service.GuardianQueryService;
import com.waddoc.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/guardians")
@RequiredArgsConstructor
@PreAuthorize("hasRole('GUARDIAN')")
public class GuardianController {

    private final GuardianQueryService guardianQueryService;

    @GetMapping("/patients")
    public ResponseEntity<GuardianPatientsResponse> getLinkedPatients(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return ResponseEntity.ok(guardianQueryService.getLinkedPatients(authenticatedUser));
    }

    @GetMapping("/patients/{patientId}/summaries")
    public ResponseEntity<GuardianConsultationSummariesResponse> getConsultationSummaries(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable String patientId
    ) {
        return ResponseEntity.ok(guardianQueryService.getConsultationSummaries(authenticatedUser, patientId));
    }
}
