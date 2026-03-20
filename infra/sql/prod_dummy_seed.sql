-- prod dummy seed fallback
-- - LocalDummyDataSeeder.java의 핵심 구조를 기준으로 작성한 수동 SQL
-- - 모든 더미 사용자 기본 비밀번호: Passw0rd!
-- - consultation_session.livekit_url은 기존 데이터의 값을 재사용하고,
--   없으면 __LIVEKIT_URL__ 플레이스홀더를 넣습니다. 필요하면 실행 전에 치환하세요.
-- - 재실행 가능하도록 주요 natural key(username, phone, unique fk)를 기준으로 upsert 합니다.
-- TODO: 장기적으로는 prod에서도 APP_SEED_ENABLED를 one-shot으로 켜서 Java seeder를 재사용하는 편이 안전하다.

BEGIN;

INSERT INTO "user" (
    public_id,
    username,
    password_hash,
    name,
    role,
    is_active,
    approval_status,
    approval_requested_at,
    approved_by_user_id,
    approved_at,
    created_at,
    updated_at
)
VALUES (
    'usr_seed_admin',
    'seed_admin',
    '$2a$10$IH3MU2MFjspMAzlawklnHe77FTWUAbUegt7dJV03R28go2QRGNBb.',
    '로컬 관리자',
    'ADMIN',
    TRUE,
    'APPROVED',
    NOW(),
    NULL,
    NOW(),
    NOW(),
    NOW()
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

CREATE TEMP TABLE seed_doctor (
    seed_no INT PRIMARY KEY,
    username TEXT NOT NULL,
    name TEXT NOT NULL,
    department TEXT NOT NULL,
    department_name TEXT NOT NULL,
    approval_status TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_doctor (seed_no, username, name, department, department_name, approval_status)
VALUES
    (1, 'seed_doc_im_01', '김도현', 'INTERNAL_MEDICINE', '내과', 'APPROVED'),
    (2, 'seed_doc_im_02', '박지연', 'INTERNAL_MEDICINE', '내과', 'APPROVED'),
    (3, 'seed_doc_im_03', '이성훈', 'INTERNAL_MEDICINE', '내과', 'APPROVED'),
    (4, 'seed_doc_im_04', '조은서', 'INTERNAL_MEDICINE', '내과', 'APPROVED'),
    (5, 'seed_doc_ortho_01', '최준혁', 'ORTHOPEDICS', '정형외과', 'APPROVED'),
    (6, 'seed_doc_ortho_02', '강민석', 'ORTHOPEDICS', '정형외과', 'APPROVED'),
    (7, 'seed_doc_ortho_03', '윤서진', 'ORTHOPEDICS', '정형외과', 'APPROVED'),
    (8, 'seed_doc_derm_01', '임수빈', 'DERMATOLOGY', '피부과', 'APPROVED'),
    (9, 'seed_doc_derm_02', '한지후', 'DERMATOLOGY', '피부과', 'APPROVED'),
    (10, 'seed_doc_neuro_01', '정유진', 'NEUROLOGY', '신경과', 'APPROVED'),
    (11, 'seed_doc_neuro_02', '오태경', 'NEUROLOGY', '신경과', 'APPROVED'),
    (12, 'seed_doc_eye_01', '장소라', 'OPHTHALMOLOGY', '안과', 'APPROVED'),
    (13, 'seed_doc_family_pending', '백현우', 'FAMILY_MEDICINE', '가정의학과', 'PENDING'),
    (14, 'seed_doc_im_pending', '서지훈', 'INTERNAL_MEDICINE', '내과', 'PENDING');

INSERT INTO "user" (
    public_id,
    username,
    password_hash,
    name,
    role,
    is_active,
    approval_status,
    approval_requested_at,
    approved_by_user_id,
    approved_at,
    created_at,
    updated_at
)
SELECT
    'usr_doc_' || LPAD(seed_no::TEXT, 2, '0'),
    username,
    '$2a$10$IH3MU2MFjspMAzlawklnHe77FTWUAbUegt7dJV03R28go2QRGNBb.',
    name,
    'DOCTOR',
    approval_status = 'APPROVED',
    approval_status,
    NOW(),
    CASE WHEN approval_status = 'APPROVED' THEN (SELECT user_id FROM "user" WHERE username = 'seed_admin') END,
    CASE WHEN approval_status = 'APPROVED' THEN NOW() END,
    NOW(),
    NOW()
FROM seed_doctor
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    name = EXCLUDED.name,
    role = EXCLUDED.role,
    is_active = EXCLUDED.is_active,
    approval_status = EXCLUDED.approval_status,
    approved_by_user_id = EXCLUDED.approved_by_user_id,
    approved_at = EXCLUDED.approved_at,
    updated_at = NOW();

INSERT INTO doctor_profile (
    public_id,
    user_id,
    department,
    department_name,
    created_at
)
SELECT
    'doc_seed_' || LPAD(d.seed_no::TEXT, 2, '0'),
    u.user_id,
    d.department,
    d.department_name,
    NOW()
FROM seed_doctor d
JOIN "user" u
  ON u.username = d.username
ON CONFLICT (user_id) DO UPDATE
SET department = EXCLUDED.department,
    department_name = EXCLUDED.department_name;

CREATE TEMP TABLE seed_guardian ON COMMIT DROP AS
WITH constants AS (
    SELECT
        ARRAY['김','이','박','최','정','강','조','윤','장','임','한','오']::TEXT[] AS surnames,
        ARRAY['민지','지훈','수진','현우','은정','준호','혜진','성민','도윤','예진','수현','태현','현정','유진','소연','재훈']::TEXT[] AS given_names
)
SELECT
    gs AS seed_no,
    'seed_guardian_' || LPAD(gs::TEXT, 2, '0') AS username,
    constants.surnames[((gs + 2) % array_length(constants.surnames, 1)) + 1]
        || constants.given_names[((gs - 1) % array_length(constants.given_names, 1)) + 1] AS name,
    CASE
        WHEN gs <= 20 THEN 'APPROVED'
        WHEN gs <= 26 THEN 'PENDING'
        ELSE 'REJECTED'
    END AS approval_status
FROM generate_series(1, 28) AS gs
CROSS JOIN constants;

INSERT INTO "user" (
    public_id,
    username,
    password_hash,
    name,
    role,
    is_active,
    approval_status,
    approval_requested_at,
    approved_by_user_id,
    approved_at,
    created_at,
    updated_at
)
SELECT
    'usr_grd_' || LPAD(seed_no::TEXT, 2, '0'),
    username,
    '$2a$10$IH3MU2MFjspMAzlawklnHe77FTWUAbUegt7dJV03R28go2QRGNBb.',
    name,
    'GUARDIAN',
    approval_status = 'APPROVED',
    approval_status,
    NOW(),
    CASE WHEN approval_status <> 'PENDING' THEN (SELECT user_id FROM "user" WHERE username = 'seed_admin') END,
    CASE WHEN approval_status <> 'PENDING' THEN NOW() END,
    NOW(),
    NOW()
FROM seed_guardian
ON CONFLICT (username) DO UPDATE
SET password_hash = EXCLUDED.password_hash,
    name = EXCLUDED.name,
    role = EXCLUDED.role,
    is_active = EXCLUDED.is_active,
    approval_status = EXCLUDED.approval_status,
    approved_by_user_id = EXCLUDED.approved_by_user_id,
    approved_at = EXCLUDED.approved_at,
    updated_at = NOW();

CREATE TEMP TABLE seed_patient ON COMMIT DROP AS
WITH constants AS (
    SELECT
        ARRAY['김','이','박','최','정','강','조','윤','장','임','한','오']::TEXT[] AS surnames,
        ARRAY['영희','순자','말순','춘자','정숙','옥자','미숙','연자','복순','경자','명자','금순']::TEXT[] AS female_names,
        ARRAY['영수','철수','성호','동수','만수','기동','정호','태수','병철','정남','종수','상호']::TEXT[] AS male_names,
        ARRAY['장전1길','장전2길','장전3길','황점길','금곡길','황점1길','금곡1길','장전마을길']::TEXT[] AS road_names
)
SELECT
    gs AS seed_no,
    'pat_seed_' || LPAD(gs::TEXT, 3, '0') AS public_id,
    constants.surnames[(((gs - 1) % array_length(constants.surnames, 1)) + 1)]
        || CASE
            WHEN MOD(gs - 1, 2) = 0 THEN constants.female_names[((((gs - 1) / array_length(constants.surnames, 1)) % array_length(constants.female_names, 1)) + 1)]
            ELSE constants.male_names[((((gs - 1) / array_length(constants.surnames, 1)) % array_length(constants.male_names, 1)) + 1)]
        END AS name,
    MAKE_DATE(
        CASE
            WHEN gs <= 16 THEN 1938 + MOD(gs - 1, 9)
            WHEN gs <= 44 THEN 1947 + MOD(gs - 1, 10)
            WHEN gs <= 68 THEN 1957 + MOD(gs - 1, 10)
            WHEN gs <= 76 THEN 1967 + MOD(gs - 1, 10)
            ELSE 1978 + MOD(gs - 1, 8)
        END,
        MOD(gs - 1, 12) + 1,
        MOD((gs - 1) * 3, 28) + 1
    ) AS birth_date,
    'GIMCHEON_JEUNGSAN'::TEXT AS region_code,
    '경북 김천시 증산면 '
        || constants.road_names[(((gs - 1) % array_length(constants.road_names, 1)) + 1)]
        || ' '
        || (19 + ((gs - 1) * 3))::TEXT
        || CASE
            WHEN MOD(gs - 1, 4) = 0 THEN '-' || (MOD(gs - 1, 7) + 1)::TEXT
            ELSE ''
        END AS address,
    '010' || LPAD((51000000 + gs - 1)::TEXT, 8, '0') AS phone,
    'seed/patients/patient-' || LPAD(gs::TEXT, 2, '0') || '-reference.jpg' AS reference_image_path
FROM generate_series(1, 80) AS gs
CROSS JOIN constants;

INSERT INTO patient (
    public_id,
    name,
    birth_date,
    birth_date6,
    region_code,
    address,
    phone,
    reference_image_path,
    reference_image_uploaded_by_user_id,
    reference_image_updated_at,
    created_at,
    updated_at
)
SELECT
    public_id,
    name,
    birth_date,
    TO_CHAR(birth_date, 'YYMMDD'),
    region_code,
    address,
    phone,
    reference_image_path,
    (SELECT user_id FROM "user" WHERE username = 'seed_admin'),
    NOW(),
    NOW(),
    NOW()
FROM seed_patient
ON CONFLICT (phone) DO UPDATE
SET name = EXCLUDED.name,
    birth_date = EXCLUDED.birth_date,
    birth_date6 = EXCLUDED.birth_date6,
    region_code = EXCLUDED.region_code,
    address = EXCLUDED.address,
    reference_image_path = EXCLUDED.reference_image_path,
    reference_image_uploaded_by_user_id = EXCLUDED.reference_image_uploaded_by_user_id,
    reference_image_updated_at = EXCLUDED.reference_image_updated_at,
    updated_at = NOW();

CREATE TEMP TABLE seed_guardian_link ON COMMIT DROP AS
WITH relations AS (
    SELECT ARRAY['배우자','아들','딸','며느리','사위','손자','손녀','조카']::TEXT[] AS names
)
SELECT
    idx + 1 AS seed_no,
    idx + 1 AS patient_seed_no,
    (idx % 20) + 1 AS guardian_seed_no,
    relations.names[(idx % array_length(relations.names, 1)) + 1] AS relation,
    'APPROVED'::TEXT AS status
FROM generate_series(0, 23) AS idx
CROSS JOIN relations
UNION ALL
SELECT
    idx + 25 AS seed_no,
    idx + 25 AS patient_seed_no,
    21 + (idx % 6) AS guardian_seed_no,
    relations.names[((idx + 2) % array_length(relations.names, 1)) + 1] AS relation,
    'PENDING'::TEXT AS status
FROM generate_series(0, 7) AS idx
CROSS JOIN relations
UNION ALL
SELECT
    idx + 33 AS seed_no,
    idx + 33 AS patient_seed_no,
    27 + (idx % 2) AS guardian_seed_no,
    relations.names[((idx + 4) % array_length(relations.names, 1)) + 1] AS relation,
    'REJECTED'::TEXT AS status
FROM generate_series(0, 3) AS idx
CROSS JOIN relations;

INSERT INTO patient_guardian_link (
    public_id,
    patient_id,
    guardian_user_id,
    approved_by_user_id,
    relation,
    status,
    requested_at,
    approved_at
)
SELECT
    'link_seed_' || LPAD(l.seed_no::TEXT, 3, '0'),
    p.patient_id,
    u.user_id,
    CASE WHEN l.status <> 'PENDING' THEN (SELECT user_id FROM "user" WHERE username = 'seed_admin') END,
    l.relation,
    l.status,
    NOW(),
    CASE WHEN l.status <> 'PENDING' THEN NOW() END
FROM seed_guardian_link l
JOIN seed_patient sp
  ON sp.seed_no = l.patient_seed_no
JOIN patient p
  ON p.phone = sp.phone
JOIN seed_guardian sg
  ON sg.seed_no = l.guardian_seed_no
JOIN "user" u
  ON u.username = sg.username
ON CONFLICT (patient_id, guardian_user_id) DO UPDATE
SET relation = EXCLUDED.relation,
    approved_by_user_id = EXCLUDED.approved_by_user_id,
    status = EXCLUDED.status,
    approved_at = EXCLUDED.approved_at;

CREATE TEMP TABLE seed_journey (
    seed_no INT PRIMARY KEY,
    patient_seed_no INT NOT NULL,
    doctor_username TEXT NOT NULL,
    date_offset INT NOT NULL,
    start_time TIME NOT NULL,
    booking_status TEXT NOT NULL,
    case_status TEXT,
    mission_phase TEXT,
    session_status TEXT,
    create_summary BOOLEAN NOT NULL,
    cancel_reason TEXT
) ON COMMIT DROP;

INSERT INTO seed_journey (
    seed_no, patient_seed_no, doctor_username, date_offset, start_time,
    booking_status, case_status, mission_phase, session_status, create_summary, cancel_reason
)
VALUES
    (1,  1, 'seed_doc_im_01',    -7, '09:00', 'COMPLETED', 'COMPLETED',  'COMPLETED',  'COMPLETED',  TRUE,  NULL),
    (2,  2, 'seed_doc_im_02',    -6, '09:30', 'COMPLETED', 'COMPLETED',  'COMPLETED',  'COMPLETED',  TRUE,  NULL),
    (3,  3, 'seed_doc_ortho_01', -5, '10:00', 'COMPLETED', 'COMPLETED',  'COMPLETED',  'COMPLETED',  TRUE,  NULL),
    (4,  4, 'seed_doc_derm_01',  -4, '10:30', 'COMPLETED', 'COMPLETED',  'COMPLETED',  'COMPLETED',  TRUE,  NULL),
    (5,  5, 'seed_doc_neuro_01', -3, '11:00', 'COMPLETED', 'COMPLETED',  'COMPLETED',  'COMPLETED',  TRUE,  NULL),
    (6,  6, 'seed_doc_eye_01',   -2, '11:30', 'COMPLETED', 'COMPLETED',  'COMPLETED',  'COMPLETED',  TRUE,  NULL),
    (7,  7, 'seed_doc_im_03',    -1, '14:00', 'COMPLETED', 'COMPLETED',  'COMPLETED',  'COMPLETED',  TRUE,  NULL),
    (8,  8, 'seed_doc_ortho_02', -1, '14:30', 'COMPLETED', 'COMPLETED',  'COMPLETED',  'COMPLETED',  TRUE,  NULL),
    (9,  9, 'seed_doc_ortho_03',  0, '15:00', 'CONFIRMED', 'PREPARING',  'EN_ROUTE',   NULL,         FALSE, NULL),
    (10, 10,'seed_doc_neuro_01',  0, '15:30', 'CONFIRMED', 'PREPARING',  'ARRIVED',    'READY',      FALSE, NULL),
    (11, 11,'seed_doc_derm_02',   0, '16:00', 'CONFIRMED', 'IN_PROGRESS','CONSULTING', 'IN_PROGRESS',FALSE, NULL),
    (12, 12,'seed_doc_im_04',     1, '09:00', 'CONFIRMED', 'PREPARING',  'DISPATCHED', NULL,         FALSE, NULL),
    (13, 13,'seed_doc_eye_01',    1, '09:30', 'CONFIRMED', 'PREPARING',  'ARRIVED',    'READY',      FALSE, NULL),
    (14, 14,'seed_doc_ortho_01',  1, '10:00', 'CONFIRMED', 'IN_PROGRESS','CONSULTING', 'IN_PROGRESS',FALSE, NULL),
    (15, 15,'seed_doc_im_03',     3, '09:00', 'CONFIRMED', NULL,         NULL,         NULL,         FALSE, NULL),
    (16, 16,'seed_doc_im_04',     4, '09:30', 'CONFIRMED', NULL,         NULL,         NULL,         FALSE, NULL),
    (17, 17,'seed_doc_ortho_02',  5, '10:00', 'CONFIRMED', NULL,         NULL,         NULL,         FALSE, NULL),
    (18, 18,'seed_doc_derm_01',   6, '10:30', 'CONFIRMED', NULL,         NULL,         NULL,         FALSE, NULL),
    (19, 19,'seed_doc_neuro_02',  7, '11:00', 'CONFIRMED', NULL,         NULL,         NULL,         FALSE, NULL),
    (20, 20,'seed_doc_eye_01',    8, '11:30', 'CONFIRMED', NULL,         NULL,         NULL,         FALSE, NULL),
    (21, 21,'seed_doc_im_01',     9, '14:00', 'CONFIRMED', NULL,         NULL,         NULL,         FALSE, NULL),
    (22, 22,'seed_doc_ortho_03', 10, '14:30', 'CONFIRMED', NULL,         NULL,         NULL,         FALSE, NULL),
    (23, 23,'seed_doc_im_02',     2, '15:00', 'CANCELLED', NULL,         NULL,         NULL,         FALSE, '환자 사정으로 일정 변경'),
    (24, 24,'seed_doc_derm_02',   3, '15:30', 'CANCELLED', NULL,         NULL,         NULL,         FALSE, '보호자 요청으로 예약 취소'),
    (25, 25,'seed_doc_neuro_02',  4, '16:00', 'CANCELLED', NULL,         NULL,         NULL,         FALSE, '현장 상황으로 재예약 예정'),
    (26, 26,'seed_doc_eye_01',    5, '16:30', 'CANCELLED', NULL,         NULL,         NULL,         FALSE, '증상 호전으로 취소');

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
    'slot_seed_' || LPAD(j.seed_no::TEXT, 3, '0'),
    dp.doctor_profile_id,
    CURRENT_DATE + j.date_offset,
    j.start_time,
    (j.start_time + INTERVAL '30 minutes')::TIME,
    j.booking_status <> 'CANCELLED',
    NOW()
FROM seed_journey j
JOIN "user" u
  ON u.username = j.doctor_username
JOIN doctor_profile dp
  ON dp.user_id = u.user_id
ON CONFLICT (public_id) DO UPDATE
SET doctor_id = EXCLUDED.doctor_id,
    slot_date = EXCLUDED.slot_date,
    start_time = EXCLUDED.start_time,
    end_time = EXCLUDED.end_time,
    is_booked = EXCLUDED.is_booked;

INSERT INTO intake_session (
    public_id,
    patient_id,
    caller_number,
    channel,
    status,
    completion_reason,
    ended_at,
    last_activity_at,
    selected_department,
    selected_department_name,
    selection_reason,
    selection_confidence_level,
    selection_is_emergency,
    offered_slot_ids_json,
    selection_updated_at,
    created_at
)
SELECT
    'ints_seed_' || LPAD(j.seed_no::TEXT, 3, '0'),
    p.patient_id,
    p.phone,
    'PHONE',
    'COMPLETED',
    CASE WHEN j.booking_status = 'CANCELLED' THEN 'EXISTING_BOOKING_CHECKED' ELSE 'BOOKING_CREATED' END,
    (CURRENT_DATE + j.date_offset + j.start_time) - INTERVAL '55 minutes',
    (CURRENT_DATE + j.date_offset + j.start_time) - INTERVAL '50 minutes',
    dp.department,
    dp.department_name,
    CASE dp.department
        WHEN 'INTERNAL_MEDICINE' THEN '어지럼과 혈압 변동으로 내과 상담 요청'
        WHEN 'ORTHOPEDICS' THEN '무릎 통증과 보행 불편으로 정형외과 상담 요청'
        WHEN 'DERMATOLOGY' THEN '가려움과 발진이 반복되어 피부과 상담 요청'
        WHEN 'NEUROLOGY' THEN '두통과 어지럼이 반복되어 신경과 상담 요청'
        WHEN 'OPHTHALMOLOGY' THEN '시야 흐림과 눈 충혈로 안과 상담 요청'
        ELSE '증상 확인을 위해 진료 예약을 진행함'
    END,
    'HIGH',
    FALSE,
    '["slot_seed_' || LPAD(j.seed_no::TEXT, 3, '0') || '"]',
    (CURRENT_DATE + j.date_offset + j.start_time) - INTERVAL '50 minutes',
    NOW()
FROM seed_journey j
JOIN seed_patient sp
  ON sp.seed_no = j.patient_seed_no
JOIN patient p
  ON p.phone = sp.phone
JOIN "user" u
  ON u.username = j.doctor_username
JOIN doctor_profile dp
  ON dp.user_id = u.user_id
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
    public_id,
    patient_id,
    intake_session_id,
    slot_id,
    doctor_id,
    channel,
    appointment_date,
    start_time,
    end_time,
    status,
    cancel_reason,
    cancelled_at,
    created_at,
    updated_at
)
SELECT
    'bk_seed_' || LPAD(j.seed_no::TEXT, 3, '0'),
    p.patient_id,
    i.intake_session_id,
    s.slot_id,
    s.doctor_id,
    'PHONE',
    s.slot_date,
    s.start_time,
    s.end_time,
    j.booking_status,
    j.cancel_reason,
    CASE
        WHEN j.booking_status = 'CANCELLED' THEN (s.slot_date + s.start_time) - INTERVAL '35 minutes'
        ELSE NULL
    END,
    NOW(),
    NOW()
FROM seed_journey j
JOIN seed_patient sp
  ON sp.seed_no = j.patient_seed_no
JOIN patient p
  ON p.phone = sp.phone
JOIN schedule_slot s
  ON s.public_id = 'slot_seed_' || LPAD(j.seed_no::TEXT, 3, '0')
JOIN intake_session i
  ON i.public_id = 'ints_seed_' || LPAD(j.seed_no::TEXT, 3, '0')
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
    public_id,
    booking_id,
    patient_id,
    doctor_id,
    intake_session_id,
    status,
    created_at,
    updated_at
)
SELECT
    'case_seed_' || LPAD(j.seed_no::TEXT, 3, '0'),
    b.booking_id,
    b.patient_id,
    b.doctor_id,
    b.intake_session_id,
    j.case_status,
    NOW(),
    NOW()
