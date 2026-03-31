ALTER TABLE booking
    ADD COLUMN region_code VARCHAR(30);

UPDATE booking b
SET region_code = p.region_code
FROM patient p
WHERE b.patient_id = p.patient_id
  AND b.region_code IS NULL;

COMMENT ON COLUMN booking.region_code IS '예약 생성 시점 환자 권역 스냅샷';

CREATE INDEX idx_booking_region_schedule_active
    ON booking(region_code, appointment_date, start_time, end_time)
    WHERE status <> 'CANCELLED';

-- 기존 이력 데이터에는 같은 지역/시간의 COMPLETED 예약이 이미 중복으로 존재한다.
-- 따라서 DB 안전장치는 현재 활성 예약에만 적용하고, 더 넓은 시간 겹침 판정은 서비스 로직에서 강제한다.
CREATE UNIQUE INDEX uq_booking_region_date_start_active
    ON booking(region_code, appointment_date, start_time)
    WHERE status = 'CONFIRMED' AND region_code IS NOT NULL;
