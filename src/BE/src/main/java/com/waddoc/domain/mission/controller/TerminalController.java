package com.waddoc.domain.mission.controller;

import com.waddoc.domain.mission.dto.ClaimMissionTerminalRequest;
import com.waddoc.domain.mission.dto.DeviceTerminalBootstrapRequest;
import com.waddoc.domain.mission.dto.DeviceTerminalBootstrapResponse;
import com.waddoc.domain.mission.dto.IssueMissionTerminalTokenResponse;
import com.waddoc.domain.mission.dto.TerminalCheckInCandidatesRequest;
import com.waddoc.domain.mission.dto.TerminalCheckInCandidatesResponse;
import com.waddoc.domain.mission.service.DeviceTerminalTokenService;
import com.waddoc.domain.mission.service.TerminalCheckInService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/terminal")
@RequiredArgsConstructor
public class TerminalController {

    private final DeviceTerminalTokenService deviceTerminalTokenService;
    private final TerminalCheckInService terminalCheckInService;

    @PostMapping("/bootstrap-token")
    public ResponseEntity<DeviceTerminalBootstrapResponse> bootstrapToken(
            @Valid @RequestBody DeviceTerminalBootstrapRequest request
    ) {
        return ResponseEntity.ok(deviceTerminalTokenService.bootstrap(request));
    }

    @PostMapping("/check-in/candidates")
    @PreAuthorize("hasRole('DEVICE_TERMINAL')")
    public ResponseEntity<TerminalCheckInCandidatesResponse> lookupCandidates(
            @Valid @RequestBody TerminalCheckInCandidatesRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(terminalCheckInService.lookupCandidates(request, authentication));
    }

    @PostMapping("/missions/{missionId}/claim")
    @PreAuthorize("hasRole('DEVICE_TERMINAL')")
    public ResponseEntity<IssueMissionTerminalTokenResponse> claimMission(
            @PathVariable String missionId,
            @Valid @RequestBody ClaimMissionTerminalRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(terminalCheckInService.claimMission(missionId, request, authentication));
    }
}
