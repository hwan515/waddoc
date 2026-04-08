package com.waddoc.bff.controller;

import com.waddoc.bff.security.BffJwtAuthService;
import com.waddoc.shared.http.CorrelationHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * BFF가 소유한 관리자 조회 API를 인증한 뒤 core-app으로 프록시한다.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminReadProxyController {

    private final WebClient.Builder webClientBuilder;
    private final BffJwtAuthService bffJwtAuthService;

    @Value("${upstream.core-app}")
    private String coreAppUri;

    @GetMapping("/bookings")
    public Mono<ResponseEntity<String>> getBookings(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestHeader(value = HttpHeaders.COOKIE, required = false) String cookieHeader,
            @RequestHeader(value = CorrelationHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestParam MultiValueMap<String, String> queryParams
    ) {
        bffJwtAuthService.requireRole(authorizationHeader, "ADMIN");
        return proxyGet("/api/v1/admin/bookings", authorizationHeader, cookieHeader, correlationId, queryParams);
    }

    @GetMapping("/cases")
    public Mono<ResponseEntity<String>> getCases(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestHeader(value = HttpHeaders.COOKIE, required = false) String cookieHeader,
            @RequestHeader(value = CorrelationHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestParam MultiValueMap<String, String> queryParams
    ) {
        bffJwtAuthService.requireRole(authorizationHeader, "ADMIN");
        return proxyGet("/api/v1/admin/cases", authorizationHeader, cookieHeader, correlationId, queryParams);
    }

    @GetMapping("/sessions")
    public Mono<ResponseEntity<String>> getSessions(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestHeader(value = HttpHeaders.COOKIE, required = false) String cookieHeader,
            @RequestHeader(value = CorrelationHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestParam MultiValueMap<String, String> queryParams
    ) {
        bffJwtAuthService.requireRole(authorizationHeader, "ADMIN");
        return proxyGet("/api/v1/admin/sessions", authorizationHeader, cookieHeader, correlationId, queryParams);
    }

    @GetMapping("/patients")
    public Mono<ResponseEntity<String>> getPatients(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestHeader(value = HttpHeaders.COOKIE, required = false) String cookieHeader,
            @RequestHeader(value = CorrelationHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestParam MultiValueMap<String, String> queryParams
    ) {
        bffJwtAuthService.requireRole(authorizationHeader, "ADMIN");
        return proxyGet("/api/v1/admin/patients", authorizationHeader, cookieHeader, correlationId, queryParams);
    }

    @GetMapping("/guardian-link-requests")
    public Mono<ResponseEntity<String>> getGuardianLinkRequests(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            @RequestHeader(value = HttpHeaders.COOKIE, required = false) String cookieHeader,
            @RequestHeader(value = CorrelationHeaders.CORRELATION_ID, required = false) String correlationId,
            @RequestParam MultiValueMap<String, String> queryParams
    ) {
        bffJwtAuthService.requireRole(authorizationHeader, "ADMIN");
        return proxyGet("/api/v1/admin/guardian-link-requests", authorizationHeader, cookieHeader, correlationId, queryParams);
    }

    private Mono<ResponseEntity<String>> proxyGet(
            String path,
            String authorizationHeader,
            String cookieHeader,
            String correlationId,
            MultiValueMap<String, String> queryParams
    ) {
        return webClientBuilder.build()
                .get()
                // BFF-owned read API만 직접 인증하고, 실제 데이터 owner인 core-app 응답을 그대로 중계한다.
                .uri(UriComponentsBuilder.fromUriString(coreAppUri + path)
                        .queryParams(queryParams)
                        .build(true)
                        .toUri())
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
                                .headers(copyHeaders(response.headers().asHttpHeaders()))
                                .body(body)));
    }

    private HttpHeaders copyHeaders(HttpHeaders source) {
        HttpHeaders target = new HttpHeaders();
        source.forEach((name, values) -> {
            if (!HttpHeaders.TRANSFER_ENCODING.equalsIgnoreCase(name)) {
                target.put(name, values);
            }
        });
        return target;
    }

    private String resolveCorrelationId(String correlationId) {
        return correlationId == null || correlationId.isBlank() ? UUID.randomUUID().toString() : correlationId;
    }
}
