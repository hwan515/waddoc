-- prod scenario dummy seed
-- 권역: 김천/안동/영주/상주, 권역별 active 차량 1대
-- 의사: 내과 2명 + 정형외과/피부과/신경과/안과 각 1명
-- TODO: 장기적으로는 prod one-shot Java seeder로 대체하고, SQL fallback은 비상용으로만 유지한다.

BEGIN;

DO $$
DECLARE
    v_user RECORD;
    v_patient RECORD;
    v_vehicle RECORD;
BEGIN
    FOR v_user IN
        SELECT * FROM (VALUES
            ('seed_prod_admin', 'usr_prd_admin01'),
            ('seed_prod_doc_im_01', 'usr_prd_doc01'),
            ('seed_prod_doc_im_02', 'usr_prd_doc02'),
            ('seed_prod_doc_ortho_01', 'usr_prd_doc03'),
            ('seed_prod_doc_derm_01', 'usr_prd_doc04'),
            ('seed_prod_doc_neuro_01', 'usr_prd_doc05'),
            ('seed_prod_doc_eye_01', 'usr_prd_doc06'),
            ('seed_prod_guardian_01', 'usr_prd_grd01')
        ) AS seeded_users(username, public_id)
    LOOP
        IF EXISTS (SELECT 1 FROM "user" WHERE username = v_user.username AND public_id <> v_user.public_id) THEN
            RAISE EXCEPTION 'Username % already exists with a different public_id', v_user.username;
        END IF;
    END LOOP;

    FOR v_patient IN
        SELECT * FROM (VALUES
            ('01011111111', 'pat_prd_gim_sudo01'),
            ('01022222222', 'pat_prd_gim_hwang01'),
            ('01033333333', 'pat_prd_gim_jirye01'),
            ('01044444444', 'pat_prd_andong01'),
            ('01055555555', 'pat_prd_yj_marak01'),
            ('01066666666', 'pat_prd_yj_nam01'),
            ('01077777777', 'pat_prd_yj_nam02'),
            ('01088888888', 'pat_prd_sj_oeseo01'),
            ('01099999999', 'pat_prd_sj_euncheok01'),
            ('01012341234', 'pat_prd_sj_hwanam01'),
            ('01023452345', 'pat_prd_sj_hwanam02')
        ) AS seeded_patients(phone, public_id)
    LOOP
        IF EXISTS (SELECT 1 FROM patient WHERE phone = v_patient.phone AND public_id <> v_patient.public_id) THEN
            RAISE EXCEPTION 'Patient phone % is already used by a different record', v_patient.phone;
        END IF;
    END LOOP;

    FOR v_vehicle IN
        SELECT * FROM (VALUES
            ('GIMCHEON', 'GIMCHEON-01'),
            ('ANDONG', 'ANDONG-01'),
            ('YEONGJU', 'YEONGJU-01'),
            ('SANGJU', 'SANGJU-01')
        ) AS seeded_vehicles(region_code, code)
    LOOP
        IF EXISTS (
            SELECT 1 FROM vehicle
             WHERE region_code = v_vehicle.region_code
               AND is_active = TRUE
               AND code <> v_vehicle.code
        ) THEN
            RAISE EXCEPTION '% already has another active vehicle', v_vehicle.region_code;
        END IF;
    END LOOP;
END $$;

INSERT INTO "user" (
    public_id, username, password_hash, name, role, is_active, approval_status,
    approval_requested_at, approved_by_user_id, approved_at, created_at, updated_at
) VALUES (
    'usr_prd_admin01', 'seed_prod_admin', '$2a$10$IH3MU2MFjspMAzlawklnHe77FTWUAbUegt7dJV03R28go2QRGNBb.',
    '운영 더미 관리자', 'ADMIN', TRUE, 'APPROVED', NOW(), NULL, NOW(), NOW(), NOW()
)
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    name = EXCLUDED.name,
    role = EXCLUDED.role,
    is_active = EXCLUDED.is_active,
    approval_status = EXCLUDED.approval_status,
    approved_by_user_id = EXCLUDED.approved_by_user_id,
    approved_at = EXCLUDED.approved_at,
    updated_at = NOW();

