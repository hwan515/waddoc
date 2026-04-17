package com.waddoc.bff.controller;

import com.waddoc.bff.security.BffJwtAuthService;
import com.waddoc.shared.http.CorrelationHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 보호자 전용 조회 API를 BFF에서 인증하고 core-app 응답을 그대로 중계한다.
 */
@RestController
@RequestMapping("/api/v1/guardians")
@RequiredArgsConstructor
public class GuardianReadProxyController {

    private final WebClient.Builder webClientBuilder;
    private final BffJwtAuthService bffJwtAuthService;

    @Value("${upstream.core-app}")
    private String coreAppUri;

    @GetMapping("/patients")
    public Mono<ResponseEntity<String>> getPatients(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestHeader(value = HttpHeaders.COOKIE, required = false) String cookieHeader,
            @RequestHeader(value = CorrelationHeaders.CORRELATION_ID, required = false) String correlationId
    ) {
        bffJwtAuthService.requireRole(authorizationHeader, "GUARDIAN");
        return proxyGet("/api/v1/guardians/patients", authorizationHeader, cookieHeader, correlationId);
    }

    @GetMapping("/patients/{patientId}/summaries")
    public Mono<ResponseEntity<String>> getSummaries(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestHeader(value = HttpHeaders.COOKIE, required = false) String cookieHeader,
            @RequestHeader(value = CorrelationHeaders.CORRELATION_ID, required = false) String correlationId,
            @PathVariable String patientId
    ) {
        bffJwtAuthService.requireRole(authorizationHeader, "GUARDIAN");
        return proxyGet("/api/v1/guardians/patients/" + patientId + "/summaries", authorizationHeader, cookieHeader, correlationId);
    }

    private Mono<ResponseEntity<String>> proxyGet(
            String path,
            String authorizationHeader,
            String cookieHeader,
            String correlationId
    ) {
        return webClientBuilder.build()
                .get()
                .uri(coreAppUri + path)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .header(CorrelationHeaders.CORRELATION_ID, resolveCorrelationId(correlationId))
                .headers(headers -> {
                    if (cookieHeader != null && !cookieHeader.isBlank()) {
                        headers.add(HttpHeaders.COOKIE, cookieHeader);
                    }
                })
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(body -> ResponseEntity.status(response.statusCode())
                                .headers(response.headers().asHttpHeaders())
                                .body(body)));
    }

    private String resolveCorrelationId(String correlationId) {
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }
}
