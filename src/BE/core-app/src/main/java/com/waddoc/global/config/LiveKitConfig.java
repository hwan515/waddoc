package com.waddoc.global.config;

import io.livekit.server.RoomServiceClient;
import io.livekit.server.WebhookReceiver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LiveKit API 호출과 webhook 검증에 필요한 Bean을 등록한다.
 */
@Configuration
public class LiveKitConfig {

    @Bean
    public WebhookReceiver liveKitWebhookReceiver(
            @Value("${livekit.api-key}") String apiKey,
            @Value("${livekit.api-secret}") String apiSecret
    ) {
        return new WebhookReceiver(apiKey, apiSecret);
    }

    @Bean
    public RoomServiceClient liveKitRoomServiceClient(
            @Value("${livekit.host}") String host,
            @Value("${livekit.api-key}") String apiKey,
            @Value("${livekit.api-secret}") String apiSecret
    ) {
        String normalizedHost = host.endsWith("/") ? host : host + "/";
        return RoomServiceClient.createClient(normalizedHost, apiKey, apiSecret);
    }
}
