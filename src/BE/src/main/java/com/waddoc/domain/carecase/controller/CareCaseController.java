package com.waddoc.domain.carecase.controller;

import com.waddoc.domain.carecase.dto.CaseDetailResponse;
import com.waddoc.domain.carecase.dto.DoctorCaseListResponse;
import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.carecase.service.CareCaseQueryService;
import com.waddoc.domain.consultation.dto.CreateConsultationSessionResponse;
import com.waddoc.domain.consultation.service.ConsultationSessionCommandService;
import com.waddoc.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/cases")
@RequiredArgsConstructor
public class CareCaseController {

    private final CareCaseQueryService careCaseQueryService;
    private final ConsultationSessionCommandService consultationSessionCommandService;

    @GetMapping("/{caseId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<CaseDetailResponse> getCaseDetail(
            @PathVariable String caseId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return ResponseEntity.ok(careCaseQueryService.getCaseDetail(caseId, authenticatedUser));
    }

    @GetMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<DoctorCaseListResponse> getAssignedCases(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(careCaseQueryService.getAssignedCases(authenticatedUser, status, date));
    }

    @PostMapping("/{caseId}/sessions")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<CreateConsultationSessionResponse> createConsultationSession(
            @PathVariable String caseId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        ConsultationSessionCommandService.CreateSessionResult result =
                consultationSessionCommandService.createOrReuseSession(caseId, authenticatedUser);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.response());
    }
}
