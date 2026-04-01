package com.waddoc.domain.robot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.waddoc.global.util.KstTime;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

@Slf4j
@Service
@RequiredArgsConstructor
public class RobotSseService {

    static final long STREAM_TIMEOUT_MILLIS     = 60 * 60 * 1000L;
    static final long DEFAULT_RECONNECT_DELAY_MILLIS = 3_000L;
    static final long HEARTBEAT_INTERVAL_MILLIS = 25_000L;

    private final RobotStateCache stateCache;
    private final RobotSnapshotAssembler robotSnapshotAssembler;
    private final Clock clock;

    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe() {
        String connectionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);

        emitters.put(connectionId, emitter);
        emitter.onCompletion(() -> removeEmitter(connectionId));
        emitter.onTimeout(() -> {
            removeEmitter(connectionId);
            emitter.complete();
        });
        emitter.onError(ignored -> removeEmitter(connectionId));

        try {
            emitter.send(SseEmitter.event()
                    .id(connectionId)
                    .name("connected")
                    .reconnectTime(DEFAULT_RECONNECT_DELAY_MILLIS)
                    .data(Map.of(
                            "minimap", stateCache.getLastMinimapJson(),
                            "state",   stateCache.getLastStateJson(),
                            "odom",    stateCache.getLastOdomJson(),
                            "status",  stateCache.getLastStatusJson(),
                            "snapshot", robotSnapshotAssembler.fromCache(stateCache),
                            "connectedAt", OffsetDateTime.now(KstTime.resolve(clock)).toString()
                    )));
        } catch (Exception e) {
            removeEmitter(connectionId);
            emitter.complete();
            if (isClientDisconnect(e)) {
                return emitter;
            }
            throw new IllegalStateException("Failed to initialize robot SSE stream", e);
        }

        log.info("Robot SSE connected. connectionId={}, total={}", connectionId, emitters.size());
        return emitter;
    }

    public void broadcast(String eventName, Object payload) {
        if (emitters.isEmpty()) {
            return;
        }
        emitters.forEach((connectionId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .id(UUID.randomUUID().toString())
                        .name(eventName)
                        .data(payload));
            } catch (Exception e) {
                if (!isClientDisconnect(e)) {
                    log.warn("Robot SSE delivery failed. connectionId={}, event={}", connectionId, eventName, e);
                }
                closeEmitter(connectionId, emitter);
            }
        });
    }

    @Scheduled(fixedDelay = HEARTBEAT_INTERVAL_MILLIS)
    void sendHeartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        String ping = "{\"ts\":\"" + OffsetDateTime.now(KstTime.resolve(clock)) + "\"}";
        emitters.forEach((connectionId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .id(UUID.randomUUID().toString())
                        .name("ping")
                        .data(ping));
            } catch (Exception e) {
                if (!isClientDisconnect(e)) {
                    log.warn("Robot SSE heartbeat failed. connectionId={}", connectionId, e);
                }
                closeEmitter(connectionId, emitter);
            }
        });
    }

    private void closeEmitter(String connectionId, SseEmitter emitter) {
        removeEmitter(connectionId);
        emitter.complete();
    }

    private void removeEmitter(String connectionId) {
        if (emitters.remove(connectionId) != null) {
            log.info("Robot SSE disconnected. connectionId={}, remaining={}", connectionId, emitters.size());
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
}
