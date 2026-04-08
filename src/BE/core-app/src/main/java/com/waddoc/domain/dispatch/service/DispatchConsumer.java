package com.waddoc.domain.dispatch.service;

import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.dispatch.entity.DispatchOutbox;
import com.waddoc.domain.dispatch.event.DispatchRequestMessage;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.mission.service.MissionCommandService;
import com.waddoc.domain.shared.event.BusinessEventOutboxService;
import com.waddoc.domain.vehicle.entity.Vehicle;
import com.waddoc.domain.vehicle.repository.VehicleRepository;
import com.waddoc.global.config.DemoModePolicy;
import com.waddoc.global.config.KafkaTopics;
import com.waddoc.global.monitoring.KafkaMonitoringMetrics;
import com.waddoc.global.util.KstTime;
import com.waddoc.shared.event.EventTypes;
import com.waddoc.shared.event.payload.DispatchAssignedEventPayload;
import com.waddoc.shared.event.payload.DispatchDelayedEventPayload;
import com.waddoc.shared.event.payload.RobotCommandDispatchRequestedPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;

/**
 * 배차 요청 이벤트를 소비해 차량 할당, 미션 생성, 후속 알림 이벤트 적재까지 처리한다.
 */
@Service
@RequiredArgsConstructor
public class DispatchConsumer {

    private static final EnumSet<MissionPhase> ACTIVE_PHASES =
            EnumSet.complementOf(EnumSet.of(MissionPhase.COMPLETED, MissionPhase.FAILED));
    private static final String CONSUMER_GROUP = "dispatch-group";

    private final DispatchOutboxRepository dispatchOutboxRepository;
    private final VehicleRepository vehicleRepository;
    private final MissionRepository missionRepository;
    private final MissionCommandService missionCommandService;
    private final BusinessEventOutboxService businessEventOutboxService;
    private final DemoModePolicy demoModePolicy;
    private final KafkaMonitoringMetrics kafkaMonitoringMetrics;
    private final Clock clock;

    @KafkaListener(topics = KafkaTopics.DISPATCH_REQUESTS_TOPIC, groupId = CONSUMER_GROUP)
    @Transactional
    public void consume(DispatchRequestMessage message) {
        kafkaMonitoringMetrics.recordConsumerProcessing(KafkaTopics.DISPATCH_REQUESTS_TOPIC, CONSUMER_GROUP, () -> {
            Optional<DispatchOutbox> outboxOptional =
                    dispatchOutboxRepository.findWithPatientByCareCasePublicId(message.caseId());
            if (outboxOptional.isEmpty()) {
                return;
            }

            DispatchOutbox outbox = outboxOptional.get();
            // 중복 소비, 취소 건, 이미 mission이 생성된 건은 멱등하게 종료한다.
            if (outbox.isCompleted()) {
                return;
            }
            if (outbox.getCareCase().getStatus() == CaseStatus.CANCELLED) {
                outbox.markCompleted();
                return;
            }
            if (demoModePolicy.isOperatorDispatchOnly()) {
                outbox.markRetryPending();
                return;
            }

            Optional<Mission> missionOptional = missionRepository.findByCareCase(outbox.getCareCase());
            if (missionOptional.isPresent() && missionOptional.get().getPhase() != MissionPhase.CREATED) {
                outbox.markCompleted();
                return;
            }

            Optional<Vehicle> vehicleOptional = vehicleRepository.findByRegionCodeAndIsActiveTrue(outbox.getRegionCode());
            // 가용 차량이 없으면 retry 상태로 넘기고 최초 1회만 지연 SMS를 보낸다.
            if (vehicleOptional.isEmpty()) {
                moveToRetry(outbox, null);
                return;
            }

            Vehicle vehicle = vehicleOptional.get();
            if (!vehicle.isOperational()
                    || missionRepository.existsByVehicleIdAndPhaseIn(vehicle.getPublicId(), ACTIVE_PHASES)) {
                moveToRetry(outbox, vehicle);
                return;
            }

            LocalDateTime now = LocalDateTime.now(KstTime.resolve(clock));
            Mission dispatchedMission = missionCommandService.createMissionForDispatch(
                    outbox.getCareCase(),
                    vehicle.getPublicId(),
                    outbox.getDestination(),
                    now,
                    missionOptional.map(Mission::getTargetWaypointNumber).orElse(null)
            );
            if (dispatchedMission.getPhase() == MissionPhase.CREATED) {
                dispatchedMission.updatePhase(MissionPhase.DISPATCHED, now);
                missionRepository.save(dispatchedMission);
            }
            outbox.markCompleted();

            businessEventOutboxService.enqueue(
                    EventTypes.DISPATCH_ASSIGNED_V1,
                    dispatchedMission.getPublicId(),
                    "corr_dispatch_assigned_" + dispatchedMission.getPublicId(),
                    new DispatchAssignedEventPayload(
                            dispatchedMission.getPublicId(),
                            outbox.getCareCase().getPublicId(),
                            vehicle.getPublicId(),
                            outbox.getCareCase().getPatient().getPhone(),
                            outbox.getDestination(),
                            now.atZone(KstTime.ZONE).toOffsetDateTime()
                    )
            );
            if (dispatchedMission.getTargetWaypointNumber() != null) {
                businessEventOutboxService.enqueue(
                        EventTypes.ROBOT_COMMAND_DISPATCH_REQUESTED_V1,
                        dispatchedMission.getPublicId(),
                        "corr_robot_dispatch_" + dispatchedMission.getPublicId(),
                        new RobotCommandDispatchRequestedPayload(
                                dispatchedMission.getPublicId(),
                                vehicle.getPublicId(),
                                dispatchedMission.getTargetWaypointNumber(),
                                outbox.getDestination()
                        )
                );
            }
        });
    }

    private void moveToRetry(DispatchOutbox outbox, Vehicle vehicle) {
        boolean shouldNotify = !outbox.isRetryPending();
        outbox.markRetryPending();
        if (shouldNotify) {
            LocalDateTime now = LocalDateTime.now(KstTime.resolve(clock));
            businessEventOutboxService.enqueue(
                    EventTypes.DISPATCH_DELAYED_V1,
                    outbox.getCareCase().getPublicId(),
                    "corr_dispatch_delayed_" + outbox.getCareCase().getPublicId(),
                    new DispatchDelayedEventPayload(
                            outbox.getCareCase().getPublicId(),
                            outbox.getRegionCode(),
                            outbox.getCareCase().getPatient().getPhone(),
                            buildDispatchDelayedMessage(vehicle),
                            now.atZone(KstTime.ZONE).toOffsetDateTime()
                    )
            );
        }
    }

    private String buildDispatchDelayedMessage(Vehicle vehicle) {
        String vehicleName = vehicle != null && vehicle.getDisplayName() != null && !vehicle.getDisplayName().isBlank()
                ? vehicle.getDisplayName()
                : "배차 차량";
        return String.format(
                "[왔닥]%n현재 %s 배정이 지연되고 있습니다.%n차량 상태가 복구되는 즉시 순차적으로 다시 배차해드리겠습니다.",
                vehicleName
        );
    }

}
