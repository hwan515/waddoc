package com.waddoc.bff.config;

import com.waddoc.shared.http.CorrelationHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 상위 클라이언트가 correlation ID를 보내지 않더라도
 * 내부 owner 서비스까지 추적 헤더가 이어지도록 기본값을 채워 넣는다.
 */
@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(CorrelationHeaders.CORRELATION_ID);
        if (correlationId != null && !correlationId.isBlank()) {
            return chain.filter(exchange);
        }

        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(CorrelationHeaders.CORRELATION_ID, UUID.randomUUID().toString())
                .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
