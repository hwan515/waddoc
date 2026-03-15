-- ============ 인테이크 ============

CREATE TABLE intake_session (
    intake_session_id   BIGSERIAL       PRIMARY KEY,
    public_id           VARCHAR(20)     NOT NULL UNIQUE,
    patient_id          BIGINT          REFERENCES patient(patient_id),
    caller_number       VARCHAR(20),
    channel             VARCHAR(30)     NOT NULL DEFAULT 'WEB_SIMULATOR',
    status              VARCHAR(20)     NOT NULL DEFAULT 'STARTED',
    completion_reason   VARCHAR(50),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    ended_at            TIMESTAMP
);

COMMENT ON TABLE intake_session IS '전화 시뮬레이터 인테이크 세션';
COMMENT ON COLUMN intake_session.public_id IS '외부 노출 ID (ints_xxxx) — capability token 역할';
COMMENT ON COLUMN intake_session.patient_id IS '환자 (nullable — 식별 전 세션 생성 허용)';
COMMENT ON COLUMN intake_session.caller_number IS '발신번호';
COMMENT ON COLUMN intake_session.channel IS '채널 (WEB_SIMULATOR)';
COMMENT ON COLUMN intake_session.status IS '상태 (STARTED | IN_PROGRESS | COMPLETED | ABANDONED | FAILED)';
COMMENT ON COLUMN intake_session.completion_reason IS '종료 사유 (BOOKING_CREATED | NO_INPUT_TIMEOUT | USER_HANGUP | EXISTING_BOOKING_CHECKED)';
COMMENT ON COLUMN intake_session.ended_at IS '세션 종료 시각';

CREATE INDEX idx_intake_session_patient ON intake_session(patient_id);

CREATE TABLE intake_turn (
    turn_id             BIGSERIAL       PRIMARY KEY,
    intake_session_id   BIGINT          NOT NULL REFERENCES intake_session(intake_session_id),
    turn_order          INT             NOT NULL,
    turn_type           VARCHAR(10)     NOT NULL,
    prompt              TEXT,
    stt_text            TEXT,
    stt_confidence      DECIMAL(5,4),
    exception_code      VARCHAR(30),
    next_action         VARCHAR(30),
    tts_message         TEXT,
    audio_file_path     VARCHAR(500),
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE intake_turn IS '인테이크 턴별 입력/응답 기록';
COMMENT ON COLUMN intake_turn.turn_order IS '턴 순번';
COMMENT ON COLUMN intake_turn.turn_type IS '입력 유형 (VOICE)';
COMMENT ON COLUMN intake_turn.prompt IS 'TTS 안내문';
COMMENT ON COLUMN intake_turn.stt_text IS 'STT 변환 결과';
COMMENT ON COLUMN intake_turn.stt_confidence IS 'STT 신뢰도';
COMMENT ON COLUMN intake_turn.exception_code IS '예외 코드 (STT_FAIL | NO_INPUT | AMBIGUOUS_SYMPTOM | EMERGENCY_SUSPECTED)';
COMMENT ON COLUMN intake_turn.next_action IS '다음 액션 (ASK_SYMPTOM | RECOMMEND 등)';
COMMENT ON COLUMN intake_turn.tts_message IS '응답 TTS 메시지';
COMMENT ON COLUMN intake_turn.audio_file_path IS '오디오 파일 상대경로';

CREATE INDEX idx_intake_turn_session ON intake_turn(intake_session_id);

CREATE TABLE symptom_intake (
    symptom_id          BIGSERIAL       PRIMARY KEY,
    intake_session_id   BIGINT          NOT NULL REFERENCES intake_session(intake_session_id),
    symptom_text        TEXT            NOT NULL,
    symptom_category    VARCHAR(50),
    is_emergency        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE symptom_intake IS '수집된 증상 원문 및 분류 결과';
COMMENT ON COLUMN symptom_intake.symptom_text IS '증상 원문';
COMMENT ON COLUMN symptom_intake.symptom_category IS '분류 결과 (두통/발열 등)';
COMMENT ON COLUMN symptom_intake.is_emergency IS '응급 여부';

CREATE INDEX idx_symptom_intake_session ON symptom_intake(intake_session_id);

CREATE TABLE recommendation (
    recommendation_id   BIGSERIAL       PRIMARY KEY,
    public_id           VARCHAR(20)     NOT NULL UNIQUE,
    intake_session_id   BIGINT          NOT NULL REFERENCES intake_session(intake_session_id),
    symptom_id          BIGINT          REFERENCES symptom_intake(symptom_id),
    department          VARCHAR(50)     NOT NULL,
    department_name     VARCHAR(50)     NOT NULL,
    confidence_level    VARCHAR(10)     NOT NULL,
    is_emergency        BOOLEAN         NOT NULL DEFAULT FALSE,
    reason              TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE recommendation IS '진료과/의사 추천 결과';
COMMENT ON COLUMN recommendation.public_id IS '외부 노출 ID (rec_xxxx)';
COMMENT ON COLUMN recommendation.department IS '추천 진료과 코드 (INTERNAL_MEDICINE 등)';
COMMENT ON COLUMN recommendation.department_name IS '진료과 한글명';
COMMENT ON COLUMN recommendation.confidence_level IS '신뢰도 (HIGH | MEDIUM | LOW)';
COMMENT ON COLUMN recommendation.is_emergency IS '응급 여부';
COMMENT ON COLUMN recommendation.reason IS '추천 근거';

CREATE INDEX idx_recommendation_session ON recommendation(intake_session_id);