FROM seed_journey j
JOIN booking b
  ON b.public_id = 'bk_seed_' || LPAD(j.seed_no::TEXT, 3, '0')
WHERE j.case_status IS NOT NULL
ON CONFLICT (booking_id) DO UPDATE
SET patient_id = EXCLUDED.patient_id,
    doctor_id = EXCLUDED.doctor_id,
    intake_session_id = EXCLUDED.intake_session_id,
    status = EXCLUDED.status,
    updated_at = NOW();

INSERT INTO mission (
    public_id,
    case_id,
    vehicle_id,
    destination,
    dispatched_at,
    estimated_arrival_time,
    phase,
    previous_phase,
    latitude,
    longitude,
    completed_at,
    created_at,
    updated_at
)
SELECT
    'ms_seed_' || LPAD(j.seed_no::TEXT, 3, '0'),
    c.case_id,
    'VEH-' || LPAD(j.seed_no::TEXT, 3, '0'),
    p.address,
    (b.appointment_date + b.start_time) - INTERVAL '40 minutes',
    (b.appointment_date + b.start_time) - INTERVAL '10 minutes',
    j.mission_phase,
    CASE j.mission_phase
        WHEN 'EN_ROUTE' THEN 'DISPATCHED'
        WHEN 'ARRIVED' THEN 'EN_ROUTE'
        WHEN 'CONSULTING' THEN 'VERIFYING'
        WHEN 'COMPLETED' THEN 'RETURNING'
        ELSE NULL
    END,
    ROUND((36.1080000 + (j.seed_no * 0.0007))::NUMERIC, 7),
    ROUND((128.0500000 + (j.seed_no * 0.0005))::NUMERIC, 7),
    CASE
        WHEN j.mission_phase = 'COMPLETED' THEN (b.appointment_date + b.start_time) + INTERVAL '55 minutes'
        ELSE NULL
    END,
    NOW(),
    NOW()
