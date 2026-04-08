CREATE TABLE business_event_outbox (
    business_event_outbox_id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    event_type VARCHAR(120) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    correlation_id VARCHAR(128) NOT NULL,
    producer VARCHAR(60) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_business_event_outbox_status_created_at
    ON business_event_outbox (status, created_at);
