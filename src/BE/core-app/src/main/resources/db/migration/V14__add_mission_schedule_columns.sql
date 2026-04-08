ALTER TABLE mission
    ADD COLUMN dispatched_at TIMESTAMP,
    ADD COLUMN estimated_arrival_time TIMESTAMP;

COMMENT ON COLUMN mission.dispatched_at IS '차량 출동 시각 또는 출동 예정 시각';
COMMENT ON COLUMN mission.estimated_arrival_time IS '차량 예상 도착 시각';

CREATE INDEX idx_mission_dispatched_at ON mission(dispatched_at);
