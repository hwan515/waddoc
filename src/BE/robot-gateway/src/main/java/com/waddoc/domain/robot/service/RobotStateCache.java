package com.waddoc.domain.robot.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 로봇 최신 상태를 Redis keyspace에 저장하고 즉시 읽을 수 있게 유지한다.
 */
@Component
public class RobotStateCache {

    private static final String ROBOT_MINIMAP_KEY = "robot:minimap";
    private static final String ROBOT_STATE_KEY = "robot:state";
    private static final String ROBOT_ODOM_KEY = "robot:odom";
    private static final String ROBOT_STATUS_KEY = "robot:status";

    private final StringRedisTemplate stringRedisTemplate;

    public RobotStateCache(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public String getLastMinimapJson() {
        return getOrDefault(ROBOT_MINIMAP_KEY);
    }

    public String getLastStateJson() {
        return getOrDefault(ROBOT_STATE_KEY);
    }

    public String getLastOdomJson() {
        return getOrDefault(ROBOT_ODOM_KEY);
    }

    public String getLastStatusJson() {
        return getOrDefault(ROBOT_STATUS_KEY);
    }

    public void setLastMinimapJson(String json) {
        stringRedisTemplate.opsForValue().set(ROBOT_MINIMAP_KEY, defaultJson(json));
    }

    public void setLastStateJson(String json) {
        stringRedisTemplate.opsForValue().set(ROBOT_STATE_KEY, defaultJson(json));
    }

    public void setLastOdomJson(String json) {
        stringRedisTemplate.opsForValue().set(ROBOT_ODOM_KEY, defaultJson(json));
    }

    public void setLastStatusJson(String json) {
        stringRedisTemplate.opsForValue().set(ROBOT_STATUS_KEY, defaultJson(json));
    }

    private String getOrDefault(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        return value == null || value.isBlank() ? "{}" : value;
    }

    private String defaultJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
    }
}
