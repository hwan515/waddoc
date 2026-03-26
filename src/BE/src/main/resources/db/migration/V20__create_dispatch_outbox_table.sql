CREATE TABLE dispatch_outbox (
    outbox_id    BIGSERIAL    PRIMARY KEY,
    case_id      BIGINT       NOT NULL REFERENCES care_case(case_id),
    region_code  VARCHAR(30)  NOT NULL,
    destination  TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_pending
    ON dispatch_outbox(status, created_at)
    WHERE status = 'PENDING';
