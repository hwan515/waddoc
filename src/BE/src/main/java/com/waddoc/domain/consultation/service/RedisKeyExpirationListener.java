package com.waddoc.domain.consultation.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.KeyExpirationEventMessageListener;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * Redis 키 만료 이벤트를 수신하여 disconnect timeout 처리를 수행한다.
 * disconnect:{sessionId}:{role} 형식의 키가 만료되면 해당 세션의 reconnect timeout 로직을 실행한다.
 */
@Slf4j
@Component
public class RedisKeyExpirationListener extends KeyExpirationEventMessageListener {

    private final ConsultationSessionRepository consultationSessionRepository;
    private final AuditLogService auditLogService;

    public RedisKeyExpirationListener(RedisMessageListenerContainer listenerContainer,
                                       ConsultationSessionRepository consultationSessionRepository,
                                       AuditLogService auditLogService) {
        super(listenerContainer);
        this.consultationSessionRepository = consultationSessionRepository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();

        if (!DisconnectTimerService.isDisconnectKey(expiredKey)) {
            return;
        }

        String sessionId = DisconnectTimerService.parseSessionId(expiredKey);
        String role = DisconnectTimerService.parseRole(expiredKey);

        log.info("Disconnect timer expired: sessionId={}, role={}", sessionId, role);

        Optional<ConsultationSession> sessionOpt = consultationSessionRepository.findByPublicId(sessionId);
        if (sessionOpt.isEmpty()) {
            log.warn("Session not found for expired disconnect timer: sessionId={}", sessionId);
            return;
        }

        ConsultationSession session = sessionOpt.get();

        auditLogService.log(
                "LIVEKIT_RECONNECT_TIMEOUT",
                "CONSULTATION_SESSION",
                session.getPublicId(),
                "corr_ses_" + session.getPublicId(),
                Map.of(
                        "participantRole", role,
                        "doctorConnected", session.isDoctorConnected(),
                        "patientConnected", session.isPatientConnected()
                )
        );
    }
}
