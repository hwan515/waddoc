-- ============ 환자 ============

CREATE TABLE patient (
    patient_id      BIGSERIAL       PRIMARY KEY,
    public_id       VARCHAR(20)     NOT NULL UNIQUE,
    name            VARCHAR(50)     NOT NULL,
    birth_date      DATE            NOT NULL,
    birth_date6     CHAR(6)         NOT NULL,
    region_code     VARCHAR(30),
    address         VARCHAR(255),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE patient IS '환자 정보 (계정 없음, 관리자가 사전 등록)';
COMMENT ON COLUMN patient.public_id IS '외부 노출 ID (pat_xxxx)';
COMMENT ON COLUMN patient.name IS '환자명';
COMMENT ON COLUMN patient.birth_date IS '원본 생년월일 (YYYY-MM-DD)';
COMMENT ON COLUMN patient.birth_date6 IS '조회용 6자리 (YYMMDD)';
COMMENT ON COLUMN patient.region_code IS '지역 코드 (ex: ULLEUNG)';
COMMENT ON COLUMN patient.address IS '주소';

CREATE TABLE patient_phone_binding (
    binding_id      BIGSERIAL       PRIMARY KEY,
    patient_id      BIGINT          NOT NULL REFERENCES patient(patient_id),
    phone           VARCHAR(20)     NOT NULL UNIQUE,
    is_primary      BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE patient_phone_binding IS '환자 전화번호 바인딩 (1번호=1환자 원칙)';
COMMENT ON COLUMN patient_phone_binding.phone IS '전화번호 (01012345678) — 전역 unique';
COMMENT ON COLUMN patient_phone_binding.is_primary IS '대표 번호 여부';

CREATE INDEX idx_patient_phone_binding_patient ON patient_phone_binding(patient_id);

CREATE TABLE patient_face_reference (
    face_ref_id     BIGSERIAL       PRIMARY KEY,
    public_id       VARCHAR(20)     NOT NULL UNIQUE,
    patient_id      BIGINT          NOT NULL REFERENCES patient(patient_id),
    photo_path      VARCHAR(500)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    uploaded_by     BIGINT          REFERENCES "user"(user_id),
    approved_by     BIGINT          REFERENCES "user"(user_id),
    approved_at     TIMESTAMP,
    deleted_at      TIMESTAMP,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE patient_face_reference IS '환자 사전 등록 얼굴 사진';
COMMENT ON COLUMN patient_face_reference.public_id IS '외부 노출 ID (face_xxxx)';
COMMENT ON COLUMN patient_face_reference.photo_path IS '사진 상대경로';
COMMENT ON COLUMN patient_face_reference.status IS '상태 (PENDING | APPROVED | REJECTED)';
COMMENT ON COLUMN patient_face_reference.uploaded_by IS '등록자 (user_id)';
COMMENT ON COLUMN patient_face_reference.approved_by IS '승인 관리자 (user_id)';
COMMENT ON COLUMN patient_face_reference.deleted_at IS '소프트 삭제 시각';

CREATE INDEX idx_patient_face_ref_patient ON patient_face_reference(patient_id);

CREATE TABLE patient_guardian_link (
    link_id             BIGSERIAL       PRIMARY KEY,
    public_id           VARCHAR(20)     NOT NULL UNIQUE,
    patient_id          BIGINT          NOT NULL REFERENCES patient(patient_id),
    guardian_user_id    BIGINT          NOT NULL REFERENCES "user"(user_id),
    relation            VARCHAR(30),
    linked_at           TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE patient_guardian_link IS '환자-보호자 연결';
COMMENT ON COLUMN patient_guardian_link.public_id IS '외부 노출 ID (link_xxxx)';
COMMENT ON COLUMN patient_guardian_link.guardian_user_id IS '보호자 계정 (user_id)';
COMMENT ON COLUMN patient_guardian_link.relation IS '관계 (자녀, 배우자 등)';

CREATE INDEX idx_patient_guardian_link_patient ON patient_guardian_link(patient_id);
CREATE INDEX idx_patient_guardian_link_user ON patient_guardian_link(guardian_user_id);