FROM seed_journey j
JOIN care_case c
  ON c.public_id = 'case_seed_' || LPAD(j.seed_no::TEXT, 3, '0')
JOIN booking b
  ON b.booking_id = c.booking_id
JOIN patient p
  ON p.patient_id = c.patient_id
WHERE j.mission_phase IS NOT NULL
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
    updated_at = NOW();

INSERT INTO consultation_session (
    public_id,
    case_id,
    status,
    room_id,
    livekit_url,
    doctor_connection_state,
    patient_connection_state,
    doctor_joined_at,
    patient_joined_at,
    started_at,
    ended_at,
    duration_minutes,
    created_at
)
SELECT
    'ses_seed_' || LPAD(j.seed_no::TEXT, 3, '0'),
    c.case_id,
    j.session_status,
    'seed-room-' || LPAD(j.seed_no::TEXT, 3, '0'),
    COALESCE(
        NULLIF((SELECT livekit_url
                FROM consultation_session
                WHERE livekit_url IS NOT NULL AND livekit_url <> ''
                ORDER BY session_id DESC
                LIMIT 1), ''),
        '__LIVEKIT_URL__'
    ),
    CASE
        WHEN j.session_status IN ('IN_PROGRESS', 'COMPLETED') THEN 'CONNECTED'
        ELSE 'DISCONNECTED'
    END,
    CASE
        WHEN j.session_status IN ('IN_PROGRESS', 'COMPLETED') THEN 'CONNECTED'
        ELSE 'DISCONNECTED'
    END,
    CASE
        WHEN j.session_status IN ('IN_PROGRESS', 'COMPLETED') THEN (b.appointment_date + b.start_time) + INTERVAL '3 minutes'
        ELSE NULL
    END,
    CASE
        WHEN j.session_status IN ('IN_PROGRESS', 'COMPLETED') THEN (b.appointment_date + b.start_time) + INTERVAL '4 minutes'
        ELSE NULL
    END,
    CASE
        WHEN j.session_status IN ('IN_PROGRESS', 'COMPLETED') THEN (b.appointment_date + b.start_time) + INTERVAL '5 minutes'
        ELSE NULL
    END,
    CASE
        WHEN j.session_status = 'COMPLETED' THEN (b.appointment_date + b.start_time) + INTERVAL '22 minutes'
        ELSE NULL
    END,
    CASE
        WHEN j.session_status = 'COMPLETED' THEN 17
        WHEN j.session_status = 'IN_PROGRESS' THEN 9
        ELSE NULL
    END,
    NOW()
