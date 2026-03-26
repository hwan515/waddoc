CREATE TABLE vital_measurement (
    vital_measurement_id   BIGSERIAL    PRIMARY KEY,
    case_id                BIGINT       NOT NULL UNIQUE REFERENCES care_case(case_id),
    temperature            DECIMAL(4,1),
    blood_pressure_sys     INTEGER,
    blood_pressure_dia     INTEGER,
    heart_rate             INTEGER,
    spo2                   INTEGER,
    ecg_waveform_json      JSONB,
    ecg_sampling_hz        INTEGER,
    ecg_duration_seconds   INTEGER,
    measured_at            TIMESTAMP,
    created_at             TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMP    NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE vital_measurement IS '진료 케이스별 최신 생체데이터';
COMMENT ON COLUMN vital_measurement.case_id IS '연결된 진료 케이스 (1:1)';
COMMENT ON COLUMN vital_measurement.temperature IS '체온';
COMMENT ON COLUMN vital_measurement.blood_pressure_sys IS '수축기 혈압';
COMMENT ON COLUMN vital_measurement.blood_pressure_dia IS '이완기 혈압';
COMMENT ON COLUMN vital_measurement.heart_rate IS '심박수';
COMMENT ON COLUMN vital_measurement.spo2 IS '산소포화도';
COMMENT ON COLUMN vital_measurement.ecg_waveform_json IS '측정 시점 ECG 샘플 파형(JSON 배열)';
COMMENT ON COLUMN vital_measurement.ecg_sampling_hz IS 'ECG 샘플링 주파수';
COMMENT ON COLUMN vital_measurement.ecg_duration_seconds IS 'ECG 샘플 길이(초)';
COMMENT ON COLUMN vital_measurement.measured_at IS '마지막 측정 시각';
