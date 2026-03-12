package com.waddoc.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 비즈니스 에러 코드 정의. HTTP 상태 + API 에러코드 + 메시지 매핑.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "잘못된 입력입니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "리소스를 찾을 수 없습니다."),

    // Patient
    PATIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND", "해당 환자를 찾을 수 없습니다."),

    // Intake Session
    INTAKE_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "INTAKE_SESSION_NOT_FOUND", "인테이크 세션을 찾을 수 없습니다."),
    SESSION_STATE_INVALID(HttpStatus.BAD_REQUEST, "SESSION_STATE_INVALID", "현재 세션 상태에서는 해당 작업을 수행할 수 없습니다."),
    PATIENT_ALREADY_BOUND(HttpStatus.CONFLICT, "PATIENT_ALREADY_BOUND", "이미 환자가 바인딩된 세션입니다."),
    PATIENT_NOT_BOUND(HttpStatus.BAD_REQUEST, "PATIENT_NOT_BOUND", "세션에 환자가 아직 바인딩되지 않았습니다."),

    // Booking
    PATIENT_MISMATCH(HttpStatus.FORBIDDEN, "PATIENT_MISMATCH", "세션의 환자와 예약의 환자가 일치하지 않습니다."),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "예약을 찾을 수 없습니다."),
    BOOKING_SLOT_CONFLICT(HttpStatus.CONFLICT, "BOOKING_SLOT_CONFLICT", "이미 예약된 슬롯입니다."),
    BOOKING_ALREADY_CANCELLED(HttpStatus.BAD_REQUEST, "BOOKING_ALREADY_CANCELLED", "이미 취소된 예약입니다."),
    BOOKING_NOT_CANCELLABLE(HttpStatus.BAD_REQUEST, "BOOKING_NOT_CANCELLABLE", "취소할 수 없는 상태의 예약입니다."),
    SLOT_NOT_FOUND(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "유효하지 않은 슬롯입니다."),
    SLOT_NOT_IN_RECOMMENDATION(HttpStatus.BAD_REQUEST, "SLOT_NOT_IN_RECOMMENDATION", "해당 세션의 추천 결과에 포함되지 않은 슬롯입니다."),

    // Recommendation
    RECOMMENDATION_NOT_FOUND(HttpStatus.NOT_FOUND, "RECOMMENDATION_NOT_FOUND", "추천 결과를 찾을 수 없습니다."),
    NO_AVAILABLE_SLOT(HttpStatus.NOT_FOUND, "NO_AVAILABLE_SLOT", "해당 진료과에 예약 가능한 슬롯이 없습니다."),

    // File
    FILE_SAVE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "FILE_SAVE_FAILED", "파일 저장에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
