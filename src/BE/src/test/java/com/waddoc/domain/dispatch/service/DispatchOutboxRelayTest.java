package com.waddoc.domain.dispatch.service;

import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.global.config.DemoModePolicy;
import com.waddoc.global.lock.RedisDistributedLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchOutboxRelayTest {

    @Mock
    private DispatchOutboxRepository dispatchOutboxRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private RedisDistributedLock redisDistributedLock;

    @Mock
    private DemoModePolicy demoModePolicy;

    @InjectMocks
    private DispatchOutboxRelay dispatchOutboxRelay;

    @Test
    void relay_skipsAutomaticDispatchWhenDemoModeIsEnabled() {
        when(demoModePolicy.isOperatorDispatchOnly()).thenReturn(true);

        dispatchOutboxRelay.relay();

        verify(redisDistributedLock, never()).tryLock(anyString(), anyLong());
        verify(dispatchOutboxRepository, never()).findAllByStatusOrderByCreatedAtAsc(any());
    }
}
