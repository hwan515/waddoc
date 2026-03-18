package com.waddoc.domain.patient.controller;

import com.waddoc.domain.patient.dto.CreatePatientRequest;
import com.waddoc.domain.patient.dto.CreatePatientResponse;
import com.waddoc.domain.patient.service.PatientCommandService;
import com.waddoc.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PatientController {

    private final PatientCommandService patientCommandService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreatePatientResponse> createPatient(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @ModelAttribute CreatePatientRequest request
    ) {
        CreatePatientResponse response = patientCommandService.createPatient(authenticatedUser, request);
        return ResponseEntity.created(URI.create("/api/v1/patients/" + response.getPatientId())).body(response);
    }
}
