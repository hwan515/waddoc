package com.waddoc.global.config;

import io.livekit.server.WebhookReceiver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class LiveKitConfig {

    @Bean
    public WebhookReceiver liveKitWebhookReceiver(
            @Value("${livekit.api-key}") String apiKey,
            @Value("${livekit.api-secret}") String apiSecret
    ) {
        return new WebhookReceiver(apiKey, apiSecret);
    }

    @Bean(name = "consultationWebhookTaskScheduler")
    public TaskScheduler consultationWebhookTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("consultation-webhook-");
        scheduler.initialize();
        return scheduler;
    }
}
