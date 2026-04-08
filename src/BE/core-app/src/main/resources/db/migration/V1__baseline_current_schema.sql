-- ============================================================
-- V1: Current baseline schema for local development
-- ============================================================

CREATE TABLE "user" (
    user_id                BIGSERIAL       PRIMARY KEY,
    public_id              VARCHAR(20)     NOT NULL UNIQUE,
    username               VARCHAR(50)     NOT NULL UNIQUE,
    password_hash          VARCHAR(255)    NOT NULL,
    name                   VARCHAR(50)     NOT NULL,
    role                   VARCHAR(20)     NOT NULL,
    is_active              BOOLEAN         NOT NULL DEFAULT TRUE,
    approval_status        VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    approval_requested_at  TIMESTAMP       NOT NULL DEFAULT NOW(),
    approved_by_user_id    BIGINT          REFERENCES "user"(user_id),
    approved_at            TIMESTAMP,
    created_at             TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE "user" IS '시스템 사용자 (의사, 관리자, 보호자)';
COMMENT ON COLUMN "user".public_id IS '외부 노출 ID (usr_xxxx)';
COMMENT ON COLUMN "user".username IS '로그인 ID';
COMMENT ON COLUMN "user".password_hash IS 'bcrypt 해시';
COMMENT ON COLUMN "user".name IS '사용자 이름';
COMMENT ON COLUMN "user".role IS '역할 (ADMIN | DOCTOR | GUARDIAN)';
COMMENT ON COLUMN "user".is_active IS '계정 활성화 여부 (승인 완료 후 true)';
COMMENT ON COLUMN "user".approval_status IS '승인 상태 (PENDING | APPROVED | REJECTED)';
COMMENT ON COLUMN "user".approval_requested_at IS '가입 승인 요청 시각';
COMMENT ON COLUMN "user".approved_by_user_id IS '승인 관리자 (user_id)';
COMMENT ON COLUMN "user".approved_at IS '승인/반려 처리 시각';

CREATE INDEX idx_user_approval_status ON "user"(approval_status);
CREATE INDEX idx_user_approved_by ON "user"(approved_by_user_id);

CREATE TABLE patient (
    patient_id                           BIGSERIAL       PRIMARY KEY,
    public_id                            VARCHAR(20)     NOT NULL UNIQUE,
    name                                 VARCHAR(50)     NOT NULL,
    birth_date                           DATE            NOT NULL,
    birth_date6                          CHAR(6)         NOT NULL,
    region_code                          VARCHAR(30),
    address                              VARCHAR(255),
    phone                                VARCHAR(20)     NOT NULL UNIQUE,
    reference_image_path                 VARCHAR(500),
    reference_image_uploaded_by_user_id  BIGINT          REFERENCES "user"(user_id),
    reference_image_updated_at           TIMESTAMP,
    created_at                           TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at                           TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE patient IS '환자 정보 (계정 없음, 관리자가 사전 등록)';
COMMENT ON COLUMN patient.public_id IS '외부 노출 ID (pat_xxxx)';
COMMENT ON COLUMN patient.name IS '환자명';
COMMENT ON COLUMN patient.birth_date IS '원본 생년월일 (YYYY-MM-DD)';
COMMENT ON COLUMN patient.birth_date6 IS '조회용 6자리 (YYMMDD)';
COMMENT ON COLUMN patient.region_code IS '지역 코드 (ex: ULLEUNG)';
COMMENT ON COLUMN patient.address IS '주소';
COMMENT ON COLUMN patient.phone IS '환자 휴대전화 번호 (01012345678) — 전역 unique';
COMMENT ON COLUMN patient.reference_image_path IS '환자 본인확인 기준 이미지 상대경로';
COMMENT ON COLUMN patient.reference_image_uploaded_by_user_id IS '기준 이미지 등록 관리자 (user_id)';
COMMENT ON COLUMN patient.reference_image_updated_at IS '기준 이미지 최근 등록/갱신 시각';

CREATE TABLE patient_guardian_link (
    link_id               BIGSERIAL       PRIMARY KEY,
    public_id             VARCHAR(20)     NOT NULL UNIQUE,
    patient_id            BIGINT          NOT NULL REFERENCES patient(patient_id),
    guardian_user_id      BIGINT          NOT NULL REFERENCES "user"(user_id),
    approved_by_user_id   BIGINT          REFERENCES "user"(user_id),
    relation              VARCHAR(30),
    status                VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    requested_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    approved_at           TIMESTAMP,
    CONSTRAINT uq_patient_guardian_link_pair UNIQUE (patient_id, guardian_user_id)
);

COMMENT ON TABLE patient_guardian_link IS '환자-보호자 연결 요청 및 승인 정보';
COMMENT ON COLUMN patient_guardian_link.public_id IS '외부 노출 ID (link_xxxx)';
COMMENT ON COLUMN patient_guardian_link.guardian_user_id IS '보호자 계정 (user_id)';
COMMENT ON COLUMN patient_guardian_link.approved_by_user_id IS '승인 관리자 (user_id)';
COMMENT ON COLUMN patient_guardian_link.relation IS '관계 (자녀, 배우자 등)';
COMMENT ON COLUMN patient_guardian_link.status IS '상태 (PENDING | APPROVED | REJECTED)';
COMMENT ON COLUMN patient_guardian_link.requested_at IS '보호자 가입 요청 시각';
COMMENT ON COLUMN patient_guardian_link.approved_at IS '승인/반려 처리 시각';

CREATE INDEX idx_patient_guardian_link_patient ON patient_guardian_link(patient_id);
CREATE INDEX idx_patient_guardian_link_user ON patient_guardian_link(guardian_user_id);
CREATE INDEX idx_patient_guardian_link_status ON patient_guardian_link(status);
CREATE INDEX idx_patient_guardian_link_approver ON patient_guardian_link(approved_by_user_id);

CREATE TABLE doctor_profile (
    doctor_profile_id  BIGSERIAL       PRIMARY KEY,
    public_id          VARCHAR(20)     NOT NULL UNIQUE,
    user_id            BIGINT          NOT NULL UNIQUE REFERENCES "user"(user_id),
    department         VARCHAR(50)     NOT NULL,
    department_name    VARCHAR(50)     NOT NULL,
    created_at         TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE doctor_profile IS '의사 프로필';
COMMENT ON COLUMN doctor_profile.public_id IS '외부 노출 ID (doc_xxxx)';
COMMENT ON COLUMN doctor_profile.user_id IS 'USER 테이블 참조 (1:1)';
COMMENT ON COLUMN doctor_profile.department IS '진료과 코드 (INTERNAL_MEDICINE 등)';
COMMENT ON COLUMN doctor_profile.department_name IS '진료과 한글명';

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

CREATE TABLE intake_session (
    intake_session_id           BIGSERIAL       PRIMARY KEY,
    public_id                   VARCHAR(20)     NOT NULL UNIQUE,
    patient_id                  BIGINT          REFERENCES patient(patient_id),
    caller_number               VARCHAR(20),
    channel                     VARCHAR(30)     NOT NULL DEFAULT 'WEB_SIMULATOR',
    status                      VARCHAR(20)     NOT NULL DEFAULT 'STARTED',
    completion_reason           VARCHAR(50),
    ended_at                    TIMESTAMP,
    last_activity_at            TIMESTAMP       NOT NULL DEFAULT NOW(),
    selected_department         VARCHAR(50),
    selected_department_name    VARCHAR(50),
    selection_reason            TEXT,
    selection_confidence_level  VARCHAR(10),
    selection_is_emergency      BOOLEAN,
    offered_slot_ids_json       TEXT,
    selection_updated_at        TIMESTAMP,
    created_at                  TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE intake_session IS '전화 시뮬레이터 인테이크 세션';
COMMENT ON COLUMN intake_session.public_id IS '외부 노출 ID (ints_xxxx) — capability token 역할';
COMMENT ON COLUMN intake_session.patient_id IS '환자 (nullable — 식별 전 세션 생성 허용)';
COMMENT ON COLUMN intake_session.caller_number IS '발신번호';
COMMENT ON COLUMN intake_session.channel IS '채널 (WEB_SIMULATOR | PHONE)';
COMMENT ON COLUMN intake_session.status IS '상태 (STARTED | IN_PROGRESS | COMPLETED | ABANDONED | FAILED)';
COMMENT ON COLUMN intake_session.completion_reason IS '종료 사유 (BOOKING_CREATED | NO_INPUT_TIMEOUT | USER_HANGUP | EXISTING_BOOKING_CHECKED)';
COMMENT ON COLUMN intake_session.ended_at IS '세션 종료 시각';
COMMENT ON COLUMN intake_session.selected_department IS '선택된 진료과 코드';
COMMENT ON COLUMN intake_session.selected_department_name IS '선택된 진료과 한글명';
COMMENT ON COLUMN intake_session.selection_reason IS '선택 근거';
COMMENT ON COLUMN intake_session.selection_confidence_level IS '선택 신뢰도 (HIGH | MEDIUM | LOW)';
COMMENT ON COLUMN intake_session.selection_is_emergency IS '응급 여부';
COMMENT ON COLUMN intake_session.offered_slot_ids_json IS '안내된 슬롯 publicId 목록 (JSON 배열)';
COMMENT ON COLUMN intake_session.selection_updated_at IS '선택 정보 갱신 시각';

CREATE INDEX idx_intake_session_patient ON intake_session(patient_id);

CREATE TABLE booking (
    booking_id          BIGSERIAL       PRIMARY KEY,
    public_id           VARCHAR(20)     NOT NULL UNIQUE,
    patient_id          BIGINT          NOT NULL REFERENCES patient(patient_id),
    intake_session_id   BIGINT          REFERENCES intake_session(intake_session_id),
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
COMMENT ON COLUMN booking.slot_id IS '예약된 슬롯';
COMMENT ON COLUMN booking.doctor_id IS '담당 의사 (DOCTOR_PROFILE)';
COMMENT ON COLUMN booking.channel IS '예약 채널 (WEB_SIMULATOR | PHONE)';
COMMENT ON COLUMN booking.appointment_date IS '진료 날짜';
COMMENT ON COLUMN booking.start_time IS '진료 시작 시간';
COMMENT ON COLUMN booking.end_time IS '진료 종료 시간';
COMMENT ON COLUMN booking.status IS '상태 (CONFIRMED | CANCELLED | COMPLETED | NO_SHOW)';
COMMENT ON COLUMN booking.cancel_reason IS '취소 사유';
COMMENT ON COLUMN booking.cancelled_at IS '취소 시각';

CREATE INDEX idx_booking_patient ON booking(patient_id);
CREATE INDEX idx_booking_doctor ON booking(doctor_id);
CREATE INDEX idx_booking_slot ON booking(slot_id);
CREATE UNIQUE INDEX idx_booking_slot_active ON booking(slot_id) WHERE status <> 'CANCELLED';

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

CREATE TABLE mission (
    mission_id       BIGSERIAL      PRIMARY KEY,
    public_id        VARCHAR(20)    NOT NULL UNIQUE,
    case_id          BIGINT         NOT NULL UNIQUE REFERENCES care_case(case_id),
    vehicle_id       VARCHAR(50),
    destination      TEXT,
    phase            VARCHAR(20)    NOT NULL DEFAULT 'CREATED',
    latitude         DECIMAL(10,7),
    longitude        DECIMAL(10,7),
    completed_at     TIMESTAMP,
    created_at       TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP      NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE mission IS '차량 출동/현장 운영 미션';
COMMENT ON COLUMN mission.public_id IS '외부 노출 ID (ms_xxxx)';
COMMENT ON COLUMN mission.case_id IS '연결된 진료 케이스';
COMMENT ON COLUMN mission.vehicle_id IS '차량 ID';
COMMENT ON COLUMN mission.destination IS '목적지 주소';
COMMENT ON COLUMN mission.phase IS '상태 (CREATED | DISPATCHED | EN_ROUTE | ARRIVED | VERIFYING | CONSULTING | RETURNING | COMPLETED | FAILED | INCIDENT)';
COMMENT ON COLUMN mission.latitude IS '현재 위도';
COMMENT ON COLUMN mission.longitude IS '현재 경도';
COMMENT ON COLUMN mission.completed_at IS '미션 완료 시각';

CREATE INDEX idx_mission_phase ON mission(phase);

CREATE TABLE consultation_session (
    session_id                 BIGSERIAL      PRIMARY KEY,
    public_id                  VARCHAR(20)    NOT NULL UNIQUE,
    case_id                    BIGINT         NOT NULL UNIQUE REFERENCES care_case(case_id),
    status                     VARCHAR(20)    NOT NULL DEFAULT 'CREATED',
    room_id                    VARCHAR(100),
    livekit_url                VARCHAR(255),
    doctor_connection_state    VARCHAR(20)    NOT NULL DEFAULT 'DISCONNECTED',
    patient_connection_state   VARCHAR(20)    NOT NULL DEFAULT 'DISCONNECTED',
    doctor_joined_at           TIMESTAMP,
    patient_joined_at          TIMESTAMP,
    started_at                 TIMESTAMP,
    ended_at                   TIMESTAMP,
    duration_minutes           INT,
    created_at                 TIMESTAMP      NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE consultation_session IS 'LiveKit 1:1 화상진료 세션';
COMMENT ON COLUMN consultation_session.public_id IS '외부 노출 ID (ses_xxxx)';
COMMENT ON COLUMN consultation_session.case_id IS '연결된 진료 케이스';
COMMENT ON COLUMN consultation_session.status IS '상태 (CREATED | READY | IN_PROGRESS | COMPLETED | FAILED | ABANDONED)';
COMMENT ON COLUMN consultation_session.room_id IS 'LiveKit room ID';
COMMENT ON COLUMN consultation_session.livekit_url IS '참가용 LiveKit URL';
COMMENT ON COLUMN consultation_session.doctor_connection_state IS '의사 연결 상태 (CONNECTED | RECONNECTING | DISCONNECTED)';
COMMENT ON COLUMN consultation_session.patient_connection_state IS '환자 연결 상태 (CONNECTED | RECONNECTING | DISCONNECTED)';

CREATE INDEX idx_consultation_session_status ON consultation_session(status);

CREATE TABLE consultation_summary (
    summary_id                 BIGSERIAL      PRIMARY KEY,
    session_id                 BIGINT         NOT NULL UNIQUE REFERENCES consultation_session(session_id),
    summary_note               TEXT           NOT NULL,
    is_prescription_issued     BOOLEAN        NOT NULL DEFAULT FALSE,
    prescription_note          TEXT,
    needs_follow_up            BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at                 TIMESTAMP      NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE consultation_summary IS '진료 요약 및 처방/재진 여부';
COMMENT ON COLUMN consultation_summary.session_id IS '연결된 화상진료 세션';
COMMENT ON COLUMN consultation_summary.summary_note IS '진료 요약(환자 증상 및 소견서)';
COMMENT ON COLUMN consultation_summary.is_prescription_issued IS '처방 여부';
COMMENT ON COLUMN consultation_summary.prescription_note IS '처방 메모(의약품)';
COMMENT ON COLUMN consultation_summary.needs_follow_up IS '재진 필요 여부';
