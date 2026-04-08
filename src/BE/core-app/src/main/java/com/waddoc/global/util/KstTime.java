package com.waddoc.global.util;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 애플리케이션 전역에서 KST 기준 시각을 일관되게 계산하기 위한 유틸이다.
 */
public final class KstTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private KstTime() {
    }

    public static Clock clock() {
        return Clock.system(ZONE);
    }

    public static Clock resolve(Clock clock) {
        return clock != null ? clock : clock();
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
