CREATE TABLE vehicle (
    vehicle_id          BIGSERIAL    PRIMARY KEY,
    public_id           VARCHAR(20)  NOT NULL UNIQUE,
    code                VARCHAR(50)  NOT NULL UNIQUE,
    region_code         VARCHAR(30)  NOT NULL,
    display_name        VARCHAR(100),
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    operational_status  VARCHAR(30)  NOT NULL DEFAULT 'OPERATIONAL',
    status_changed_at   TIMESTAMP,
    status_reason       VARCHAR(255),
    created_at          TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_vehicle_region_active
    ON vehicle(region_code)
    WHERE is_active = TRUE;

INSERT INTO vehicle (public_id, code, region_code, display_name)
VALUES ('veh_00000001', 'GIMCHEON-01', 'GIMCHEON_JEUNGSAN', '김천증산 1호차');
