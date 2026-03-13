package com.waddoc.domain.intake.controller;

import com.waddoc.domain.intake.dto.IdentifyByCallerNumberRequest;
import com.waddoc.domain.intake.dto.IdentifyByInfoRequest;
import com.waddoc.domain.intake.dto.IdentifyByPhoneRequest;
import com.waddoc.domain.intake.dto.IdentifyPatientResponse;
import com.waddoc.domain.intake.service.PatientIdentifyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/intake/sessions/{intakeSessionId}/identify")
@RequiredArgsConstructor
public class PatientIdentifyController {

    private final PatientIdentifyService patientIdentifyService;

    @PostMapping("/by-caller-number")
    public ResponseEntity<IdentifyPatientResponse> identifyByCallerNumber(
            @PathVariable String intakeSessionId,
            @Valid @RequestBody IdentifyByCallerNumberRequest request) {
        IdentifyPatientResponse response =
                patientIdentifyService.identifyByCallerNumber(intakeSessionId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/by-phone")
    public ResponseEntity<IdentifyPatientResponse> identifyByPhone(
            @PathVariable String intakeSessionId,
            @Valid @RequestBody IdentifyByPhoneRequest request) {
        IdentifyPatientResponse response =
                patientIdentifyService.identifyByPhone(intakeSessionId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/by-info")
    public ResponseEntity<IdentifyPatientResponse> identifyByInfo(
            @PathVariable String intakeSessionId,
            @Valid @RequestBody IdentifyByInfoRequest request) {
        IdentifyPatientResponse response =
                patientIdentifyService.identifyByInfo(intakeSessionId, request);
        return ResponseEntity.ok(response);
    }
}
