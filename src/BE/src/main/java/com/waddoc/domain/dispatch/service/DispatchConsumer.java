package com.waddoc.domain.dispatch.service;

import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.dispatch.entity.DispatchOutbox;
import com.waddoc.domain.dispatch.event.DispatchRequestMessage;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.mission.service.MissionCommandService;
import com.waddoc.domain.notification.event.SmsRequestMessage;
import com.waddoc.domain.vehicle.entity.Vehicle;
import com.waddoc.domain.vehicle.repository.VehicleRepository;
import com.waddoc.global.config.DemoModePolicy;
import com.waddoc.global.config.KafkaTopics;
import com.waddoc.global.monitoring.KafkaMonitoringMetrics;
import com.waddoc.global.util.KstTime;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;

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
    private final KafkaTemplate<String, Object> kafkaTemplate;
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

            boolean wasRetryPending = outbox.isRetryPending();
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

            if (wasRetryPending) {
                // 재시도 끝에 배차가 성사된 경우에만 복구 안내 SMS를 보낸다.
                publishSms(
                        outbox,
                        buildDispatchAssignedMessage(vehicle),
                        "dispatch-assigned-" + outbox.getCareCase().getPublicId()
                );
            }
        });
    }

    private void moveToRetry(DispatchOutbox outbox, Vehicle vehicle) {
        boolean shouldNotify = !outbox.isRetryPending();
        outbox.markRetryPending();
        if (shouldNotify) {
            publishSms(
                    outbox,
                    buildDispatchDelayedMessage(vehicle),
                    "dispatch-delayed-" + outbox.getCareCase().getPublicId()
            );
        }
    }

    private void publishSms(DispatchOutbox outbox, String message, String correlationId) {
        String recipientPhone = outbox.getCareCase().getPatient().getPhone();
        if (recipientPhone == null || recipientPhone.isBlank()) {
            return;
        }

        kafkaTemplate.send(
                KafkaTopics.SMS_REQUESTS_TOPIC,
                new SmsRequestMessage(recipientPhone, message, correlationId)
        );
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

    private String buildDispatchAssignedMessage(Vehicle vehicle) {
        if (vehicle == null) {
            return "[왔닥]%n배차가 완료되었습니다.%n차량이 배정되어 순차적으로 출동을 준비하고 있습니다.".formatted();
        }
        String vehicleName = vehicle.getDisplayName() != null && !vehicle.getDisplayName().isBlank()
                ? vehicle.getDisplayName()
                : vehicle.getCode();
        return String.format(
                "[왔닥]%n배차가 완료되었습니다.%n%s 차량이 배정되어 순차적으로 출동을 준비하고 있습니다.",
                vehicleName
        );
    }
}
