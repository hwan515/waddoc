package com.waddoc.shared.http;

/**
 * 서비스 간 프록시와 추적에 사용하는 공통 헤더 이름을 모아 둔다.
 */
public final class CorrelationHeaders {

    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String FORWARDED_AUTHORIZATION = "Authorization";
    public static final String FORWARDED_COOKIE = "Cookie";

    private CorrelationHeaders() {
    }
}
