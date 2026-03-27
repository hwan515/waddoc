package com.waddoc.domain.robot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RobotSseService {

    static final long STREAM_TIMEOUT_MILLIS     = 60 * 60 * 1000L;
    static final long DEFAULT_RECONNECT_DELAY_MILLIS = 3_000L;
    static final long HEARTBEAT_INTERVAL_MILLIS = 25_000L;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final RobotStateCache stateCache;
    private final RobotSnapshotAssembler robotSnapshotAssembler;

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
                            "connectedAt", OffsetDateTime.now(KST).toString()
                    )));
        } catch (Exception e) {
            removeEmitter(connectionId);
            emitter.completeWithError(e);
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
                log.warn("Robot SSE delivery failed. connectionId={}, event={}", connectionId, eventName, e);
                removeEmitter(connectionId);
                emitter.completeWithError(e);
            }
        });
    }

    @Scheduled(fixedDelay = HEARTBEAT_INTERVAL_MILLIS)
    void sendHeartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        String ping = "{\"ts\":\"" + OffsetDateTime.now(KST) + "\"}";
        emitters.forEach((connectionId, emitter) -> {
            try {
                emitter.send(SseEmitter.event()
                        .id(UUID.randomUUID().toString())
                        .name("ping")
                        .data(ping));
            } catch (Exception e) {
                log.warn("Robot SSE heartbeat failed. connectionId={}", connectionId, e);
                removeEmitter(connectionId);
                emitter.completeWithError(e);
            }
        });
    }

    private void removeEmitter(String connectionId) {
        emitters.remove(connectionId);
        log.info("Robot SSE disconnected. connectionId={}, remaining={}", connectionId, emitters.size());
    }
}
