package com.waddoc.global.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import com.waddoc.global.util.KstTime;

/**
 * 전역 예외 처리. BusinessException과 Validation 에러를 공통 형식으로 변환.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Clock clock;

    public GlobalExceptionHandler(@Autowired(required = false) Clock clock) {
        this.clock = clock != null ? clock : Clock.system(KstTime.ZONE);
    }

    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: {}", e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.of(errorCode, clock));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        return ResponseEntity.badRequest().body(buildInvalidInputResponse(e.getBindingResult()));
    }

    @ExceptionHandler(BindException.class)
    protected ResponseEntity<ErrorResponse> handleBindException(BindException e) {
        return ResponseEntity.badRequest().body(buildInvalidInputResponse(e.getBindingResult()));
    }

    private ErrorResponse buildInvalidInputResponse(BindingResult bindingResult) {
        List<ErrorResponse.FieldError> details = bindingResult.getFieldErrors().stream()
                .map(fe -> ErrorResponse.FieldError.builder()
                        .field(fe.getField())
                        .reason(fe.getDefaultMessage())
                        .build())
                .toList();

        return ErrorResponse.builder()
                .errorCode("INVALID_INPUT")
                .message("입력값이 올바르지 않습니다.")
                .timestamp(LocalDateTime.now(clock))
                .details(details)
                .build();
    }
}
