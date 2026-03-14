-- ============ 예약 / 케이스 ============

CREATE TABLE booking (
    booking_id          BIGSERIAL       PRIMARY KEY,
    public_id           VARCHAR(20)     NOT NULL UNIQUE,
    patient_id          BIGINT          NOT NULL REFERENCES patient(patient_id),
    intake_session_id   BIGINT          REFERENCES intake_session(intake_session_id),
    recommendation_id   BIGINT          REFERENCES recommendation(recommendation_id),
    slot_id             BIGINT          NOT NULL REFERENCES schedule_slot(slot_id),
    doctor_id           BIGINT          NOT NULL REFERENCES doctor_profile(doctor_profile_id),
    channel             VARCHAR(30)     NOT NULL DEFAULT 'WEB_SIMULATOR',
    appointment_date    DATE            NOT NULL,
    start_time          TIME            NOT NULL,
    end_time            TIME            NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'CONFIRMED',
    cancel_reason       VARCHAR(255),
    cancelled_at        TIMESTAMP,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE booking IS '예약 정보';
COMMENT ON COLUMN booking.public_id IS '외부 노출 ID (bk_xxxx)';
COMMENT ON COLUMN booking.patient_id IS '예약 환자';
COMMENT ON COLUMN booking.intake_session_id IS '연결된 인테이크 세션';
COMMENT ON COLUMN booking.recommendation_id IS '연결된 추천 결과';
COMMENT ON COLUMN booking.slot_id IS '예약된 슬롯';
COMMENT ON COLUMN booking.doctor_id IS '담당 의사 (DOCTOR_PROFILE)';
COMMENT ON COLUMN booking.channel IS '예약 채널 (WEB_SIMULATOR)';
COMMENT ON COLUMN booking.appointment_date IS '진료 날짜';
COMMENT ON COLUMN booking.start_time IS '진료 시작 시간';
COMMENT ON COLUMN booking.end_time IS '진료 종료 시간';
COMMENT ON COLUMN booking.status IS '상태 (CONFIRMED | CANCELLED | COMPLETED | NO_SHOW)';
COMMENT ON COLUMN booking.cancel_reason IS '취소 사유';
COMMENT ON COLUMN booking.cancelled_at IS '취소 시각';

CREATE INDEX idx_booking_patient ON booking(patient_id);
CREATE INDEX idx_booking_doctor ON booking(doctor_id);
CREATE INDEX idx_booking_slot ON booking(slot_id);
CREATE UNIQUE INDEX idx_booking_slot_active ON booking(slot_id) WHERE status != 'CANCELLED';

CREATE TABLE care_case (
    case_id             BIGSERIAL       PRIMARY KEY,
    public_id           VARCHAR(20)     NOT NULL UNIQUE,
    booking_id          BIGINT          NOT NULL UNIQUE REFERENCES booking(booking_id),
    patient_id          BIGINT          NOT NULL REFERENCES patient(patient_id),
    doctor_id           BIGINT          NOT NULL REFERENCES doctor_profile(doctor_profile_id),
    intake_session_id   BIGINT          REFERENCES intake_session(intake_session_id),
    status              VARCHAR(20)     NOT NULL DEFAULT 'CREATED',
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE care_case IS '진료 케이스 (예약과 1:1)';
COMMENT ON COLUMN care_case.public_id IS '외부 노출 ID (case_xxxx)';
COMMENT ON COLUMN care_case.booking_id IS '연결된 예약 (1:1)';
COMMENT ON COLUMN care_case.patient_id IS '환자';
COMMENT ON COLUMN care_case.doctor_id IS '담당 의사 (DOCTOR_PROFILE)';
COMMENT ON COLUMN care_case.intake_session_id IS '연결된 인테이크 세션';
COMMENT ON COLUMN care_case.status IS '상태 (CREATED | PREPARING | IN_PROGRESS | COMPLETED | FAILED | CANCELLED)';

CREATE INDEX idx_care_case_patient ON care_case(patient_id);
CREATE INDEX idx_care_case_doctor ON care_case(doctor_id);
