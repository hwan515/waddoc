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
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "아이디 또는 비밀번호가 올바르지 않습니다."),
    AUTH_ACCOUNT_PENDING_APPROVAL(HttpStatus.UNAUTHORIZED, "AUTH_ACCOUNT_PENDING_APPROVAL", "관리자 승인 대기 중인 계정입니다."),
    AUTH_ACCOUNT_LOCKED(HttpStatus.LOCKED, "AUTH_ACCOUNT_LOCKED", "비활성화된 계정입니다."),
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "인증이 필요합니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "해당 작업에 접근할 권한이 없습니다."),
    AUTH_REFRESH_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH_REFRESH_EXPIRED", "리프레시 토큰이 만료되었거나 유효하지 않습니다."),
    AUTH_TOKEN_REUSE(HttpStatus.UNAUTHORIZED, "AUTH_TOKEN_REUSE", "이미 사용되었거나 무효화된 리프레시 토큰입니다."),
    AUTH_DOCTOR_PROFILE_REQUIRED(HttpStatus.FORBIDDEN, "AUTH_DOCTOR_PROFILE_REQUIRED", "의사 프로필이 연결되지 않은 계정입니다."),
    AUTH_USERNAME_CONFLICT(HttpStatus.CONFLICT, "AUTH_USERNAME_CONFLICT", "이미 사용 중인 로그인 ID입니다."),
    AUTH_GUARDIAN_NOT_APPROVED(HttpStatus.FORBIDDEN, "AUTH_GUARDIAN_NOT_APPROVED", "승인되지 않았거나 연결된 환자가 없는 보호자 계정입니다."),

    // Patient
    PATIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PATIENT_NOT_FOUND", "해당 환자를 찾을 수 없습니다."),
    PATIENT_PHONE_NOT_FOUND(HttpStatus.NOT_FOUND, "PATIENT_PHONE_NOT_FOUND", "환자 전화번호로 대상을 찾을 수 없습니다."),
    GUARDIAN_NOT_LINKED(HttpStatus.FORBIDDEN, "GUARDIAN_NOT_LINKED", "해당 환자에 연결되지 않은 보호자 계정입니다."),
    GUARDIAN_LINK_ALREADY_EXISTS(HttpStatus.CONFLICT, "GUARDIAN_LINK_ALREADY_EXISTS", "동일 환자-보호자 가입 이력이 이미 존재합니다."),
    GUARDIAN_LINK_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "GUARDIAN_LINK_REQUEST_NOT_FOUND", "보호자 가입 요청을 찾을 수 없습니다."),
    GUARDIAN_LINK_ALREADY_PROCESSED(HttpStatus.CONFLICT, "GUARDIAN_LINK_ALREADY_PROCESSED", "이미 승인 또는 반려된 요청입니다."),

    // Intake Session
    INTAKE_SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "INTAKE_SESSION_NOT_FOUND", "인테이크 세션을 찾을 수 없습니다."),
    INVALID_PATCH_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_PATCH_REQUEST", "허용되지 않는 세션 변경 요청입니다."),
    SESSION_STATE_INVALID(HttpStatus.BAD_REQUEST, "SESSION_STATE_INVALID", "현재 세션 상태에서는 해당 작업을 수행할 수 없습니다."),
    PATIENT_ALREADY_BOUND(HttpStatus.CONFLICT, "PATIENT_ALREADY_BOUND", "이미 환자가 바인딩된 세션입니다."),
    PATIENT_NOT_BOUND(HttpStatus.BAD_REQUEST, "PATIENT_NOT_BOUND", "세션에 환자가 아직 바인딩되지 않았습니다."),

    // Booking / Case
    PATIENT_MISMATCH(HttpStatus.FORBIDDEN, "PATIENT_MISMATCH", "세션의 환자와 예약의 환자가 일치하지 않습니다."),
    BOOKING_NOT_FOUND(HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "예약을 찾을 수 없습니다."),
    CASE_NOT_FOUND(HttpStatus.NOT_FOUND, "CASE_NOT_FOUND", "진료 케이스를 찾을 수 없습니다."),
    CASE_NOT_ASSIGNED(HttpStatus.FORBIDDEN, "CASE_NOT_ASSIGNED", "해당 의사에게 배정되지 않은 케이스입니다."),
    MISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "MISSION_NOT_FOUND", "미션을 찾을 수 없습니다."),
    MISSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "MISSION_ALREADY_EXISTS", "이미 생성된 미션이 존재합니다."),
    MISSION_PHASE_TRANSITION_INVALID(HttpStatus.BAD_REQUEST, "MISSION_PHASE_TRANSITION_INVALID", "허용되지 않는 미션 단계 전환입니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "진료 세션을 찾을 수 없습니다."),
    LIVEKIT_ROOM_CREATE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "LIVEKIT_ROOM_CREATE_FAILED", "LiveKit 룸 생성에 실패했습니다."),
    CONSULTATION_SUMMARY_NOT_FOUND(HttpStatus.NOT_FOUND, "CONSULTATION_SUMMARY_NOT_FOUND", "진료 요약을 찾을 수 없습니다."),
    LIVEKIT_WEBHOOK_INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "LIVEKIT_WEBHOOK_INVALID_SIGNATURE", "유효하지 않은 LiveKit Webhook 서명입니다."),
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
