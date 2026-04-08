package com.waddoc.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DemoModePolicy {

    private final boolean enabled;

    public DemoModePolicy(@Value("${app.demo-mode.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isOperatorDispatchOnly() {
        return enabled;
    }

    public boolean isAutomaticDispatchEnabled() {
        return !isOperatorDispatchOnly();
    }

    public boolean isSameDayAutoProvisionEnabled() {
        return !isOperatorDispatchOnly();
    }
}
