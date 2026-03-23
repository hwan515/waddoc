package com.waddoc.domain.mission.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.mission.dto.UpsertMissionVitalMeasurementResponse;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.vital.dto.UpsertVitalMeasurementRequest;
import com.waddoc.domain.vital.dto.VitalMeasurementResponse;
import com.waddoc.domain.vital.service.VitalMeasurementCommandService;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.authorization.AccessActor;
import com.waddoc.global.security.authorization.AccessControlService;
import com.waddoc.global.security.jwt.MissionTerminalScopes;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MissionVitalMeasurementService {

    private static final EnumSet<MissionPhase> WRITABLE_MISSION_PHASES =
            EnumSet.of(MissionPhase.ARRIVED, MissionPhase.VERIFYING, MissionPhase.CONSULTING);

    private final MissionRepository missionRepository;
    private final AccessControlService accessControlService;
    private final VitalMeasurementCommandService vitalMeasurementCommandService;
    private final AuditLogService auditLogService;

    @Transactional
    public UpsertMissionVitalMeasurementResponse upsert(
            String missionId,
            UpsertVitalMeasurementRequest request,
            Authentication authentication
    ) {
        if (!request.hasAnyMeasurementValue()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        AccessActor actor = accessControlService.assertAdminOrMissionTerminal(
                authentication,
                missionId,
                MissionTerminalScopes.VITALS_WRITE
        );

        Mission mission = missionRepository.findWithDetailsByPublicId(missionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));

        if (!WRITABLE_MISSION_PHASES.contains(mission.getPhase())) {
            throw new BusinessException(ErrorCode.MISSION_NOT_READY);
        }

        VitalMeasurementResponse vitals = vitalMeasurementCommandService.upsert(
                mission.getCareCase(),
                request
        );

        auditLogService.log(
                "MISSION_VITALS_UPSERTED",
                "MISSION",
                mission.getPublicId(),
                "corr_mis_" + mission.getPublicId(),
                actor.actorId(),
                actor.actorRole(),
                Map.of(
                        "caseId", mission.getCareCase().getPublicId(),
                        "missionPhase", mission.getPhase().name(),
                        "measuredAt", String.valueOf(vitals.getMeasuredAt())
                )
        );

        return UpsertMissionVitalMeasurementResponse.of(mission, vitals);
    }
}
