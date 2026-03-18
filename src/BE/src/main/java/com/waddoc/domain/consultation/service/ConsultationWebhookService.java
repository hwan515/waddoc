package com.waddoc.domain.consultation.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import io.livekit.server.WebhookReceiver;
import livekit.LivekitModels;
import livekit.LivekitWebhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationWebhookService {

    private final ConsultationSessionRepository consultationSessionRepository;
    private final AuditLogService auditLogService;
    private final WebhookReceiver webhookReceiver;

    @Qualifier("consultationWebhookTaskScheduler")
    private final TaskScheduler taskScheduler;

    // TODO: 백엔드를 다중 인스턴스로 확장하면 이 인메모리 타이머를 Redis/DB 기반 지연 작업으로 옮겨야 한다.
    // 현재 구현은 동일 인스턴스가 left/joined webhook을 모두 처리하는 단일 서버 전제를 둔다.
    private final Map<String, ScheduledFuture<?>> disconnectTasks = new ConcurrentHashMap<>();

    @Transactional
    public void handleWebhook(String body, String authorizationHeader) {
        LivekitWebhook.WebhookEvent event;
        try {
            event = webhookReceiver.receive(body, authorizationHeader);
        } catch (RuntimeException e) {
            throw new BusinessException(ErrorCode.LIVEKIT_WEBHOOK_INVALID_SIGNATURE);
        }

        String eventName = event.getEvent();
        if (eventName == null || eventName.isBlank()) {
            log.info("Ignoring LiveKit webhook with empty event name");
            return;
        }

        switch (eventName) {
            case "participant_joined" -> handleParticipantJoined(event);
            case "participant_left" -> handleParticipantLeft(event);
            case "room_finished" -> handleRoomFinished(event);
            default -> log.info("Ignoring unsupported LiveKit webhook event: {}", eventName);
        }
    }

    private void handleParticipantJoined(LivekitWebhook.WebhookEvent event) {
        ConsultationSession session = findSessionByRoom(event);
        ParticipantRole participantRole = resolveParticipantRole(session, event);
        cancelDisconnectTimer(session.getPublicId(), participantRole);

        switch (participantRole) {
            case DOCTOR -> session.connectDoctor();
            case PATIENT -> session.connectPatient();
            case UNKNOWN -> {
                log.info("Ignoring participant_joined with unknown identity. sessionId={}, identity={}",
                        session.getPublicId(), event.hasParticipant() ? event.getParticipant().getIdentity() : null);
                return;
            }
        }

        auditLogService.log(
                "LIVEKIT_PARTICIPANT_JOINED",
                "CONSULTATION_SESSION",
                session.getPublicId(),
                "corr_ses_" + session.getPublicId(),
                Map.of(
                        "participantRole", participantRole.name(),
                        "event", event.getEvent(),
                        "identity", event.getParticipant().getIdentity()
                )
        );
    }

    private void handleParticipantLeft(LivekitWebhook.WebhookEvent event) {
        ConsultationSession session = findSessionByRoom(event);
        ParticipantRole participantRole = resolveParticipantRole(session, event);

        switch (participantRole) {
            case DOCTOR -> session.disconnectDoctor();
            case PATIENT -> session.disconnectPatient();
            case UNKNOWN -> {
                log.info("Ignoring participant_left with unknown identity. sessionId={}, identity={}",
                        session.getPublicId(), event.hasParticipant() ? event.getParticipant().getIdentity() : null);
                return;
            }
        }

        scheduleDisconnectTimer(session, participantRole);

        auditLogService.log(
                "LIVEKIT_PARTICIPANT_LEFT",
                "CONSULTATION_SESSION",
                session.getPublicId(),
                "corr_ses_" + session.getPublicId(),
                Map.of(
                        "participantRole", participantRole.name(),
                        "event", event.getEvent(),
                        "identity", event.getParticipant().getIdentity()
                )
        );
    }

    private void handleRoomFinished(LivekitWebhook.WebhookEvent event) {
        ConsultationSession session = findSessionByRoom(event);
        cancelDisconnectTimer(session.getPublicId(), ParticipantRole.DOCTOR);
        cancelDisconnectTimer(session.getPublicId(), ParticipantRole.PATIENT);

        if (session.getStatus() != ConsultationSessionStatus.COMPLETED) {
            session.complete(calculateDurationMinutes(session, LocalDateTime.now()));
        }

        auditLogService.log(
                "LIVEKIT_ROOM_FINISHED",
                "CONSULTATION_SESSION",
                session.getPublicId(),
                "corr_ses_" + session.getPublicId(),
                Map.of("event", event.getEvent(), "roomId", session.getRoomId())
        );
    }

    private ConsultationSession findSessionByRoom(LivekitWebhook.WebhookEvent event) {
        String roomId = extractRoomId(event);
        return consultationSessionRepository.findWithParticipantsByRoomId(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    private String extractRoomId(LivekitWebhook.WebhookEvent event) {
        if (!event.hasRoom() || event.getRoom().getName().isBlank()) {
            throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
        }
        return event.getRoom().getName();
    }

    private ParticipantRole resolveParticipantRole(ConsultationSession session, LivekitWebhook.WebhookEvent event) {
        if (!event.hasParticipant()) {
            return ParticipantRole.UNKNOWN;
        }
        String identity = Optional.ofNullable(event.getParticipant().getIdentity()).orElse("").trim();
        if (identity.isBlank()) {
            return ParticipantRole.UNKNOWN;
        }

        String doctorProfileId = session.getCareCase().getDoctor().getPublicId();
        String doctorUserId = session.getCareCase().getDoctor().getUser().getPublicId();
        String patientId = session.getCareCase().getPatient().getPublicId();

        if (matchesIdentity(identity, doctorProfileId, "doctor") || matchesIdentity(identity, doctorUserId, "doctor")) {
            return ParticipantRole.DOCTOR;
        }
        if (matchesIdentity(identity, patientId, "patient")) {
            return ParticipantRole.PATIENT;
        }
        return ParticipantRole.UNKNOWN;
    }

    private boolean matchesIdentity(String identity, String expectedId, String rolePrefix) {
        String normalized = identity.toLowerCase(Locale.ROOT);
        String expected = expectedId.toLowerCase(Locale.ROOT);
        return normalized.equals(expected)
                || normalized.equals(rolePrefix + ":" + expected)
                || normalized.equals(rolePrefix + "-" + expected)
                || normalized.endsWith(":" + expected)
                || normalized.endsWith("-" + expected);
    }

    private void scheduleDisconnectTimer(ConsultationSession session, ParticipantRole participantRole) {
        String taskKey = buildTaskKey(session.getPublicId(), participantRole);
        cancelDisconnectTimer(session.getPublicId(), participantRole);

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> {
                    disconnectTasks.remove(taskKey);
                    auditLogService.log(
                            "LIVEKIT_RECONNECT_TIMEOUT",
                            "CONSULTATION_SESSION",
                            session.getPublicId(),
                            "corr_ses_" + session.getPublicId(),
                            Map.of(
                                    "participantRole", participantRole.name(),
                                    "doctorConnected", session.isDoctorConnected(),
                                    "patientConnected", session.isPatientConnected()
                            )
                    );
                },
                Instant.now().plusSeconds(30)
        );

        if (future != null) {
            disconnectTasks.put(taskKey, future);
        }
    }

    private void cancelDisconnectTimer(String sessionId, ParticipantRole participantRole) {
        ScheduledFuture<?> future = disconnectTasks.remove(buildTaskKey(sessionId, participantRole));
        if (future != null) {
            future.cancel(false);
        }
    }

    private String buildTaskKey(String sessionId, ParticipantRole participantRole) {
        return sessionId + ":" + participantRole.name();
    }

    private int calculateDurationMinutes(ConsultationSession session, LocalDateTime endedAt) {
        if (session.getStartedAt() == null) {
            return 0;
        }
        return Math.max(0, (int) Duration.between(session.getStartedAt(), endedAt).toMinutes());
    }

    enum ParticipantRole {
        DOCTOR,
        PATIENT,
        UNKNOWN
    }
}
