CREATE TABLE processed_event (
    processed_event_id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE notification_log (
    notification_log_id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(120) NOT NULL,
    recipient_type VARCHAR(30) NOT NULL,
    recipient_id VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE sms_delivery (
    sms_delivery_id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    recipient_phone VARCHAR(30) NOT NULL,
    message TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE doctor_notification_projection (
    doctor_notification_projection_id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL UNIQUE,
    doctor_user_id VARCHAR(64) NOT NULL,
    doctor_id VARCHAR(64) NOT NULL,
    booking_id VARCHAR(64) NOT NULL,
    care_case_id VARCHAR(64) NOT NULL,
    payload_json TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_processed_event_event_id ON processed_event (event_id);
CREATE INDEX idx_doctor_notification_projection_doctor_user_id ON doctor_notification_projection (doctor_user_id);