FROM seed_journey j
JOIN care_case c
  ON c.public_id = 'case_seed_' || LPAD(j.seed_no::TEXT, 3, '0')
JOIN booking b
  ON b.booking_id = c.booking_id
WHERE j.session_status IS NOT NULL
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

INSERT INTO consultation_summary (
    session_id,
    summary_note,
    is_prescription_issued,
    prescription_note,
    needs_follow_up,
    created_at
)
SELECT
    s.session_id,
    CASE dp.department
        WHEN 'INTERNAL_MEDICINE' THEN '혈압과 복용 약을 점검했고 현재 처방 유지 및 경과 관찰을 안내했습니다.'
        WHEN 'ORTHOPEDICS' THEN '관절 통증은 퇴행성 변화 가능성이 있어 활동량 조절과 찜질을 안내했습니다.'
        WHEN 'DERMATOLOGY' THEN '피부 자극 회피와 연고 사용법, 재내원 기준을 설명했습니다.'
        WHEN 'NEUROLOGY' THEN '어지럼과 두통은 생활 관리와 복약 지침을 중심으로 설명했습니다.'
        WHEN 'OPHTHALMOLOGY' THEN '안구 건조와 충혈 증상 완화를 위한 점안제 사용법을 안내했습니다.'
        ELSE '진료 후 경과 관찰과 복약 지도를 안내했습니다.'
    END,
    CASE
        WHEN dp.department IN ('DERMATOLOGY', 'OPHTHALMOLOGY') THEN TRUE
        WHEN MOD(j.seed_no, 3) <> 0 THEN TRUE
        ELSE FALSE
    END,
    CASE
        WHEN dp.department = 'INTERNAL_MEDICINE' THEN '혈압약 또는 소화기 증상 완화 약 7일분 처방'
        WHEN dp.department = 'ORTHOPEDICS' THEN '소염진통제와 근이완제 5일분 처방'
        WHEN dp.department = 'DERMATOLOGY' THEN '연고와 항히스타민제 사용법 안내'
        WHEN dp.department = 'NEUROLOGY' THEN '두통 조절 약과 생활 관리 지침 안내'
        WHEN dp.department = 'OPHTHALMOLOGY' THEN '점안제와 인공눈물 사용법 안내'
        ELSE NULL
    END,
    CASE
        WHEN dp.department IN ('NEUROLOGY', 'ORTHOPEDICS') THEN TRUE
        WHEN MOD(j.seed_no, 4) = 0 THEN TRUE
        ELSE FALSE
    END,
    NOW()
