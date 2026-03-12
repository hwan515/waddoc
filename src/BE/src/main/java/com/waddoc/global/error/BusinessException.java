package com.waddoc.global.error;

import lombok.Getter;

/**
 * 비즈니스 로직 예외. ErrorCode를 담아 GlobalExceptionHandler에서 처리.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
