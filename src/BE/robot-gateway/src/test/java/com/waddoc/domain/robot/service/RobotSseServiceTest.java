package com.waddoc.domain.robot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class RobotSseServiceTest {

    private RobotSseService robotSseService;

    @BeforeEach
    void setUp() {
        StringRedisTemplate stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
        Mockito.when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        Mockito.when(valueOperations.get(Mockito.anyString())).thenReturn("{}");

        robotSseService = new RobotSseService(
                new RobotStateCache(stringRedisTemplate),
                new RobotSnapshotAssembler(new ObjectMapper()),
                Clock.fixed(Instant.parse("2026-04-01T00:00:00Z"), ZoneId.of("Asia/Seoul"))
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
