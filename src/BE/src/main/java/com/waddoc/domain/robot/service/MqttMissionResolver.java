package com.waddoc.domain.robot.service;

import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MqttMissionResolver {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final MissionRepository missionRepository;

    @Transactional(readOnly = true)
    public Optional<Mission> resolveMission(
            String missionId,
            String vehicleId,
            Integer goalWaypointNumber,
            Collection<MissionPhase> allowedPhases
    ) {
        String normalizedMissionId = normalize(missionId);
        if (normalizedMissionId != null) {
            return missionRepository.findWithDetailsByPublicId(normalizedMissionId)
                    .filter(mission -> allowedPhases.contains(mission.getPhase()));
        }

        String normalizedVehicleId = normalize(vehicleId);
        if (normalizedVehicleId == null) {
            return Optional.empty();
        }

        List<Mission> candidates = missionRepository.findCurrentVehicleMissions(
                normalizedVehicleId,
                LocalDate.now(KST),
                BookingStatus.CONFIRMED,
                allowedPhases
        ).stream()
                .filter(mission -> goalWaypointNumber == null || goalWaypointNumber.equals(mission.getTargetWaypointNumber()))
                .sorted(currentMissionComparator())
                .toList();

        return candidates.stream().findFirst();
    }

    private Comparator<Mission> currentMissionComparator() {
        return Comparator
                .comparingInt((Mission mission) -> phasePriority(mission.getPhase()))
                .thenComparing(mission -> mission.getCareCase().getBooking().getStartTime())
                .thenComparing(Mission::getPublicId);
    }

    private int phasePriority(MissionPhase phase) {
        return switch (phase) {
            case ARRIVED -> 0;
            case VERIFYING -> 1;
            case CONSULTING -> 2;
            case EN_ROUTE -> 3;
            case DISPATCHED -> 4;
            default -> 99;
        };
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
