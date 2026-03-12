-- ============ 사용자 / 인증 ============

CREATE TABLE "user" (
    user_id         BIGSERIAL       PRIMARY KEY,
    public_id       VARCHAR(20)     NOT NULL UNIQUE,
    username        VARCHAR(50)     NOT NULL UNIQUE,
    password_hash   VARCHAR(255)    NOT NULL,
    name            VARCHAR(50)     NOT NULL,
    role            VARCHAR(20)     NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE "user" IS '시스템 사용자 (의사, 관리자, 보호자)';
COMMENT ON COLUMN "user".public_id IS '외부 노출 ID (usr_xxxx)';
COMMENT ON COLUMN "user".username IS '로그인 ID';
COMMENT ON COLUMN "user".password_hash IS 'bcrypt 해시';
COMMENT ON COLUMN "user".name IS '사용자 이름';
COMMENT ON COLUMN "user".role IS '역할 (ADMIN | DOCTOR | GUARDIAN)';
COMMENT ON COLUMN "user".is_active IS '계정 활성화 여부';

CREATE TABLE auth_session (
    session_id          BIGSERIAL       PRIMARY KEY,
    user_id             BIGINT          NOT NULL REFERENCES "user"(user_id),
    refresh_token_hash  VARCHAR(255)    NOT NULL,
    token_family        VARCHAR(50)     NOT NULL,
    device_info         VARCHAR(255),
    is_valid            BOOLEAN         NOT NULL DEFAULT TRUE,
    issued_at           TIMESTAMP       NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP       NOT NULL
);

COMMENT ON TABLE auth_session IS 'Refresh Token 세션 관리';
COMMENT ON COLUMN auth_session.refresh_token_hash IS 'RT 해시값';
COMMENT ON COLUMN auth_session.token_family IS '탈취 감지용 family ID';
COMMENT ON COLUMN auth_session.device_info IS '기기 정보';
COMMENT ON COLUMN auth_session.is_valid IS '세션 유효 여부';

CREATE INDEX idx_auth_session_user_id ON auth_session(user_id);
CREATE INDEX idx_auth_session_token_family ON auth_session(token_family);
