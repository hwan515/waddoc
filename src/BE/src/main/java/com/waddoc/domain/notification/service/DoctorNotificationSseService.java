package com.waddoc.domain.notification.service;

import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorNotificationSseService {

    static final long STREAM_TIMEOUT_MILLIS = 60 * 60 * 1000L;
    static final long DEFAULT_RECONNECT_DELAY_MILLIS = 3_000L;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AccessControlService accessControlService;

    private final ConcurrentMap<String, ConcurrentMap<String, SseEmitter>> emittersByDoctorId =
            new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    public SseEmitter subscribeDoctor(AuthenticatedUser authenticatedUser) {
        DoctorProfile doctorProfile = accessControlService.getDoctorProfileOrThrow(authenticatedUser);

        String doctorProfileId = doctorProfile.getPublicId();
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);

        emittersByDoctorId
                .computeIfAbsent(doctorProfileId, ignored -> new ConcurrentHashMap<>())
                .put(connectionId, emitter);

        emitter.onCompletion(() -> removeEmitter(doctorProfileId, connectionId));
        emitter.onTimeout(() -> {
            removeEmitter(doctorProfileId, connectionId);
            emitter.complete();
        });
        emitter.onError(ignored -> removeEmitter(doctorProfileId, connectionId));

        try {
            emitter.send(SseEmitter.event()
                    .id(connectionId)
                    .name("connected")
                    .reconnectTime(DEFAULT_RECONNECT_DELAY_MILLIS)
                    .data(new ConnectedEvent(OffsetDateTime.now(KST))));
        } catch (Exception e) {
            removeEmitter(doctorProfileId, connectionId);
            emitter.completeWithError(e);
            throw new IllegalStateException("Failed to initialize doctor notification SSE stream", e);
        }

        log.info("Doctor SSE connected. doctorId={}, connectionId={}", doctorProfileId, connectionId);
        return emitter;
    }

    public void sendToDoctor(String doctorProfileId, String eventName, Object payload) {
        Map<String, SseEmitter> connections = emittersByDoctorId.get(doctorProfileId);
        if (connections == null || connections.isEmpty()) {
            return;
        }

        connections.forEach((connectionId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .id(UUID.randomUUID().toString())
                        .name(eventName)
                        .data(payload));
            } catch (Exception e) {
                log.warn("Doctor SSE delivery failed. doctorId={}, connectionId={}, eventName={}",
                        doctorProfileId, connectionId, eventName, e);
                removeEmitter(doctorProfileId, connectionId);
                emitter.completeWithError(e);
            }
        });
    }

    int countConnections(String doctorProfileId) {
        Map<String, SseEmitter> connections = emittersByDoctorId.get(doctorProfileId);
        return connections == null ? 0 : connections.size();
    }

    public boolean hasConnections(String doctorProfileId) {
        return countConnections(doctorProfileId) > 0;
    }

    private void removeEmitter(String doctorProfileId, String connectionId) {
        ConcurrentMap<String, SseEmitter> connections = emittersByDoctorId.get(doctorProfileId);
        if (connections == null) {
            return;
        }

        connections.remove(connectionId);
        if (connections.isEmpty()) {
            emittersByDoctorId.remove(doctorProfileId, connections);
        }

        log.info("Doctor SSE disconnected. doctorId={}, connectionId={}", doctorProfileId, connectionId);
    }

    private record ConnectedEvent(OffsetDateTime connectedAt) {
    }
}
