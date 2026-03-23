package com.waddoc.domain.mission.controller;

import com.waddoc.domain.consultation.dto.IssuePatientTokenResponse;
import com.waddoc.domain.consultation.service.ConsultationPatientTokenService;
import com.waddoc.domain.mission.dto.CreateMissionRequest;
import com.waddoc.domain.mission.dto.CreateMissionResponse;
import com.waddoc.domain.mission.dto.IssueMissionTerminalTokenResponse;
import com.waddoc.domain.mission.dto.MissionDetailResponse;
import com.waddoc.domain.mission.dto.MissionIdentityCheckResponse;
import com.waddoc.domain.mission.dto.MissionListResponse;
import com.waddoc.domain.mission.dto.UpsertMissionVitalMeasurementResponse;
import com.waddoc.domain.mission.dto.UpdateMissionPhaseRequest;
import com.waddoc.domain.mission.dto.UpdateMissionPhaseResponse;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.service.MissionCommandService;
import com.waddoc.domain.mission.service.MissionIdentityCheckService;
import com.waddoc.domain.mission.service.MissionQueryService;
import com.waddoc.domain.mission.service.MissionTerminalTokenService;
import com.waddoc.domain.mission.service.MissionVitalMeasurementService;
import com.waddoc.domain.vital.dto.UpsertVitalMeasurementRequest;
import com.waddoc.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * 왕진 미션의 생성, 조회, 단계 변경과 현장 단말 연동 API를 묶은 컨트롤러다.
 */
@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
public class MissionController {

    private final MissionCommandService missionCommandService;
    private final MissionIdentityCheckService missionIdentityCheckService;
    private final MissionQueryService missionQueryService;
    private final MissionTerminalTokenService missionTerminalTokenService;
    private final ConsultationPatientTokenService consultationPatientTokenService;
    private final MissionVitalMeasurementService missionVitalMeasurementService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CreateMissionResponse> createMission(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateMissionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(missionCommandService.createMission(authenticatedUser, request));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MissionListResponse> getMissions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) MissionPhase phase
    ) {
        return ResponseEntity.ok(missionQueryService.getMissions(authenticatedUser, date, phase));
    }

    @GetMapping("/{missionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MissionDetailResponse> getMissionDetail(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable String missionId
    ) {
        return ResponseEntity.ok(missionQueryService.getMissionDetail(authenticatedUser, missionId));
    }

    @PatchMapping("/{missionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UpdateMissionPhaseResponse> updateMissionPhase(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable String missionId,
            @Valid @RequestBody UpdateMissionPhaseRequest request
    ) {
        return ResponseEntity.ok(missionCommandService.updateMissionPhase(authenticatedUser, missionId, request));
    }

    @PostMapping("/{missionId}/terminal/token")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    public ResponseEntity<IssueMissionTerminalTokenResponse> issueMissionTerminalToken(
            @PathVariable String missionId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser
    ) {
        return ResponseEntity.ok(missionTerminalTokenService.issueToken(missionId, authenticatedUser));
    }

    @PostMapping("/{missionId}/identity-check")
    @PreAuthorize("hasAnyRole('ADMIN', 'MISSION_TERMINAL')")
    public ResponseEntity<MissionIdentityCheckResponse> verifyMissionIdentity(
            @PathVariable String missionId,
            @RequestPart("faceImage") MultipartFile faceImage,
            @RequestPart("idCardImage") MultipartFile idCardImage,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                missionIdentityCheckService.verify(missionId, faceImage, idCardImage, authentication)
        );
    }

    @PostMapping("/{missionId}/participants/patient/token")
    @PreAuthorize("hasAnyRole('ADMIN', 'MISSION_TERMINAL')")
    public ResponseEntity<IssuePatientTokenResponse> issuePatientTokenByMission(
            @PathVariable String missionId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                consultationPatientTokenService.issuePatientTokenByMission(missionId, authentication)
        );
    }

    @PutMapping("/{missionId}/vitals")
    @PreAuthorize("hasAnyRole('ADMIN', 'MISSION_TERMINAL')")
    public ResponseEntity<UpsertMissionVitalMeasurementResponse> upsertMissionVitals(
            @PathVariable String missionId,
            @Valid @RequestBody UpsertVitalMeasurementRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                missionVitalMeasurementService.upsert(missionId, request, authentication)
        );
    }
}
