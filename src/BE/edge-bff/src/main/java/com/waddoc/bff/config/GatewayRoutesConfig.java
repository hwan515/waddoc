package com.waddoc.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 공개 API 경로를 각 owner 서비스로 연결하는 gateway 라우팅 규칙이다.
 */
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator gatewayRoutes(
            RouteLocatorBuilder builder,
            @Value("${upstream.core-app}") String coreAppUri,
            @Value("${upstream.notification-service}") String notificationServiceUri,
            @Value("${upstream.robot-gateway}") String robotGatewayUri
    ) {
        return builder.routes()
                .route("core-auth", r -> r.path("/api/v1/auth/**").uri(coreAppUri))
                .route("core-patients", r -> r.path("/api/v1/patients/**").uri(coreAppUri))
                .route("core-intake", r -> r.path("/api/v1/intake/**").uri(coreAppUri))
                .route("core-bookings", r -> r.path("/api/v1/bookings/**").uri(coreAppUri))
                .route("core-sessions", r -> r.path("/api/v1/sessions/**").uri(coreAppUri))
                .route("core-missions", r -> r.path("/api/v1/missions/**").uri(coreAppUri))
                .route("core-terminal", r -> r.path("/api/v1/terminal/**").uri(coreAppUri))
                .route("core-admin-vehicles", r -> r
                        .path("/api/v1/admin/vehicles", "/api/v1/admin/vehicles/**")
                        .uri(coreAppUri))
                .route("core-admin-monitoring", r -> r
                        .path("/api/v1/admin/monitoring/**")
                        .uri(coreAppUri))
                .route("notification-stream", r -> r.path("/api/v1/doctors/me/notifications/stream").uri(notificationServiceUri))
                .route("robot", r -> r.path("/api/v1/robots/**").uri(robotGatewayUri))
                .route("core-admin-writes", r -> r
                        .path(
                                "/api/v1/admin/guardian-link-requests/*/approve",
                                "/api/v1/admin/guardian-link-requests/*/reject",
                                "/api/v1/admin/demo/missions/**"
                        )
                        .uri(coreAppUri))
                .build();
    }
}
