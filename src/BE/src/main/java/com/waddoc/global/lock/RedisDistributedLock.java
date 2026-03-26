package com.waddoc.global.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RedisDistributedLock {

    private final StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                        "return redis.call('del', KEYS[1]) " +
                        "else return 0 end"
        );
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    /**
     * 분산 락 획득을 시도한다.
     *
     * @param key        락 키
     * @param ttlSeconds 락 만료 시간(초)
     * @return 락 획득 시 lockValue(UUID), 실패 시 null
     */
    public String tryLock(String key, long ttlSeconds) {
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, lockValue, Duration.ofSeconds(ttlSeconds));
        return Boolean.TRUE.equals(acquired) ? lockValue : null;
    }

    /**
     * 분산 락을 해제한다. 본인이 획득한 락만 해제할 수 있다.
     *
     * @param key       락 키
     * @param lockValue tryLock에서 반환받은 값
     */
    public void unlock(String key, String lockValue) {
        if (lockValue == null) {
            return;
        }
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), lockValue);
    }
}
