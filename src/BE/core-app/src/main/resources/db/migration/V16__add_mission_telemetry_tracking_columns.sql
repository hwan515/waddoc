ALTER TABLE mission
    ADD COLUMN last_telemetry_source_event_id VARCHAR(100),
    ADD COLUMN last_telemetry_seq_no BIGINT,
    ADD COLUMN last_telemetry_at TIMESTAMP;
