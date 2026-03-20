package com.waddoc.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공통 에러 응답 DTO. API 명세의 에러 응답 형식에 대응.
 */
@Getter
@Builder
public class ErrorResponse {

    private final String errorCode;
    private final String message;
    private final LocalDateTime timestamp;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<FieldError> details;

    public static ErrorResponse of(ErrorCode code) {
        return ErrorResponse.builder()
                .errorCode(code.getCode())
                .message(code.getMessage())
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Getter
    @Builder
    public static class FieldError {
        private final String field;
        private final String reason;
    }
}
