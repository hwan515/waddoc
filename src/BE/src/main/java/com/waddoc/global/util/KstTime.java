package com.waddoc.global.util;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

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
