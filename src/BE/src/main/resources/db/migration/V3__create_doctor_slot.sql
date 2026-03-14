-- ============ 의사 / 슬롯 ============

CREATE TABLE doctor_profile (
    doctor_profile_id   BIGSERIAL       PRIMARY KEY,
    public_id           VARCHAR(20)     NOT NULL UNIQUE,
    user_id             BIGINT          NOT NULL UNIQUE REFERENCES "user"(user_id),
    department          VARCHAR(50)     NOT NULL,
    department_name     VARCHAR(50)     NOT NULL,
    specialty           VARCHAR(100),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE doctor_profile IS '의사 프로필';
COMMENT ON COLUMN doctor_profile.public_id IS '외부 노출 ID (doc_xxxx)';
COMMENT ON COLUMN doctor_profile.user_id IS 'USER 테이블 참조 (1:1)';
COMMENT ON COLUMN doctor_profile.department IS '진료과 코드 (INTERNAL_MEDICINE 등)';
COMMENT ON COLUMN doctor_profile.department_name IS '진료과 한글명';
COMMENT ON COLUMN doctor_profile.specialty IS '전문 분야';

CREATE TABLE schedule_slot (
    slot_id         BIGSERIAL       PRIMARY KEY,
    public_id       VARCHAR(20)     NOT NULL UNIQUE,
    doctor_id       BIGINT          NOT NULL REFERENCES doctor_profile(doctor_profile_id),
    slot_date       DATE            NOT NULL,
    start_time      TIME            NOT NULL,
    end_time        TIME            NOT NULL,
    is_booked       BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE schedule_slot IS '의사별 예약 가능 슬롯';
COMMENT ON COLUMN schedule_slot.public_id IS '외부 노출 ID (slot_xxxx)';
COMMENT ON COLUMN schedule_slot.doctor_id IS 'DOCTOR_PROFILE 참조';
COMMENT ON COLUMN schedule_slot.slot_date IS '진료 날짜';
COMMENT ON COLUMN schedule_slot.start_time IS '시작 시간';
COMMENT ON COLUMN schedule_slot.end_time IS '종료 시간';
COMMENT ON COLUMN schedule_slot.is_booked IS '예약 완료 여부';

CREATE INDEX idx_schedule_slot_doctor ON schedule_slot(doctor_id);
CREATE INDEX idx_schedule_slot_date ON schedule_slot(slot_date, is_booked);
