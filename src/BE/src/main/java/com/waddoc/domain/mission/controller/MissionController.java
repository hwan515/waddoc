package com.waddoc.domain.mission.controller;

import com.waddoc.domain.mission.dto.CreateMissionRequest;
import com.waddoc.domain.mission.dto.CreateMissionResponse;
import com.waddoc.domain.mission.dto.MissionDetailResponse;
import com.waddoc.domain.mission.dto.MissionListResponse;
import com.waddoc.domain.mission.dto.UpdateMissionPhaseRequest;
import com.waddoc.domain.mission.dto.UpdateMissionPhaseResponse;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.service.MissionCommandService;
import com.waddoc.domain.mission.service.MissionQueryService;
import com.waddoc.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/missions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MissionController {

    private final MissionCommandService missionCommandService;
    private final MissionQueryService missionQueryService;

    @PostMapping
    public ResponseEntity<CreateMissionResponse> createMission(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody CreateMissionRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(missionCommandService.createMission(authenticatedUser, request));
    }

    @GetMapping
    public ResponseEntity<MissionListResponse> getMissions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) MissionPhase phase
    ) {
        return ResponseEntity.ok(missionQueryService.getMissions(authenticatedUser, date, phase));
    }

    @GetMapping("/{missionId}")
    public ResponseEntity<MissionDetailResponse> getMissionDetail(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable String missionId
    ) {
        return ResponseEntity.ok(missionQueryService.getMissionDetail(authenticatedUser, missionId));
    }

    @PatchMapping("/{missionId}")
    public ResponseEntity<UpdateMissionPhaseResponse> updateMissionPhase(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable String missionId,
            @Valid @RequestBody UpdateMissionPhaseRequest request
    ) {
        return ResponseEntity.ok(missionCommandService.updateMissionPhase(authenticatedUser, missionId, request));
    }
}
