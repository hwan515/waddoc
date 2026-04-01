package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.global.util.KstTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class RobotSseServiceTest {

    private RobotSseService robotSseService;

    @BeforeEach
    void setUp() {
        robotSseService = new RobotSseService(
                new RobotStateCache(),
                new RobotSnapshotAssembler(new ObjectMapper()),
                Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), KstTime.ZONE)
        );
    }

    @Test
    void sendHeartbeat_removesEmitterWhenClientDisconnects() throws Exception {
        emitterStore().put("conn_test", new BrokenPipeSseEmitter());

        robotSseService.sendHeartbeat();

        assertThat(emitterStore()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, SseEmitter> emitterStore() throws Exception {
        Field field = RobotSseService.class.getDeclaredField("emitters");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, SseEmitter>) field.get(robotSseService);
    }

    private static final class BrokenPipeSseEmitter extends SseEmitter {
        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            throw new IOException("Broken pipe");
        }
    }
}
