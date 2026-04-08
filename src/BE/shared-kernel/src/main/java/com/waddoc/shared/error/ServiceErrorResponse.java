package com.waddoc.shared.error;

import java.time.OffsetDateTime;

/**
 * 서비스 간 HTTP 호출에서 공통으로 사용할 에러 응답 형태다.
 */
public record ServiceErrorResponse(
        String code,
        String message,
        OffsetDateTime timestamp,
        String path
) {
}
