package com.waddoc.domain.consultation.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import io.livekit.server.WebhookReceiver;
import livekit.LivekitWebhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * LiveKit webhook을 검증하고 참가자 연결 상태를 진료 세션에 반영한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationWebhookService {

    private static final String WEBHOOK_IDEMPOTENCY_PREFIX = "webhook:event:";
    private static final Duration WEBHOOK_IDEMPOTENCY_TTL = Duration.ofMinutes(5);

    private final ConsultationSessionRepository consultationSessionRepository;
    private final AuditLogService auditLogService;
    private final WebhookReceiver webhookReceiver;
    private final DisconnectTimerService disconnectTimerService;
    private final StringRedisTemplate redisTemplate;

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

        // 웹훅 멱등성: 이미 처리된 이벤트는 무시
        String eventId = event.getId();
        if (eventId != null && !eventId.isBlank()) {
            String idempotencyKey = WEBHOOK_IDEMPOTENCY_PREFIX + eventId;
            Boolean wasAbsent = redisTemplate.opsForValue().setIfAbsent(idempotencyKey, "1", WEBHOOK_IDEMPOTENCY_TTL);
            if (!Boolean.TRUE.equals(wasAbsent)) {
                log.info("Ignoring duplicate LiveKit webhook event: id={}, event={}", eventId, eventName);
                return;
            }
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
        disconnectTimerService.cancel(session.getPublicId(), participantRole.name());

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

        disconnectTimerService.schedule(session.getPublicId(), participantRole.name());

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
        disconnectTimerService.cancel(session.getPublicId(), ParticipantRole.DOCTOR.name());
        disconnectTimerService.cancel(session.getPublicId(), ParticipantRole.PATIENT.name());

        // 진료가 실제 시작된 세션만 room_finished 시 완료 처리한다.
        if (session.getStatus() == ConsultationSessionStatus.IN_PROGRESS) {
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
