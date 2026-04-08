package com.waddoc.domain.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 의사별 SSE 연결을 관리하고 새 예약 알림을 브라우저로 밀어 넣는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorNotificationSseService {

    static final long STREAM_TIMEOUT_MILLIS = 60 * 60 * 1000L;
    static final long DEFAULT_RECONNECT_DELAY_MILLIS = 3_000L;
    static final long HEARTBEAT_INTERVAL_MILLIS = 25_000L;

    private final ConcurrentMap<String, ConcurrentMap<String, SseEmitter>> emittersByDoctorUserId =
            new ConcurrentHashMap<>();

    public SseEmitter subscribeDoctor(String doctorUserId) {
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);

        emittersByDoctorUserId
                .computeIfAbsent(doctorUserId, ignored -> new ConcurrentHashMap<>())
                .put(connectionId, emitter);

        emitter.onCompletion(() -> removeEmitter(doctorUserId, connectionId));
        emitter.onTimeout(() -> {
            removeEmitter(doctorUserId, connectionId);
            emitter.complete();
        });
        emitter.onError(ignored -> removeEmitter(doctorUserId, connectionId));

        try {
            emitter.send(SseEmitter.event()
                    .id(connectionId)
                    .name("connected")
                    .reconnectTime(DEFAULT_RECONNECT_DELAY_MILLIS)
                    .data(new ConnectedEvent(OffsetDateTime.now())));
        } catch (Exception e) {
            removeEmitter(doctorUserId, connectionId);
            emitter.complete();
            if (isClientDisconnect(e)) {
                return emitter;
            }
            throw new IllegalStateException("Failed to initialize doctor notification SSE stream", e);
        }

        log.info("Doctor SSE connected. doctorUserId={}, connectionId={}", doctorUserId, connectionId);
        return emitter;
    }

    public void sendToDoctorUser(String doctorUserId, String eventName, Object payload) {
        Map<String, SseEmitter> connections = emittersByDoctorUserId.get(doctorUserId);
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
                if (!isClientDisconnect(e)) {
                    log.warn("Doctor SSE delivery failed. doctorUserId={}, connectionId={}, eventName={}",
                            doctorUserId, connectionId, eventName, e);
                }
                closeEmitter(doctorUserId, connectionId, emitter);
            }
        });
    }

    @Scheduled(fixedDelay = HEARTBEAT_INTERVAL_MILLIS)
    void sendHeartbeat() {
        if (emittersByDoctorUserId.isEmpty()) {
            return;
        }

        emittersByDoctorUserId.forEach((doctorUserId, connections) ->
                connections.forEach((connectionId, emitter) -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .id(UUID.randomUUID().toString())
                                .name("ping")
                                .data(new ConnectedEvent(OffsetDateTime.now())));
                    } catch (Exception e) {
                        if (!isClientDisconnect(e)) {
                            log.warn("Doctor SSE heartbeat failed. doctorUserId={}, connectionId={}",
                                    doctorUserId, connectionId, e);
                        }
                        closeEmitter(doctorUserId, connectionId, emitter);
                    }
                }));
    }

    int countConnections(String doctorUserId) {
        Map<String, SseEmitter> connections = emittersByDoctorUserId.get(doctorUserId);
        return connections == null ? 0 : connections.size();
    }

    public boolean hasConnections(String doctorUserId) {
        return countConnections(doctorUserId) > 0;
    }

    private void closeEmitter(String doctorUserId, String connectionId, SseEmitter emitter) {
        removeEmitter(doctorUserId, connectionId);
        emitter.complete();
    }

    private void removeEmitter(String doctorUserId, String connectionId) {
        ConcurrentMap<String, SseEmitter> connections = emittersByDoctorUserId.get(doctorUserId);
        if (connections == null) {
            return;
        }

        SseEmitter removed = connections.remove(connectionId);
        if (connections.isEmpty()) {
            emittersByDoctorUserId.remove(doctorUserId, connections);
        }

        if (removed != null) {
            log.info("Doctor SSE disconnected. doctorUserId={}, connectionId={}", doctorUserId, connectionId);
        }
    }

    private boolean isClientDisconnect(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AsyncRequestNotUsableException) {
                return true;
            }

            String simpleName = current.getClass().getSimpleName();
            if ("ClientAbortException".equals(simpleName) || "EOFException".equals(simpleName)) {
                return true;
            }

            String message = current.getMessage();
            if (message != null) {
                String normalizedMessage = message.toLowerCase(Locale.ROOT);
                if (normalizedMessage.contains("broken pipe")
                        || normalizedMessage.contains("connection reset by peer")) {
                    return true;
                }
            }

            current = current.getCause();
        }
        return false;
    }

    private record ConnectedEvent(OffsetDateTime connectedAt) {
    }
}
