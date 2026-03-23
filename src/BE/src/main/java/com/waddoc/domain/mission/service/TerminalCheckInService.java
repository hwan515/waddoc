package com.waddoc.domain.mission.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.mission.dto.ClaimMissionTerminalRequest;
import com.waddoc.domain.mission.dto.IssueMissionTerminalTokenResponse;
import com.waddoc.domain.mission.dto.TerminalCheckInCandidatesRequest;
import com.waddoc.domain.mission.dto.TerminalCheckInCandidatesResponse;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.DeviceTerminalPrincipal;
import com.waddoc.global.security.authorization.AccessControlService;
import com.waddoc.global.security.jwt.DeviceTerminalScopes;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TerminalCheckInService {

    private static final EnumSet<MissionPhase> CLAIMABLE_PHASES =
            EnumSet.of(MissionPhase.ARRIVED, MissionPhase.VERIFYING, MissionPhase.CONSULTING);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final MissionRepository missionRepository;
    private final AccessControlService accessControlService;
    private final MissionTerminalTokenService missionTerminalTokenService;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public TerminalCheckInCandidatesResponse lookupCandidates(
            TerminalCheckInCandidatesRequest request,
            Authentication authentication
    ) {
        DeviceTerminalPrincipal principal = accessControlService.assertDeviceTerminalPrincipal(
                authentication,
                DeviceTerminalScopes.CHECK_IN_CANDIDATES
        );

        // 차량 단말에는 최소 식별 정보만 내려주고, 실제 본인확인은 다음 단계에서 수행한다.
        List<Mission> candidates = missionRepository.findTerminalCandidates(
                request.getPhoneLast4(),
                request.getBirthDate6(),
                BookingStatus.CONFIRMED,
                CLAIMABLE_PHASES
        ).stream()
                .filter(mission -> isMissionAccessibleToTerminal(mission, principal))
                .toList();

        auditLogService.log(
                "DEVICE_TERMINAL_CHECK_IN_LOOKUP",
                "TERMINAL",
                principal.terminalId(),
                "corr_terminal_lookup_" + principal.terminalId(),
                principal.terminalId(),
                principal.actorRole(),
                Map.of(
                        "phoneLast4", request.getPhoneLast4(),
                        "birthDate6Masked", maskBirthDate6(request.getBirthDate6()),
                        "candidateCount", candidates.size(),
                        "vehicleId", principal.vehicleId() != null ? principal.vehicleId() : "",
                        "regionCode", principal.regionCode() != null ? principal.regionCode() : ""
                )
        );

        return TerminalCheckInCandidatesResponse.of(
                candidates.stream()
                        .map(this::toCandidate)
                        .toList()
        );
    }

    @Transactional
    public IssueMissionTerminalTokenResponse claimMission(
            String missionId,
            ClaimMissionTerminalRequest request,
            Authentication authentication
    ) {
        DeviceTerminalPrincipal principal = accessControlService.assertDeviceTerminalPrincipal(
                authentication,
                DeviceTerminalScopes.CLAIM_MISSION
        );

        Mission mission = missionRepository.findWithDetailsByPublicId(missionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));

        // 후보 조회 때 쓴 식별값으로 다시 한 번 묶어, 다른 미션을 임의로 claim하지 못하게 한다.
        if (!isClaimableForRequest(mission, request, principal)) {
            throw new BusinessException(ErrorCode.TERMINAL_MISSION_CLAIM_FORBIDDEN);
        }

        bindMissionToTerminalIfNeeded(mission, principal);

        auditLogService.log(
                "DEVICE_TERMINAL_MISSION_CLAIMED",
                "MISSION",
                mission.getPublicId(),
                "corr_terminal_claim_" + mission.getPublicId(),
                principal.terminalId(),
                principal.actorRole(),
                Map.of(
                        "phoneLast4", request.getPhoneLast4(),
                        "birthDate6Masked", maskBirthDate6(request.getBirthDate6()),
                        "vehicleId", mission.getVehicleId() != null ? mission.getVehicleId() : "",
                        "regionCode", principal.regionCode() != null ? principal.regionCode() : ""
                )
        );

        return missionTerminalTokenService.issueTokenForDeviceClaim(mission.getPublicId(), principal.terminalId());
    }

    private TerminalCheckInCandidatesResponse.Candidate toCandidate(Mission mission) {
        Booking booking = mission.getCareCase().getBooking();
        return TerminalCheckInCandidatesResponse.Candidate.builder()
                .missionId(mission.getPublicId())
                .patientMaskedName(maskPersonName(mission.getCareCase().getPatient().getName()))
                .appointmentDate(booking.getAppointmentDate().format(DATE_FORMATTER))
                .appointmentTime(booking.getStartTime().format(TIME_FORMATTER))
                .doctorMaskedName(maskPersonName(mission.getCareCase().getDoctor().getUser().getName()))
                .missionPhase(mission.getPhase().name())
                .build();
    }

    private boolean isClaimableForRequest(
            Mission mission,
            ClaimMissionTerminalRequest request,
            DeviceTerminalPrincipal principal
    ) {
        Booking booking = mission.getCareCase().getBooking();
        String patientPhone = mission.getCareCase().getPatient().getPhone();

        // TODO: reintroduce appointment-date/window validation when terminal-to-schedule binding is finalized.
        return isMissionAccessibleToTerminal(mission, principal)
                && CLAIMABLE_PHASES.contains(mission.getPhase())
                && booking.getStatus() == BookingStatus.CONFIRMED
                && patientPhone != null
                && patientPhone.endsWith(request.getPhoneLast4())
                && request.getBirthDate6().equals(mission.getCareCase().getPatient().getBirthDate6());
    }

    private boolean isMissionAccessibleToTerminal(Mission mission, DeviceTerminalPrincipal principal) {
        if (principal.hasRegionBinding()) {
            String patientRegionCode = normalize(mission.getCareCase().getPatient().getRegionCode());
            if (!principal.regionCode().equals(patientRegionCode)) {
                return false;
            }
        }

        if (!principal.hasVehicleBinding()) {
            return true;
        }

        String assignedVehicleId = normalize(mission.getVehicleId());
        return assignedVehicleId == null || principal.vehicleId().equals(assignedVehicleId);
    }

    private void bindMissionToTerminalIfNeeded(Mission mission, DeviceTerminalPrincipal principal) {
        if (!principal.hasVehicleBinding()) {
            return;
        }

        if (normalize(mission.getVehicleId()) == null) {
            // 즉시 진료로 먼저 열린 mission은 첫 차량 단말 claim 시점에 단말 차량으로 고정한다.
            mission.assignVehicle(principal.vehicleId());
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String maskPersonName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        if (name.length() == 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*" + name.charAt(name.length() - 1);
    }

    private String maskBirthDate6(String birthDate6) {
        if (birthDate6 == null || birthDate6.length() != 6) {
            return "******";
        }
        return birthDate6.substring(0, 2) + "****";
    }
}
