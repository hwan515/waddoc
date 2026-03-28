ALTER TABLE mission
    ADD COLUMN target_waypoint_number INTEGER;

COMMENT ON COLUMN mission.target_waypoint_number IS '데모/로봇 출동용 목표 waypoint 번호';