FROM seed_journey j
JOIN consultation_session s
  ON s.public_id = 'ses_seed_' || LPAD(j.seed_no::TEXT, 3, '0')
JOIN care_case c
  ON c.case_id = s.case_id
JOIN doctor_profile dp
  ON dp.doctor_profile_id = c.doctor_id
WHERE j.create_summary IS TRUE
ON CONFLICT (session_id) DO UPDATE
SET summary_note = EXCLUDED.summary_note,
    is_prescription_issued = EXCLUDED.is_prescription_issued,
    prescription_note = EXCLUDED.prescription_note,
    needs_follow_up = EXCLUDED.needs_follow_up;

CREATE TEMP TABLE seed_intake_only (
    seed_no INT PRIMARY KEY,
    patient_seed_no INT NOT NULL,
    department TEXT NOT NULL,
    department_name TEXT NOT NULL,
    selection_reason TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_intake_only (seed_no, patient_seed_no, department, department_name, selection_reason)
VALUES
    (27, 27, 'INTERNAL_MEDICINE', '내과', '기침과 미열이 이어져 내과 상담만 진행'),
    (28, 28, 'ORTHOPEDICS', '정형외과', '무릎 통증 상담 후 가족과 일정 재조율 예정'),
    (29, 29, 'DERMATOLOGY', '피부과', '가려움 증상 상담 후 추후 예약 예정'),
    (30, 30, 'NEUROLOGY', '신경과', '두통 상담 후 기존 예약 여부만 확인'),
    (31, 31, 'OPHTHALMOLOGY', '안과', '시야 흐림 상담 후 추후 예약 예정'),
    (32, 32, 'INTERNAL_MEDICINE', '내과', '혈압 상담만 진행하고 예약은 보류'),
    (33, 33, 'ORTHOPEDICS', '정형외과', '허리 통증 상담 후 가족 확인 대기'),
    (34, 34, 'DERMATOLOGY', '피부과', '피부 증상 상담 후 약 복용 여부 검토 중');

INSERT INTO intake_session (
    public_id,
    patient_id,
    caller_number,
    channel,
    status,
    completion_reason,
    ended_at,
    last_activity_at,
    selected_department,
    selected_department_name,
    selection_reason,
    selection_confidence_level,
    selection_is_emergency,
    offered_slot_ids_json,
    selection_updated_at,
    created_at
)
SELECT
    'ints_seed_' || LPAD(i.seed_no::TEXT, 3, '0'),
    p.patient_id,
    p.phone,
    'PHONE',
    'COMPLETED',
    'EXISTING_BOOKING_CHECKED',
    NOW() - INTERVAL '10 minutes',
    NOW() - INTERVAL '12 minutes',
    i.department,
    i.department_name,
    i.selection_reason,
    'HIGH',
    FALSE,
    '[]',
    NOW() - INTERVAL '12 minutes',
    NOW()
FROM seed_intake_only i
JOIN seed_patient sp
  ON sp.seed_no = i.patient_seed_no
JOIN patient p
  ON p.phone = sp.phone
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

COMMIT;
