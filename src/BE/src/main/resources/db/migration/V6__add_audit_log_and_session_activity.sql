-- ============ 감사 로그 ============

CREATE TABLE audit_log (
    log_id          BIGSERIAL       PRIMARY KEY,
    public_id       VARCHAR(20)     NOT NULL UNIQUE,
    log_timestamp   TIMESTAMP       NOT NULL DEFAULT NOW(),
    actor_id        VARCHAR(20)     NOT NULL,
    actor_role      VARCHAR(20)     NOT NULL,
    action          VARCHAR(50)     NOT NULL,
    target_type     VARCHAR(50)     NOT NULL,
    target_id       VARCHAR(20),
    correlation_id  VARCHAR(40)     NOT NULL,
    detail_json     JSONB
);

COMMENT ON TABLE audit_log IS '전체 행위 감사 로그';
COMMENT ON COLUMN audit_log.public_id IS '외부 노출 ID (log_xxxx)';
COMMENT ON COLUMN audit_log.actor_id IS '행위자 ID (SYSTEM 또는 user publicId)';
COMMENT ON COLUMN audit_log.actor_role IS '행위자 역할 (SYSTEM | ADMIN | DOCTOR | GUARDIAN)';
COMMENT ON COLUMN audit_log.action IS '액션 (INTAKE_SESSION_CREATED 등)';
COMMENT ON COLUMN audit_log.target_type IS '대상 유형 (INTAKE_SESSION 등)';
COMMENT ON COLUMN audit_log.target_id IS '대상 publicId';
COMMENT ON COLUMN audit_log.correlation_id IS '전 구간 흐름 추적용 (corr_ints_xxx, corr_case_xxx)';

CREATE INDEX idx_audit_log_correlation ON audit_log(correlation_id);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_timestamp ON audit_log(log_timestamp);

-- ============ 인테이크 세션 활동 시각 ============

ALTER TABLE intake_session
  ADD COLUMN last_activity_at TIMESTAMP NOT NULL DEFAULT NOW();