INSERT INTO "user" (
    public_id, username, password_hash, name, role, is_active, approval_status,
    approval_requested_at, approved_by_user_id, approved_at, created_at, updated_at
) VALUES
('usr_prd_doc01', 'seed_prod_doc_im_01', '$2a$10$IH3MU2MFjspMAzlawklnHe77FTWUAbUegt7dJV03R28go2QRGNBb.', '김도현', 'DOCTOR', TRUE, 'APPROVED', NOW(), (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('usr_prd_doc02', 'seed_prod_doc_im_02', '$2a$10$IH3MU2MFjspMAzlawklnHe77FTWUAbUegt7dJV03R28go2QRGNBb.', '박지연', 'DOCTOR', TRUE, 'APPROVED', NOW(), (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('usr_prd_doc03', 'seed_prod_doc_ortho_01', '$2a$10$IH3MU2MFjspMAzlawklnHe77FTWUAbUegt7dJV03R28go2QRGNBb.', '최준혁', 'DOCTOR', TRUE, 'APPROVED', NOW(), (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('usr_prd_doc04', 'seed_prod_doc_derm_01', '$2a$10$IH3MU2MFjspMAzlawklnHe77FTWUAbUegt7dJV03R28go2QRGNBb.', '임수빈', 'DOCTOR', TRUE, 'APPROVED', NOW(), (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('usr_prd_doc05', 'seed_prod_doc_neuro_01', '$2a$10$IH3MU2MFjspMAzlawklnHe77FTWUAbUegt7dJV03R28go2QRGNBb.', '정유진', 'DOCTOR', TRUE, 'APPROVED', NOW(), (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('usr_prd_doc06', 'seed_prod_doc_eye_01', '$2a$10$IH3MU2MFjspMAzlawklnHe77FTWUAbUegt7dJV03R28go2QRGNBb.', '장소라', 'DOCTOR', TRUE, 'APPROVED', NOW(), (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW())
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    name = EXCLUDED.name,
    role = EXCLUDED.role,
    is_active = EXCLUDED.is_active,
    approval_status = EXCLUDED.approval_status,
    approved_by_user_id = EXCLUDED.approved_by_user_id,
    approved_at = EXCLUDED.approved_at,
    updated_at = NOW();

INSERT INTO doctor_profile (public_id, user_id, department, department_name, created_at) VALUES
('doc_prd_001', (SELECT user_id FROM "user" WHERE username = 'seed_prod_doc_im_01'), 'INTERNAL_MEDICINE', '내과', NOW()),
('doc_prd_002', (SELECT user_id FROM "user" WHERE username = 'seed_prod_doc_im_02'), 'INTERNAL_MEDICINE', '내과', NOW()),
('doc_prd_003', (SELECT user_id FROM "user" WHERE username = 'seed_prod_doc_ortho_01'), 'ORTHOPEDICS', '정형외과', NOW()),
('doc_prd_004', (SELECT user_id FROM "user" WHERE username = 'seed_prod_doc_derm_01'), 'DERMATOLOGY', '피부과', NOW()),
('doc_prd_005', (SELECT user_id FROM "user" WHERE username = 'seed_prod_doc_neuro_01'), 'NEUROLOGY', '신경과', NOW()),
('doc_prd_006', (SELECT user_id FROM "user" WHERE username = 'seed_prod_doc_eye_01'), 'OPHTHALMOLOGY', '안과', NOW())
ON CONFLICT (user_id) DO UPDATE
SET department = EXCLUDED.department, department_name = EXCLUDED.department_name;

INSERT INTO "user" (
    public_id, username, password_hash, name, role, is_active, approval_status,
    approval_requested_at, approved_by_user_id, approved_at, created_at, updated_at
) VALUES (
    'usr_prd_grd01', 'seed_prod_guardian_01', '$2a$10$IH3MU2MFjspMAzlawklnHe77FTWUAbUegt7dJV03R28go2QRGNBb.',
    '김보호', 'GUARDIAN', TRUE, 'APPROVED', NOW(), (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()
)
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    name = EXCLUDED.name,
    role = EXCLUDED.role,
    is_active = EXCLUDED.is_active,
    approval_status = EXCLUDED.approval_status,
    approved_by_user_id = EXCLUDED.approved_by_user_id,
    approved_at = EXCLUDED.approved_at,
    updated_at = NOW();

INSERT INTO vehicle (
    public_id, code, region_code, display_name, is_active, operational_status,
    status_changed_at, status_reason, created_at, updated_at
) VALUES
('veh_GIMCHEON_01', 'GIMCHEON-01', 'GIMCHEON', '김천 1호차', TRUE, 'OPERATIONAL', NOW(), NULL, NOW(), NOW()),
('veh_ANDONG_01', 'ANDONG-01', 'ANDONG', '안동 1호차', TRUE, 'OPERATIONAL', NOW(), NULL, NOW(), NOW()),
('veh_YEONGJU_01', 'YEONGJU-01', 'YEONGJU', '영주 1호차', TRUE, 'OPERATIONAL', NOW(), NULL, NOW(), NOW()),
('veh_SANGJU_01', 'SANGJU-01', 'SANGJU', '상주 1호차', TRUE, 'OPERATIONAL', NOW(), NULL, NOW(), NOW())
ON CONFLICT (code) DO UPDATE
SET public_id = EXCLUDED.public_id,
    region_code = EXCLUDED.region_code,
    display_name = EXCLUDED.display_name,
    is_active = EXCLUDED.is_active,
    operational_status = EXCLUDED.operational_status,
    status_changed_at = EXCLUDED.status_changed_at,
    status_reason = EXCLUDED.status_reason,
    updated_at = NOW();

INSERT INTO patient (
    public_id, name, birth_date, birth_date6, gender, region_code, address, phone,
    reference_image_path, reference_image_uploaded_by_user_id, reference_image_updated_at, created_at, updated_at
) VALUES
('pat_prd_gim_sudo01', '홍길동', DATE '1911-11-11', '111111', 'MALE', 'GIMCHEON', '경북 김천시 증산면 수도리 수도길 865-13-1438', '01011111111', 'patients/pat_prd_gim_sudo01/reference.jpg', (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('pat_prd_gim_hwang01', '김철수', DATE '1968-04-15', '680415', 'MALE', 'GIMCHEON', '경북 김천시 증산면 황점리 황점1길 70-100-807', '01022222222', 'patients/pat_prd_gim_hwang01/reference.jpg', (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('pat_prd_gim_jirye01', '박영수', DATE '1957-09-03', '570903', 'MALE', 'GIMCHEON', '경북 김천시 지례면 박곡리 지례예술촌길 390-427', '01033333333', 'patients/pat_prd_gim_jirye01/reference.jpg', (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('pat_prd_andong01', '안순자', DATE '1954-02-18', '540218', 'FEMALE', 'ANDONG', '경북 안동시 임동면 사월리(보마골) 한절골길 356-384', '01044444444', 'patients/pat_prd_andong01/reference.jpg', (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('pat_prd_yj_marak01', '최복례', DATE '1952-08-21', '520821', 'FEMALE', 'YEONGJU', '경북 영주시 단산면 마락리 영단로 1236-1-1522-4', '01055555555', 'patients/pat_prd_yj_marak01/reference.jpg', (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('pat_prd_yj_nam01', '이영희', DATE '1948-03-17', '480317', 'FEMALE', 'YEONGJU', '경북 영주시 부석면 남대리 영부로 847-3-1199-24(남대리)', '01066666666', 'patients/pat_prd_yj_nam01/reference.jpg', (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('pat_prd_yj_nam02', '정말자', DATE '1959-12-09', '591209', 'FEMALE', 'YEONGJU', '경북 영주시 부석면 남대리 영부로890번길 17-236(남대리)', '01077777777', 'patients/pat_prd_yj_nam02/reference.jpg', (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('pat_prd_sj_oeseo01', '장영호', DATE '1961-07-26', '610726', 'MALE', 'SANGJU', '경북 상주시 외서면 대전2리 갈골 하나동1길, 하나동2길, 송죽동1길, 송죽동2길, 행복동길, 낙원동길', '01088888888', 'patients/pat_prd_sj_oeseo01/reference.jpg', (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('pat_prd_sj_euncheok01', '서금자', DATE '1950-10-12', '501012', 'FEMALE', 'SANGJU', '경북 상주시 은척면 장암2리 수예길 16-132', '01099999999', 'patients/pat_prd_sj_euncheok01/reference.jpg', (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('pat_prd_sj_hwanam01', '윤복순', DATE '1949-05-30', '490530', 'FEMALE', 'SANGJU', '경북 상주시 화남면 동관2리 평온동관로 3791-385', '01012341234', 'patients/pat_prd_sj_hwanam01/reference.jpg', (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW()),
('pat_prd_sj_hwanam02', '오정자', DATE '1956-01-08', '560108', 'FEMALE', 'SANGJU', '경북 상주시 화남면 동관2리 비룡동관로 967-1162', '01023452345', 'patients/pat_prd_sj_hwanam02/reference.jpg', (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), NOW(), NOW(), NOW())
ON CONFLICT (public_id) DO UPDATE
SET name = EXCLUDED.name,
    birth_date = EXCLUDED.birth_date,
    birth_date6 = EXCLUDED.birth_date6,
    gender = EXCLUDED.gender,
    region_code = EXCLUDED.region_code,
    address = EXCLUDED.address,
    phone = EXCLUDED.phone,
    reference_image_path = EXCLUDED.reference_image_path,
    reference_image_uploaded_by_user_id = EXCLUDED.reference_image_uploaded_by_user_id,
    reference_image_updated_at = EXCLUDED.reference_image_updated_at,
    updated_at = NOW();

INSERT INTO patient_guardian_link (
    public_id, patient_id, guardian_user_id, approved_by_user_id, relation, status, requested_at, approved_at
) VALUES
('link_prd_001', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_gim_sudo01'), (SELECT user_id FROM "user" WHERE username = 'seed_prod_guardian_01'), (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), '배우자', 'APPROVED', NOW(), NOW()),
('link_prd_002', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_gim_hwang01'), (SELECT user_id FROM "user" WHERE username = 'seed_prod_guardian_01'), (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), '아들', 'APPROVED', NOW(), NOW()),
('link_prd_003', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_gim_jirye01'), (SELECT user_id FROM "user" WHERE username = 'seed_prod_guardian_01'), (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), '딸', 'APPROVED', NOW(), NOW()),
('link_prd_004', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_andong01'), (SELECT user_id FROM "user" WHERE username = 'seed_prod_guardian_01'), (SELECT user_id FROM "user" WHERE username = 'seed_prod_admin'), '자부', 'APPROVED', NOW(), NOW())
ON CONFLICT (patient_id, guardian_user_id) DO UPDATE
SET relation = EXCLUDED.relation,
    approved_by_user_id = EXCLUDED.approved_by_user_id,
    status = EXCLUDED.status,
    approved_at = EXCLUDED.approved_at;

DO $$
DECLARE
    v_slot RECORD;
    v_doctor_id BIGINT;
BEGIN
    FOR v_slot IN
        SELECT * FROM (VALUES
            ('slot_prd_gim_sudo01', 'doc_prd_001', 0, TIME '09:00'),
            ('slot_prd_gim_hwang01', 'doc_prd_002', 1, TIME '09:30'),
            ('slot_prd_gim_jirye01', 'doc_prd_003', 2, TIME '10:00'),
            ('slot_prd_andong01', 'doc_prd_005', 0, TIME '11:00'),
            ('slot_prd_yj_marak01', 'doc_prd_004', 0, TIME '13:00'),
            ('slot_prd_yj_nam01', 'doc_prd_002', 1, TIME '14:00'),
            ('slot_prd_yj_nam02', 'doc_prd_006', 3, TIME '15:00'),
            ('slot_prd_sj_oeseo01', 'doc_prd_003', 0, TIME '16:00'),
            ('slot_prd_sj_euncheok01', 'doc_prd_001', 4, TIME '10:30'),
            ('slot_prd_sj_hwanam01', 'doc_prd_005', 4, TIME '11:00'),
            ('slot_prd_sj_hwanam02', 'doc_prd_004', 5, TIME '11:30')
        ) AS seeded_slots(slot_public_id, doctor_public_id, day_offset, start_time)
    LOOP
        SELECT doctor_profile_id INTO v_doctor_id FROM doctor_profile WHERE public_id = v_slot.doctor_public_id;
        IF v_doctor_id IS NULL THEN
            RAISE EXCEPTION 'Doctor profile % not found after seed upsert', v_slot.doctor_public_id;
        END IF;
        IF EXISTS (
            SELECT 1 FROM schedule_slot
             WHERE doctor_id = v_doctor_id
               AND slot_date = CURRENT_DATE + v_slot.day_offset
               AND start_time = v_slot.start_time
               AND public_id <> v_slot.slot_public_id
        ) THEN
            RAISE EXCEPTION 'Doctor % already has another slot at % %', v_slot.doctor_public_id, CURRENT_DATE + v_slot.day_offset, v_slot.start_time;
        END IF;
    END LOOP;
END $$;

INSERT INTO schedule_slot (public_id, doctor_id, slot_date, start_time, end_time, is_booked, created_at) VALUES
('slot_prd_gim_sudo01', (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_001'), CURRENT_DATE, TIME '09:00', TIME '09:30', TRUE, NOW()),
('slot_prd_gim_hwang01', (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_002'), CURRENT_DATE + 1, TIME '09:30', TIME '10:00', TRUE, NOW()),
('slot_prd_gim_jirye01', (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_003'), CURRENT_DATE + 2, TIME '10:00', TIME '10:30', TRUE, NOW()),
('slot_prd_andong01', (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_005'), CURRENT_DATE, TIME '11:00', TIME '11:30', TRUE, NOW()),
('slot_prd_yj_marak01', (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_004'), CURRENT_DATE, TIME '13:00', TIME '13:30', TRUE, NOW()),
('slot_prd_yj_nam01', (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_002'), CURRENT_DATE + 1, TIME '14:00', TIME '14:30', TRUE, NOW()),
('slot_prd_yj_nam02', (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_006'), CURRENT_DATE + 3, TIME '15:00', TIME '15:30', TRUE, NOW()),
('slot_prd_sj_oeseo01', (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_003'), CURRENT_DATE, TIME '16:00', TIME '16:30', TRUE, NOW()),
('slot_prd_sj_euncheok01', (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_001'), CURRENT_DATE + 4, TIME '10:30', TIME '11:00', TRUE, NOW()),
('slot_prd_sj_hwanam01', (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_005'), CURRENT_DATE + 4, TIME '11:00', TIME '11:30', TRUE, NOW()),
('slot_prd_sj_hwanam02', (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_004'), CURRENT_DATE + 5, TIME '11:30', TIME '12:00', TRUE, NOW())
ON CONFLICT (public_id) DO UPDATE
SET doctor_id = EXCLUDED.doctor_id,
    slot_date = EXCLUDED.slot_date,
    start_time = EXCLUDED.start_time,
    end_time = EXCLUDED.end_time,
    is_booked = EXCLUDED.is_booked;

-- 조회용 미래 슬롯을 2026-04-13까지 유지한다.
WITH slot_templates AS (
    SELECT * FROM (VALUES
        ('doc_prd_001', TIME '09:00', TIME '09:30'),
        ('doc_prd_002', TIME '09:30', TIME '10:00'),
        ('doc_prd_003', TIME '10:00', TIME '10:30'),
        ('doc_prd_005', TIME '11:00', TIME '11:30'),
        ('doc_prd_004', TIME '13:00', TIME '13:30'),
        ('doc_prd_002', TIME '14:00', TIME '14:30'),
        ('doc_prd_006', TIME '15:00', TIME '15:30'),
        ('doc_prd_003', TIME '16:00', TIME '16:30')
    ) AS t(doctor_public_id, start_time, end_time)
),
future_days AS (
    SELECT d::date AS slot_date
      FROM generate_series(CURRENT_DATE + 6, DATE '2026-04-13', INTERVAL '1 day') AS d
)
INSERT INTO schedule_slot (
    public_id,
    doctor_id,
    slot_date,
    start_time,
    end_time,
    is_booked,
    created_at
)
SELECT
    'slot_prd_future_' || st.doctor_public_id || '_' || to_char(fd.slot_date, 'YYYYMMDD') || '_' || replace(st.start_time::text, ':', ''),
    dp.doctor_profile_id,
    fd.slot_date,
    st.start_time,
    st.end_time,
    FALSE,
    NOW()
  FROM slot_templates st
  JOIN doctor_profile dp
    ON dp.public_id = st.doctor_public_id
  CROSS JOIN future_days fd
 WHERE NOT EXISTS (
    SELECT 1
      FROM schedule_slot ss
     WHERE ss.doctor_id = dp.doctor_profile_id
       AND ss.slot_date = fd.slot_date
       AND ss.start_time = st.start_time
 );

INSERT INTO intake_session (
    public_id, patient_id, caller_number, channel, status, completion_reason, ended_at, last_activity_at,
    selected_department, selected_department_name, selection_reason, selection_confidence_level,
    selection_is_emergency, offered_slot_ids_json, selection_updated_at, created_at
) VALUES
('ints_prd_gim_sudo01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_gim_sudo01'), '01011111111', 'PHONE', 'COMPLETED', 'BOOKING_CREATED', NOW() - INTERVAL '70 minutes', NOW() - INTERVAL '65 minutes', 'INTERNAL_MEDICINE', '내과', '김천 수도리 권역 차량 단말 본인인증 검증용 예약', 'HIGH', FALSE, '["slot_prd_gim_sudo01"]', NOW() - INTERVAL '65 minutes', NOW()),
('ints_prd_gim_hwang01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_gim_hwang01'), '01022222222', 'PHONE', 'COMPLETED', 'BOOKING_CREATED', NOW(), NOW(), 'INTERNAL_MEDICINE', '내과', '김천 황점리 미래 예약 조회용 예약', 'HIGH', FALSE, '["slot_prd_gim_hwang01"]', NOW(), NOW()),
('ints_prd_gim_jirye01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_gim_jirye01'), '01033333333', 'PHONE', 'COMPLETED', 'BOOKING_CREATED', NOW(), NOW(), 'ORTHOPEDICS', '정형외과', '김천 지례 권역 미래 예약 조회용 예약', 'HIGH', FALSE, '["slot_prd_gim_jirye01"]', NOW(), NOW()),
('ints_prd_andong01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_andong01'), '01044444444', 'PHONE', 'COMPLETED', 'BOOKING_CREATED', NOW() - INTERVAL '60 minutes', NOW() - INTERVAL '55 minutes', 'NEUROLOGY', '신경과', '안동 임동면 본인인증 진행중 시나리오 검증용 예약', 'HIGH', FALSE, '["slot_prd_andong01"]', NOW() - INTERVAL '55 minutes', NOW()),
('ints_prd_yj_marak01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_yj_marak01'), '01055555555', 'PHONE', 'COMPLETED', 'BOOKING_CREATED', NOW() - INTERVAL '50 minutes', NOW() - INTERVAL '45 minutes', 'DERMATOLOGY', '피부과', '영주 단산면 active mission 검증용 예약', 'HIGH', FALSE, '["slot_prd_yj_marak01"]', NOW() - INTERVAL '45 minutes', NOW()),
('ints_prd_yj_nam01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_yj_nam01'), '01066666666', 'PHONE', 'COMPLETED', 'BOOKING_CREATED', NOW(), NOW(), 'INTERNAL_MEDICINE', '내과', '영주 남대리 미래 예약 조회용 예약', 'HIGH', FALSE, '["slot_prd_yj_nam01"]', NOW(), NOW()),
('ints_prd_yj_nam02', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_yj_nam02'), '01077777777', 'PHONE', 'COMPLETED', 'BOOKING_CREATED', NOW(), NOW(), 'OPHTHALMOLOGY', '안과', '영주 남대리 안과 미래 예약 조회용 예약', 'HIGH', FALSE, '["slot_prd_yj_nam02"]', NOW(), NOW()),
('ints_prd_sj_oeseo01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_sj_oeseo01'), '01088888888', 'PHONE', 'COMPLETED', 'BOOKING_CREATED', NOW() - INTERVAL '40 minutes', NOW() - INTERVAL '35 minutes', 'ORTHOPEDICS', '정형외과', '상주 외서면 화상진료 진행중 시나리오 검증용 예약', 'HIGH', FALSE, '["slot_prd_sj_oeseo01"]', NOW() - INTERVAL '35 minutes', NOW()),
('ints_prd_sj_euncheok01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_sj_euncheok01'), '01099999999', 'PHONE', 'COMPLETED', 'BOOKING_CREATED', NOW(), NOW(), 'INTERNAL_MEDICINE', '내과', '상주 은척면 미래 예약 조회용 예약', 'HIGH', FALSE, '["slot_prd_sj_euncheok01"]', NOW(), NOW()),
('ints_prd_sj_hwanam01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_sj_hwanam01'), '01012341234', 'PHONE', 'COMPLETED', 'BOOKING_CREATED', NOW(), NOW(), 'NEUROLOGY', '신경과', '상주 화남면 미래 예약 조회용 예약', 'HIGH', FALSE, '["slot_prd_sj_hwanam01"]', NOW(), NOW()),
('ints_prd_sj_hwanam02', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_sj_hwanam02'), '01023452345', 'PHONE', 'COMPLETED', 'BOOKING_CREATED', NOW(), NOW(), 'DERMATOLOGY', '피부과', '상주 화남면 피부과 미래 예약 조회용 예약', 'HIGH', FALSE, '["slot_prd_sj_hwanam02"]', NOW(), NOW())
ON CONFLICT (public_id) DO UPDATE
SET patient_id = EXCLUDED.patient_id,
    caller_number = EXCLUDED.caller_number,
    channel = EXCLUDED.channel,
    status = EXCLUDED.status,
    completion_reason = EXCLUDED.completion_reason,
    ended_at = EXCLUDED.ended_at,
    last_activity_at = EXCLUDED.last_activity_at,
    selected_department = EXCLUDED.selected_department,
    selected_department_name = EXCLUDED.selected_department_name,
    selection_reason = EXCLUDED.selection_reason,
    selection_confidence_level = EXCLUDED.selection_confidence_level,
    selection_is_emergency = EXCLUDED.selection_is_emergency,
    offered_slot_ids_json = EXCLUDED.offered_slot_ids_json,
    selection_updated_at = EXCLUDED.selection_updated_at;

INSERT INTO booking (
    public_id, patient_id, intake_session_id, slot_id, doctor_id, channel,
    appointment_date, start_time, end_time, status, cancel_reason, cancelled_at, created_at, updated_at
) VALUES
('bk_prd_gim_sudo01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_gim_sudo01'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_gim_sudo01'), (SELECT slot_id FROM schedule_slot WHERE public_id = 'slot_prd_gim_sudo01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_001'), 'PHONE', CURRENT_DATE, TIME '09:00', TIME '09:30', 'CONFIRMED', NULL, NULL, NOW(), NOW()),
('bk_prd_gim_hwang01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_gim_hwang01'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_gim_hwang01'), (SELECT slot_id FROM schedule_slot WHERE public_id = 'slot_prd_gim_hwang01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_002'), 'PHONE', CURRENT_DATE + 1, TIME '09:30', TIME '10:00', 'CONFIRMED', NULL, NULL, NOW(), NOW()),
('bk_prd_gim_jirye01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_gim_jirye01'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_gim_jirye01'), (SELECT slot_id FROM schedule_slot WHERE public_id = 'slot_prd_gim_jirye01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_003'), 'PHONE', CURRENT_DATE + 2, TIME '10:00', TIME '10:30', 'CONFIRMED', NULL, NULL, NOW(), NOW()),
('bk_prd_andong01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_andong01'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_andong01'), (SELECT slot_id FROM schedule_slot WHERE public_id = 'slot_prd_andong01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_005'), 'PHONE', CURRENT_DATE, TIME '11:00', TIME '11:30', 'CONFIRMED', NULL, NULL, NOW(), NOW()),
('bk_prd_yj_marak01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_yj_marak01'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_yj_marak01'), (SELECT slot_id FROM schedule_slot WHERE public_id = 'slot_prd_yj_marak01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_004'), 'PHONE', CURRENT_DATE, TIME '13:00', TIME '13:30', 'CONFIRMED', NULL, NULL, NOW(), NOW()),
('bk_prd_yj_nam01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_yj_nam01'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_yj_nam01'), (SELECT slot_id FROM schedule_slot WHERE public_id = 'slot_prd_yj_nam01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_002'), 'PHONE', CURRENT_DATE + 1, TIME '14:00', TIME '14:30', 'CONFIRMED', NULL, NULL, NOW(), NOW()),
('bk_prd_yj_nam02', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_yj_nam02'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_yj_nam02'), (SELECT slot_id FROM schedule_slot WHERE public_id = 'slot_prd_yj_nam02'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_006'), 'PHONE', CURRENT_DATE + 3, TIME '15:00', TIME '15:30', 'CONFIRMED', NULL, NULL, NOW(), NOW()),
('bk_prd_sj_oeseo01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_sj_oeseo01'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_sj_oeseo01'), (SELECT slot_id FROM schedule_slot WHERE public_id = 'slot_prd_sj_oeseo01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_003'), 'PHONE', CURRENT_DATE, TIME '16:00', TIME '16:30', 'CONFIRMED', NULL, NULL, NOW(), NOW()),
('bk_prd_sj_euncheok01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_sj_euncheok01'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_sj_euncheok01'), (SELECT slot_id FROM schedule_slot WHERE public_id = 'slot_prd_sj_euncheok01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_001'), 'PHONE', CURRENT_DATE + 4, TIME '10:30', TIME '11:00', 'CONFIRMED', NULL, NULL, NOW(), NOW()),
('bk_prd_sj_hwanam01', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_sj_hwanam01'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_sj_hwanam01'), (SELECT slot_id FROM schedule_slot WHERE public_id = 'slot_prd_sj_hwanam01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_005'), 'PHONE', CURRENT_DATE + 4, TIME '11:00', TIME '11:30', 'CONFIRMED', NULL, NULL, NOW(), NOW()),
('bk_prd_sj_hwanam02', (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_sj_hwanam02'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_sj_hwanam02'), (SELECT slot_id FROM schedule_slot WHERE public_id = 'slot_prd_sj_hwanam02'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_004'), 'PHONE', CURRENT_DATE + 5, TIME '11:30', TIME '12:00', 'CONFIRMED', NULL, NULL, NOW(), NOW())
ON CONFLICT (public_id) DO UPDATE
SET patient_id = EXCLUDED.patient_id,
    intake_session_id = EXCLUDED.intake_session_id,
    slot_id = EXCLUDED.slot_id,
    doctor_id = EXCLUDED.doctor_id,
    channel = EXCLUDED.channel,
    appointment_date = EXCLUDED.appointment_date,
    start_time = EXCLUDED.start_time,
    end_time = EXCLUDED.end_time,
    status = EXCLUDED.status,
    cancel_reason = EXCLUDED.cancel_reason,
    cancelled_at = EXCLUDED.cancelled_at,
    updated_at = NOW();

INSERT INTO care_case (
    public_id, booking_id, patient_id, doctor_id, intake_session_id, status, created_at, updated_at
) VALUES
('case_prd_gim_sudo01', (SELECT booking_id FROM booking WHERE public_id = 'bk_prd_gim_sudo01'), (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_gim_sudo01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_001'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_gim_sudo01'), 'PREPARING', NOW(), NOW()),
('case_prd_gim_hwang01', (SELECT booking_id FROM booking WHERE public_id = 'bk_prd_gim_hwang01'), (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_gim_hwang01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_002'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_gim_hwang01'), 'CREATED', NOW(), NOW()),
('case_prd_gim_jirye01', (SELECT booking_id FROM booking WHERE public_id = 'bk_prd_gim_jirye01'), (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_gim_jirye01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_003'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_gim_jirye01'), 'CREATED', NOW(), NOW()),
('case_prd_andong01', (SELECT booking_id FROM booking WHERE public_id = 'bk_prd_andong01'), (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_andong01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_005'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_andong01'), 'IN_PROGRESS', NOW(), NOW()),
('case_prd_yj_marak01', (SELECT booking_id FROM booking WHERE public_id = 'bk_prd_yj_marak01'), (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_yj_marak01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_004'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_yj_marak01'), 'PREPARING', NOW(), NOW()),
('case_prd_yj_nam01', (SELECT booking_id FROM booking WHERE public_id = 'bk_prd_yj_nam01'), (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_yj_nam01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_002'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_yj_nam01'), 'CREATED', NOW(), NOW()),
('case_prd_yj_nam02', (SELECT booking_id FROM booking WHERE public_id = 'bk_prd_yj_nam02'), (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_yj_nam02'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_006'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_yj_nam02'), 'CREATED', NOW(), NOW()),
('case_prd_sj_oeseo01', (SELECT booking_id FROM booking WHERE public_id = 'bk_prd_sj_oeseo01'), (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_sj_oeseo01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_003'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_sj_oeseo01'), 'IN_PROGRESS', NOW(), NOW()),
('case_prd_sj_euncheok01', (SELECT booking_id FROM booking WHERE public_id = 'bk_prd_sj_euncheok01'), (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_sj_euncheok01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_001'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_sj_euncheok01'), 'CREATED', NOW(), NOW()),
('case_prd_sj_hwanam01', (SELECT booking_id FROM booking WHERE public_id = 'bk_prd_sj_hwanam01'), (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_sj_hwanam01'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_005'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_sj_hwanam01'), 'CREATED', NOW(), NOW()),
('case_prd_sj_hwanam02', (SELECT booking_id FROM booking WHERE public_id = 'bk_prd_sj_hwanam02'), (SELECT patient_id FROM patient WHERE public_id = 'pat_prd_sj_hwanam02'), (SELECT doctor_profile_id FROM doctor_profile WHERE public_id = 'doc_prd_004'), (SELECT intake_session_id FROM intake_session WHERE public_id = 'ints_prd_sj_hwanam02'), 'CREATED', NOW(), NOW())
ON CONFLICT (booking_id) DO UPDATE
SET patient_id = EXCLUDED.patient_id,
    doctor_id = EXCLUDED.doctor_id,
    intake_session_id = EXCLUDED.intake_session_id,
    status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO mission (
    public_id, case_id, vehicle_id, destination, dispatched_at, estimated_arrival_time, phase, previous_phase,
    latitude, longitude, completed_at, last_telemetry_source_event_id, last_telemetry_seq_no, last_telemetry_at,
    created_at, updated_at
) VALUES
('ms_prd_gim_sudo01', (SELECT case_id FROM care_case WHERE public_id = 'case_prd_gim_sudo01'), (SELECT public_id FROM vehicle WHERE code = 'GIMCHEON-01'), (SELECT address FROM patient WHERE public_id = 'pat_prd_gim_sudo01'), NOW() - INTERVAL '50 minutes', NOW() - INTERVAL '20 minutes', 'ARRIVED', 'EN_ROUTE', 36.1115000, 128.0708000, NULL, NULL, NULL, NULL, NOW(), NOW()),
('ms_prd_andong01', (SELECT case_id FROM care_case WHERE public_id = 'case_prd_andong01'), (SELECT public_id FROM vehicle WHERE code = 'ANDONG-01'), (SELECT address FROM patient WHERE public_id = 'pat_prd_andong01'), NOW() - INTERVAL '60 minutes', NOW() - INTERVAL '25 minutes', 'VERIFYING', 'ARRIVED', 36.6987000, 128.8223000, NULL, NULL, NULL, NULL, NOW(), NOW()),
('ms_prd_yj_marak01', (SELECT case_id FROM care_case WHERE public_id = 'case_prd_yj_marak01'), (SELECT public_id FROM vehicle WHERE code = 'YEONGJU-01'), (SELECT address FROM patient WHERE public_id = 'pat_prd_yj_marak01'), NOW() - INTERVAL '45 minutes', NOW() - INTERVAL '10 minutes', 'ARRIVED', 'EN_ROUTE', 36.8049000, 128.6240000, NULL, NULL, NULL, NULL, NOW(), NOW()),
('ms_prd_sj_oeseo01', (SELECT case_id FROM care_case WHERE public_id = 'case_prd_sj_oeseo01'), (SELECT public_id FROM vehicle WHERE code = 'SANGJU-01'), (SELECT address FROM patient WHERE public_id = 'pat_prd_sj_oeseo01'), NOW() - INTERVAL '55 minutes', NOW() - INTERVAL '15 minutes', 'CONSULTING', 'VERIFYING', 36.4109000, 128.1591000, NULL, NULL, NULL, NULL, NOW(), NOW())
ON CONFLICT (case_id) DO UPDATE
SET vehicle_id = EXCLUDED.vehicle_id,
    destination = EXCLUDED.destination,
    dispatched_at = EXCLUDED.dispatched_at,
    estimated_arrival_time = EXCLUDED.estimated_arrival_time,
    phase = EXCLUDED.phase,
    previous_phase = EXCLUDED.previous_phase,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    completed_at = EXCLUDED.completed_at,
    last_telemetry_source_event_id = EXCLUDED.last_telemetry_source_event_id,
    last_telemetry_seq_no = EXCLUDED.last_telemetry_seq_no,
    last_telemetry_at = EXCLUDED.last_telemetry_at,
    updated_at = NOW();

INSERT INTO consultation_session (
    public_id, case_id, status, room_id, livekit_url, doctor_connection_state, patient_connection_state,
    doctor_joined_at, patient_joined_at, started_at, ended_at, duration_minutes, created_at
) VALUES
('ses_prd_gim_sudo01', (SELECT case_id FROM care_case WHERE public_id = 'case_prd_gim_sudo01'), 'READY', 'prod-room-gim-01', COALESCE(NULLIF((SELECT livekit_url FROM consultation_session WHERE livekit_url IS NOT NULL AND livekit_url <> '' ORDER BY session_id DESC LIMIT 1), ''), '__SET_LIVEKIT_URL__'), 'DISCONNECTED', 'DISCONNECTED', NULL, NULL, NULL, NULL, NULL, NOW()),
('ses_prd_andong01', (SELECT case_id FROM care_case WHERE public_id = 'case_prd_andong01'), 'READY', 'prod-room-andong-01', COALESCE(NULLIF((SELECT livekit_url FROM consultation_session WHERE livekit_url IS NOT NULL AND livekit_url <> '' ORDER BY session_id DESC LIMIT 1), ''), '__SET_LIVEKIT_URL__'), 'DISCONNECTED', 'DISCONNECTED', NULL, NULL, NULL, NULL, NULL, NOW()),
('ses_prd_yj_marak01', (SELECT case_id FROM care_case WHERE public_id = 'case_prd_yj_marak01'), 'READY', 'prod-room-yeongju-01', COALESCE(NULLIF((SELECT livekit_url FROM consultation_session WHERE livekit_url IS NOT NULL AND livekit_url <> '' ORDER BY session_id DESC LIMIT 1), ''), '__SET_LIVEKIT_URL__'), 'DISCONNECTED', 'DISCONNECTED', NULL, NULL, NULL, NULL, NULL, NOW()),
('ses_prd_sj_oeseo01', (SELECT case_id FROM care_case WHERE public_id = 'case_prd_sj_oeseo01'), 'IN_PROGRESS', 'prod-room-sangju-01', COALESCE(NULLIF((SELECT livekit_url FROM consultation_session WHERE livekit_url IS NOT NULL AND livekit_url <> '' ORDER BY session_id DESC LIMIT 1), ''), '__SET_LIVEKIT_URL__'), 'CONNECTED', 'CONNECTED', NOW() - INTERVAL '20 minutes', NOW() - INTERVAL '19 minutes', NOW() - INTERVAL '18 minutes', NULL, NULL, NOW())
ON CONFLICT (case_id) DO UPDATE
SET status = EXCLUDED.status,
    room_id = EXCLUDED.room_id,
    livekit_url = EXCLUDED.livekit_url,
    doctor_connection_state = EXCLUDED.doctor_connection_state,
    patient_connection_state = EXCLUDED.patient_connection_state,
    doctor_joined_at = EXCLUDED.doctor_joined_at,
    patient_joined_at = EXCLUDED.patient_joined_at,
    started_at = EXCLUDED.started_at,
    ended_at = EXCLUDED.ended_at,
    duration_minutes = EXCLUDED.duration_minutes;

COMMIT;
