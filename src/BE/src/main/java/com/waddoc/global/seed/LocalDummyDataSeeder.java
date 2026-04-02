package com.waddoc.global.seed;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.booking.repository.BookingRepository;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.consultation.entity.ConnectionState;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.entity.ConsultationSummary;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.consultation.repository.ConsultationSummaryRepository;
import com.waddoc.domain.dispatch.entity.DispatchOutbox;
import com.waddoc.domain.dispatch.entity.DispatchOutboxStatus;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.doctor.entity.ScheduleSlot;
import com.waddoc.domain.doctor.repository.DoctorProfileRepository;
import com.waddoc.domain.doctor.repository.ScheduleSlotRepository;
import com.waddoc.domain.intake.entity.CompletionReason;
import com.waddoc.domain.intake.entity.ConfidenceLevel;
import com.waddoc.domain.intake.entity.IntakeChannel;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.entity.IntakeStatus;
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientGender;
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import com.waddoc.domain.patient.repository.PatientGuardianLinkRepository;
import com.waddoc.domain.patient.repository.PatientRepository;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.domain.user.repository.UserRepository;
import com.waddoc.domain.vehicle.entity.OperationalStatus;
import com.waddoc.domain.vehicle.entity.Vehicle;
import com.waddoc.domain.vehicle.repository.VehicleRepository;
import com.waddoc.global.type.ApprovalStatus;
import com.waddoc.global.util.KstTime;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Component
@Profile({ "local", "prod" })
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class LocalDummyDataSeeder implements ApplicationRunner {

    private static final String PHONE_CHANNEL = IntakeChannel.PHONE.name();
    private static final String WEB_SIMULATOR_CHANNEL = IntakeChannel.WEB_SIMULATOR.name();
    private static final String OUTPATIENT_CHANNEL = "OUTPATIENT";
    private static final String ADMIN_USERNAME = "seed_prod_admin";
    private static final String ADMIN_NAME = "운영 더미 관리자";
    private static final String LIVEKIT_URL_PLACEHOLDER = "__SET_LIVEKIT_URL__";
    private static final String TOPOLOGY_ADDRESS_PREFIX = "경상북도 김천시 증산면 위상지도 웨이포인트 ";
    private static final String DEFAULT_GUARDIAN_RELATION = "자녀";
    private static final DateTimeFormatter BIRTH_DATE6_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final LocalDate FUTURE_SLOT_END_DATE = LocalDate.of(2026, 4, 13);
    // 현재 데모 시나리오에서는 당일 활성 비대면 예약을 만들지 않는다.
    private static final int UPCOMING_ACTIVE_BOOKING_DAY_COUNT = 0;
    // 2026-03-27 기준 ros2 TopologicalMap.json 의 waypoint 는 1..229 연속이다.
    private static final int TOPOLOGICAL_WAYPOINT_START = 1;
    private static final int TOPOLOGICAL_WAYPOINT_END = 229;
    private static final int TARGET_PATIENT_WAYPOINT_END = 142;
    private static final int SYNTHETIC_ADDRESS_BUILDING_NUMBER_OFFSET = 3;
    private static final String PRIMARY_PATIENT_KEY = "gim_wp_059";
    private static final String PRIMARY_PATIENT_NAME = "김원준";
    private static final String PRIMARY_PATIENT_PHONE = "01067984260";
    private static final String PRIMARY_PATIENT_ADDRESS = "경상북도 김천시 증산면 장전4길 14";
    private static final String PRIMARY_PATIENT_DOCTOR_USERNAME = "seed_prod_doc_im_01";
    private static final String PRIMARY_PATIENT_CONSULTATION_SUMMARY = "혈압이 높게 유지되어 고혈압 약을 처방하고 염분 섭취를 줄이도록 안내함.";
    private static final String PRIMARY_PATIENT_PRESCRIPTION_NOTE = "[\"M022\"]";
    private static final int PRIMARY_PATIENT_HISTORY_MONTH = 3;
    private static final int PRIMARY_PATIENT_HISTORY_DAY = 22;
    private static final int PRIMARY_PATIENT_HISTORICAL_VISIT_COUNT = 1;
    private static final int PRIORITY_PATIENT_HISTORICAL_VISIT_COUNT = 3;

    private static final List<String> SYNTHETIC_ROAD_NAMES = List.of(
            "황항길",
            "평촌길",
            "유성길",
            "수도길",
            "송하길",
            "가례길",
            "금곡길",
            "모산길",
            "삼도봉로",
            "증산로",
            "하강길",
            "부항길");

    private static final List<String> SYNTHETIC_LAST_NAMES = List.of(
            "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임",
            "한", "오", "서", "신", "권", "황", "안", "송", "전", "홍");

    private static final List<String> SYNTHETIC_PATIENT_GIVEN_FIRST = List.of(
            "민", "서", "지", "현", "도", "재", "준", "수", "영", "은",
            "하", "선", "태", "진", "혜");

    private static final List<String> SYNTHETIC_PATIENT_GIVEN_SECOND = List.of(
            "수", "진", "우", "아", "현", "민", "호", "윤", "영", "준",
            "은", "경", "연", "희", "찬");

    private static final List<String> SYNTHETIC_GUARDIAN_GIVEN_FIRST = List.of(
            "도", "서", "하", "예", "유", "주", "현", "민", "지", "수",
            "정", "준", "다", "채", "혜");

    private static final List<String> SYNTHETIC_GUARDIAN_GIVEN_SECOND = List.of(
            "현", "원", "진", "서", "은", "아", "호", "혁", "윤", "찬",
            "경", "림", "우", "빈", "영");

    private static final LocalTime REALISTIC_SLOT_OPEN_TIME = LocalTime.of(9, 0);
    private static final LocalTime REALISTIC_SLOT_CLOSE_TIME = LocalTime.of(23, 0);
    private static final List<LocalTime> REALISTIC_SLOT_START_TIMES = buildRealisticSlotStartTimes();
    private static final List<String> SYNTHETIC_MALE_PATIENT_GIVEN_NAMES = List.of(
            "영수", "영호", "상철", "병철", "종수", "춘식", "만수", "기태", "동식", "재덕",
            "용환", "석구", "정환", "복남", "태식", "병수", "남철", "성호", "달수", "경수");
    private static final List<String> SYNTHETIC_FEMALE_PATIENT_GIVEN_NAMES = List.of(
            "영순", "정숙", "금순", "춘자", "옥자", "복순", "미자", "순덕", "경자", "정희",
            "영자", "영희", "인숙", "명자", "정자", "봉순", "순자", "귀남", "길순", "말순");
    private static final List<String> SYNTHETIC_GUARDIAN_GIVEN_NAMES = List.of(
            "민지", "서연", "지현", "주희", "도윤", "민석", "은정", "소연", "현우", "지훈",
            "나영", "유진", "다현", "서준", "하늘", "가영", "민호", "정민", "수진", "예린");
    private static final int TARGET_HISTORICAL_MISSION_COUNT = 50;
    private static final int PENDING_GUARDIAN_LINK_COUNT = 5;
    private static final int OUTPATIENT_BOOKINGS_PER_DOCTOR = 5;

    private static final List<DoctorSeed> DOCTOR_SEEDS = List.of(
            new DoctorSeed("seed_prod_doc_im_01", "김도현", "INTERNAL_MEDICINE", "내과"),
            new DoctorSeed("seed_prod_doc_im_02", "박지연", "INTERNAL_MEDICINE", "내과"),
            new DoctorSeed("seed_prod_doc_ortho_01", "최준혁", "ORTHOPEDICS", "정형외과"),
            new DoctorSeed("seed_prod_doc_derm_01", "임수빈", "DERMATOLOGY", "피부과"),
            new DoctorSeed("seed_prod_doc_neuro_01", "정유진", "NEUROLOGY", "신경과"),
            new DoctorSeed("seed_prod_doc_eye_01", "장소라", "OPHTHALMOLOGY", "안과"));

    private static final List<VehicleSeed> VEHICLE_SEEDS = List.of(
            new VehicleSeed("veh_GIMCHEON_01", "GIMCHEON-01", "GIMCHEON", "김천 1호차"),
            new VehicleSeed("veh_ANDONG_01", "ANDONG-01", "ANDONG", "안동 1호차"),
            new VehicleSeed("veh_YEONGJU_01", "YEONGJU-01", "YEONGJU", "영주 1호차"),
            new VehicleSeed("veh_SANGJU_01", "SANGJU-01", "SANGJU", "상주 1호차"));

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final ScheduleSlotRepository scheduleSlotRepository;
    private final BookingRepository bookingRepository;
    private final CareCaseRepository careCaseRepository;
    private final PatientGuardianLinkRepository patientGuardianLinkRepository;
    private final IntakeSessionRepository intakeSessionRepository;
    private final MissionRepository missionRepository;
    private final ConsultationSessionRepository consultationSessionRepository;
    private final ConsultationSummaryRepository consultationSummaryRepository;
    private final DispatchOutboxRepository dispatchOutboxRepository;
    private final VehicleRepository vehicleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    @Value("${app.seed.default-password}")
    private String defaultPassword;

    @Value("${livekit.url:}")
    private String livekitUrl;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        assertSeedDefaultPasswordConfigured();
        LocalDate today = KstTime.now().toLocalDate();
        User adminUser = ensureUser(ADMIN_USERNAME, ADMIN_NAME, Role.ADMIN, null, ApprovalStatus.APPROVED);
        Map<String, DoctorProfile> doctorsByUsername = seedDoctors(adminUser);
        Map<String, Vehicle> vehiclesByCode = seedVehicles();
        List<PatientSeed> patientSeeds = buildPatientSeeds();
        Map<String, Patient> patientsByKey = seedPatients(patientSeeds, adminUser);
        syncPrimaryPatientCreatedAt(today, patientsByKey);
        pruneLegacySeedPatients(patientsByKey);
        Map<String, User> guardiansByPatientKey = seedGuardians(patientSeeds, adminUser);

        seedGuardianLinks(patientSeeds, patientsByKey, guardiansByPatientKey, adminUser);
        seedHistoricalBookings(today, doctorsByUsername, patientsByKey, vehiclesByCode, patientSeeds);
        seedUpcomingBookings(today, doctorsByUsername, patientsByKey, vehiclesByCode, patientSeeds);
        seedOutpatientBookings(today, doctorsByUsername, patientsByKey, patientSeeds);
        seedFutureSlots(today, doctorsByUsername);

        log.info(
                "Prod-like dummy data synced. adminUsername={}, doctorCount={}, vehicleCodes={}, patientCount={}, guardianCount={}, futureSlotEnd={}",
                adminUser.getUsername(), doctorsByUsername.size(), vehiclesByCode.keySet(), patientsByKey.size(),
                guardiansByPatientKey.size(), FUTURE_SLOT_END_DATE);
    }

    private void assertSeedDefaultPasswordConfigured() {
        if (defaultPassword == null || defaultPassword.isBlank()) {
            throw new IllegalStateException("APP_SEED_DEFAULT_PASSWORD must be set when app.seed.enabled=true");
        }
    }

    private Map<String, DoctorProfile> seedDoctors(User adminUser) {
        Map<String, DoctorProfile> doctorsByUsername = new LinkedHashMap<>();
        for (DoctorSeed seed : DOCTOR_SEEDS) {
            User user = ensureUser(seed.username(), seed.name(), Role.DOCTOR, adminUser, ApprovalStatus.APPROVED);
            doctorsByUsername.put(seed.username(), ensureDoctorProfile(user, seed.department(), seed.departmentName()));
        }
        return doctorsByUsername;
    }

    private List<PatientSeed> buildPatientSeeds() {
        List<PatientSeed> priorityPatientSeeds = buildPriorityPatientSeeds();
        Map<Integer, PatientSeed> prioritizedSeedsByWaypoint = new LinkedHashMap<>();
        for (PatientSeed seed : priorityPatientSeeds) {
            prioritizedSeedsByWaypoint.put(seed.waypointNumber(), seed);
        }

        List<PatientSeed> patientSeeds = new java.util.ArrayList<>(priorityPatientSeeds);
        for (int waypointNumber = TOPOLOGICAL_WAYPOINT_START; waypointNumber <= TARGET_PATIENT_WAYPOINT_END; waypointNumber++) {
            if (prioritizedSeedsByWaypoint.containsKey(waypointNumber)) {
                continue;
            }
            patientSeeds.add(buildRealisticSyntheticPatientSeed(waypointNumber));
        }
        return patientSeeds;
    }

    private PatientSeed buildRealisticSyntheticPatientSeed(int waypointNumber) {
        String suffix = buildWaypointSuffix(waypointNumber);
        return new PatientSeed(
                "gim_wp_" + suffix,
                buildSyntheticPatientName(waypointNumber),
                LocalDate.of(1945 + (waypointNumber % 35), ((waypointNumber - 1) % 12) + 1,
                        ((waypointNumber - 1) % 28) + 1),
                waypointNumber % 2 == 0 ? PatientGender.FEMALE : PatientGender.MALE,
                "GIMCHEON",
                buildSyntheticWaypointAddress(waypointNumber),
                buildWaypointPhone(waypointNumber),
                null,
                waypointNumber,
                buildGuardianUsername(waypointNumber),
                buildSyntheticGuardianName(waypointNumber),
                DEFAULT_GUARDIAN_RELATION);
    }

    private Map<String, Vehicle> seedVehicles() {
        Map<String, Vehicle> vehiclesByCode = new LinkedHashMap<>();
        for (VehicleSeed seed : VEHICLE_SEEDS) {
            vehiclesByCode.put(seed.code(), ensureVehicle(seed));
        }
        return vehiclesByCode;
    }

    private Map<String, Patient> seedPatients(List<PatientSeed> patientSeeds, User adminUser) {
        Map<String, Patient> patientsByKey = new LinkedHashMap<>();
        for (PatientSeed seed : patientSeeds) {
            patientsByKey.put(seed.key(), ensurePatient(
                    seed.name(), seed.birthDate(), seed.gender(), seed.regionCode(), seed.address(), seed.phone(),
                    seed.referenceImagePath(), adminUser));
        }
        return patientsByKey;
    }

    private void syncPrimaryPatientCreatedAt(LocalDate today, Map<String, Patient> patientsByKey) {
        Patient primaryPatient = patientsByKey.get(PRIMARY_PATIENT_KEY);
        if (primaryPatient == null || primaryPatient.getId() == null) {
            return;
        }

        entityManager.createNativeQuery("""
                update patient
                   set created_at = ?1
                 where patient_id = ?2
                """)
                .setParameter(1, LocalDate.of(today.getYear(), 3, 1).atStartOfDay())
                .setParameter(2, primaryPatient.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.refresh(primaryPatient);
    }

    private void pruneLegacySeedPatients(Map<String, Patient> patientsByKey) {
        List<Long> keepPatientIds = patientsByKey.values().stream()
                .map(Patient::getId)
                .filter(Objects::nonNull)
                .toList();
        if (keepPatientIds.isEmpty()) {
            return;
        }

        List<Long> removablePatientIds = entityManager.createQuery("""
                select p.id
                  from Patient p
                 where p.id not in :keepPatientIds
                   and p.referenceImagePath like :legacySeedReferencePattern
                """, Long.class)
                .setParameter("keepPatientIds", keepPatientIds)
                .setParameter("legacySeedReferencePattern", "patients/pat_prd_%/reference.jpg")
                .getResultList();
        if (removablePatientIds.isEmpty()) {
            return;
        }

        List<Long> bookingIds = entityManager.createQuery("""
                select b.id
                  from Booking b
                 where b.patient.id in :patientIds
                """, Long.class)
                .setParameter("patientIds", removablePatientIds)
                .getResultList();
        List<Long> caseIds = entityManager.createQuery("""
                select c.id
                  from CareCase c
                 where c.patient.id in :patientIds
                """, Long.class)
                .setParameter("patientIds", removablePatientIds)
                .getResultList();
        List<Long> slotIds = bookingIds.isEmpty()
                ? List.of()
                : entityManager.createQuery("""
                        select distinct b.slot.id
                          from Booking b
                         where b.id in :bookingIds
                        """, Long.class)
                        .setParameter("bookingIds", bookingIds)
                        .getResultList();
        LinkedHashSet<Long> intakeSessionIds = new LinkedHashSet<>(entityManager.createQuery("""
                select distinct i.id
                  from IntakeSession i
                 where i.patient.id in :patientIds
                """, Long.class)
                .setParameter("patientIds", removablePatientIds)
                .getResultList());
        if (!bookingIds.isEmpty()) {
            intakeSessionIds.addAll(entityManager.createQuery("""
                    select distinct b.intakeSession.id
                      from Booking b
                     where b.id in :bookingIds
                       and b.intakeSession is not null
                    """, Long.class)
                    .setParameter("bookingIds", bookingIds)
                    .getResultList());
        }

        entityManager.createQuery("""
                delete from PatientGuardianLink l
                 where l.patient.id in :patientIds
                """)
                .setParameter("patientIds", removablePatientIds)
                .executeUpdate();

        if (!caseIds.isEmpty()) {
            entityManager.createQuery("""
                    delete from DispatchOutbox d
                     where d.careCase.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
            entityManager.createQuery("""
                    delete from VitalMeasurement v
                     where v.careCase.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
            entityManager.createQuery("""
                    delete from ConsultationSummary cs
                     where cs.session.careCase.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
            entityManager.createQuery("""
                    delete from ConsultationSession s
                     where s.careCase.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
            entityManager.createQuery("""
                    delete from Mission m
                     where m.careCase.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
            entityManager.createQuery("""
                    delete from CareCase c
                     where c.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
        }

        if (!bookingIds.isEmpty()) {
            entityManager.createQuery("""
                    delete from Booking b
                     where b.id in :bookingIds
                    """)
                    .setParameter("bookingIds", bookingIds)
                    .executeUpdate();
        }

        if (!slotIds.isEmpty()) {
            entityManager.createQuery("""
                    update ScheduleSlot s
                       set s.booked = false
                     where s.id in :slotIds
                    """)
                    .setParameter("slotIds", slotIds)
                    .executeUpdate();
        }

        deleteOrphanIntakeSessions(intakeSessionIds);

        entityManager.createQuery("""
                delete from Patient p
                 where p.id in :patientIds
                """)
                .setParameter("patientIds", removablePatientIds)
                .executeUpdate();
        entityManager.flush();
    }

    private Map<String, User> seedGuardians(List<PatientSeed> patientSeeds, User adminUser) {
        java.util.Set<String> approvedPatientKeys = approvedGuardianPatientKeys();
        java.util.Set<String> pendingPatientKeys = buildPendingGuardianPatientKeys(patientSeeds, approvedPatientKeys);
        java.util.Set<String> targetPatientKeys = new java.util.LinkedHashSet<>(approvedPatientKeys);
        targetPatientKeys.addAll(pendingPatientKeys);

        Map<String, User> guardiansByPatientKey = new LinkedHashMap<>();
        for (PatientSeed seed : patientSeeds) {
            if (!targetPatientKeys.contains(seed.key())) {
                continue;
            }

            ApprovalStatus targetStatus = approvedPatientKeys.contains(seed.key())
                    ? ApprovalStatus.APPROVED
                    : ApprovalStatus.PENDING;
            guardiansByPatientKey.put(seed.key(), ensureUser(
                    seed.guardianUsername(),
                    seed.guardianName(),
                    Role.GUARDIAN,
                    adminUser,
                    targetStatus));
        }
        return guardiansByPatientKey;
    }

    private void seedGuardianLinks(List<PatientSeed> patientSeeds, Map<String, Patient> patientsByKey,
            Map<String, User> guardiansByPatientKey, User adminUser) {
        java.util.Set<String> approvedPatientKeys = approvedGuardianPatientKeys();
        java.util.Set<String> pendingPatientKeys = buildPendingGuardianPatientKeys(patientSeeds, approvedPatientKeys);

        for (PatientSeed seed : patientSeeds) {
            User guardianUser = guardiansByPatientKey.get(seed.key());
            if (guardianUser == null) {
                continue;
            }

            Patient patient = getPatient(patientsByKey, seed.key());

            if (approvedPatientKeys.contains(seed.key())) {
                ensureGuardianLink(
                        patient,
                        guardianUser,
                        seed.guardianRelation(),
                        GuardianLinkStatus.APPROVED,
                        adminUser);
            } else if (pendingPatientKeys.contains(seed.key())) {
                ensureGuardianLink(
                        patient,
                        guardianUser,
                        seed.guardianRelation(),
                        GuardianLinkStatus.PENDING,
                        adminUser);
            }
        }
    }

    private void seedHistoricalBookings(LocalDate today, Map<String, DoctorProfile> doctorsByUsername,
            Map<String, Patient> patientsByKey, Map<String, Vehicle> vehiclesByCode, List<PatientSeed> patientSeeds) {
        LocalDate historyStart = resolveHistoricalSeedStartDate(today);
        if (today.isBefore(historyStart)) {
            return;
        }

        LocalDate historyEnd = resolveHistoricalSeedEndDate(today);
        if (historyEnd.isBefore(historyStart)) {
            return;
        }
        LocalDate primaryPatientHistoryDate = resolvePrimaryPatientHistoricalConsultationDate(today, historyStart,
                historyEnd);

        resetSeedClinicalArtifacts(patientsByKey, doctorsByUsername);

        List<HistoricalBookingPlan> bookingPlans = buildHistoricalBookingPlans(patientSeeds);
        List<HistoricalAppointmentSlot> appointmentSlots = buildHistoricalAppointmentSlots(historyStart, historyEnd,
                bookingPlans.size());
        Vehicle gimcheonVehicle = getVehicle(vehiclesByCode, "GIMCHEON-01");

        for (int index = 0; index < bookingPlans.size(); index++) {
            HistoricalBookingPlan bookingPlan = bookingPlans.get(index);
            HistoricalAppointmentSlot appointmentSlot = appointmentSlots.get(index);
            PatientSeed seed = bookingPlan.patientSeed();
            Patient patient = getPatient(patientsByKey, seed.key());
            DoctorProfile doctor = getDoctor(doctorsByUsername, bookingPlan.doctorUsername());
            LocalDate appointmentDate = appointmentSlot.appointmentDate();
            LocalTime startTime = appointmentSlot.startTime();
            if (isPrimaryPatientFirstHistoricalVisit(bookingPlan)) {
                appointmentDate = primaryPatientHistoryDate;
                startTime = REALISTIC_SLOT_START_TIMES.get(0);
            }
            LocalTime endTime = startTime.plusMinutes(30);
            LocalDateTime appointmentDateTime = appointmentDate.atTime(startTime);
            LocalDateTime intakeCompletedAt = appointmentDateTime.minusDays(1).withHour(17).withMinute(10);
            LocalDateTime intakeLastActivityAt = intakeCompletedAt.minusMinutes(5);
            LocalDateTime dispatchedAt = appointmentDateTime.minusMinutes(25);
            LocalDateTime estimatedArrivalTime = appointmentDateTime.minusMinutes(5);
            LocalDateTime doctorJoinedAt = appointmentDateTime.plusMinutes(2);
            LocalDateTime consultationStartedAt = appointmentDateTime.plusMinutes(5);
            LocalDateTime consultationEndedAt = appointmentDateTime.plusMinutes(25);
            LocalDateTime missionCompletedAt = appointmentDateTime.plusMinutes(40);

            ScheduleSlot slot = ensureSlot(doctor, appointmentDate, startTime, endTime);
            IntakeSession intakeSession = ensureIntakeSession(
                    buildHistoricalIntakePublicId(seed, bookingPlan.visitSequence()),
                    patient,
                    patient.getPhone(),
                    IntakeChannel.PHONE,
                    doctor.getDepartment(),
                    doctor.getDepartmentName(),
                    "",
                    List.of(slot.getPublicId()),
                    CompletionReason.BOOKING_CREATED);
            syncIntakeSessionTimeline(
                    intakeSession,
                    intakeLastActivityAt,
                    intakeCompletedAt,
                    CompletionReason.BOOKING_CREATED);

            Booking booking = ensureBooking(patient, slot, PHONE_CHANNEL, BookingStatus.COMPLETED, intakeSession, null);
            CareCase careCase = ensureCareCase(booking, intakeSession);
            syncCaseStatus(careCase, CaseStatus.COMPLETED);

            Mission mission = ensureMission(
                    careCase,
                    gimcheonVehicle.getPublicId(),
                    patient.getAddress(),
                    dispatchedAt,
                    estimatedArrivalTime,
                    seed.waypointNumber());
            syncMissionState(
                    mission,
                    gimcheonVehicle.getPublicId(),
                    patient.getAddress(),
                    dispatchedAt,
                    estimatedArrivalTime,
                    MissionPhase.COMPLETED,
                    MissionPhase.RETURNING,
                    null,
                    null,
                    missionCompletedAt,
                    seed.waypointNumber());

            ConsultationSession session = ensureConsultationSession(
                    careCase,
                    "seed-room-" + buildWaypointSuffix(seed.waypointNumber()) + "-" + bookingPlan.visitSequence(),
                    resolveSeedLivekitUrl());
            syncConsultationSessionState(
                    session,
                    ConsultationSessionStatus.COMPLETED,
                    doctorJoinedAt,
                    consultationStartedAt,
                    consultationEndedAt,
                    20);
            if (isPrimaryPatientFirstHistoricalVisit(bookingPlan)) {
                ensureConsultationSummary(
                        session,
                        PRIMARY_PATIENT_CONSULTATION_SUMMARY,
                        true,
                        PRIMARY_PATIENT_PRESCRIPTION_NOTE,
                        true);
            }
        }
    }

    private void seedUpcomingBookings(LocalDate today, Map<String, DoctorProfile> doctorsByUsername,
            Map<String, Patient> patientsByKey, Map<String, Vehicle> vehiclesByCode, List<PatientSeed> patientSeeds) {
        if (today.isAfter(FUTURE_SLOT_END_DATE)) {
            return;
        }

        pruneUpcomingSeedArtifacts(today, FUTURE_SLOT_END_DATE, patientSeeds, patientsByKey);
        if (UPCOMING_ACTIVE_BOOKING_DAY_COUNT <= 0) {
            return;
        }

        List<DoctorProfile> doctors = List.copyOf(doctorsByUsername.values());
        Vehicle gimcheonVehicle = getVehicle(vehiclesByCode, "GIMCHEON-01");
        LocalDate upcomingStartDate = resolveSeedScheduleStartDate(today);
        LocalDate upcomingEndDate = resolveUpcomingActiveBookingEndDate(today);
        if (upcomingEndDate.isBefore(upcomingStartDate)) {
            return;
        }
        int upcomingDayCount = (int) upcomingStartDate.datesUntil(upcomingEndDate.plusDays(1)).count();
        int upcomingBookingCount = Math.min(patientSeeds.size(), upcomingDayCount * doctors.size());

        for (int index = 0; index < upcomingBookingCount; index++) {
            PatientSeed seed = patientSeeds.get(index);
            Patient patient = getPatient(patientsByKey, seed.key());
            LocalDate appointmentDate = upcomingStartDate.plusDays(index % upcomingDayCount);
            DoctorProfile doctor = doctors.get((index / upcomingDayCount) % doctors.size());
            LocalTime startTime = REALISTIC_SLOT_START_TIMES.get(index % REALISTIC_SLOT_START_TIMES.size());
            LocalTime endTime = startTime.plusMinutes(30);
            LocalDateTime appointmentDateTime = appointmentDate.atTime(startTime);
            LocalDateTime intakeCompletedAt = appointmentDateTime.minusHours(1);
            LocalDateTime intakeLastActivityAt = intakeCompletedAt.minusMinutes(5);
            LocalDateTime dispatchedAt = appointmentDateTime.minusMinutes(20);
            LocalDateTime estimatedArrivalTime = appointmentDateTime.minusMinutes(2);

            ScheduleSlot slot = ensureSlot(doctor, appointmentDate, startTime, endTime);
            IntakeSession intakeSession = ensureIntakeSession(
                    patient,
                    patient.getPhone(),
                    IntakeChannel.WEB_SIMULATOR,
                    doctor.getDepartment(),
                    doctor.getDepartmentName(),
                    "",
                    List.of(slot.getPublicId()),
                    CompletionReason.BOOKING_CREATED);
            syncIntakeSessionTimeline(
                    intakeSession,
                    intakeLastActivityAt,
                    intakeCompletedAt,
                    CompletionReason.BOOKING_CREATED);

            Booking booking = ensureBooking(patient, slot, WEB_SIMULATOR_CHANNEL, BookingStatus.CONFIRMED,
                    intakeSession, null);
            CareCase careCase = ensureCareCase(booking, intakeSession);
            syncCaseStatus(careCase, CaseStatus.CREATED);

            Mission mission = ensureMission(
                    careCase,
                    gimcheonVehicle.getPublicId(),
                    patient.getAddress(),
                    dispatchedAt,
                    estimatedArrivalTime,
                    seed.waypointNumber());
            syncMissionState(
                    mission,
                    gimcheonVehicle.getPublicId(),
                    patient.getAddress(),
                    dispatchedAt,
                    estimatedArrivalTime,
                    MissionPhase.ARRIVED,
                    MissionPhase.EN_ROUTE,
                    null,
                    null,
                    null,
                    seed.waypointNumber());

            DispatchOutbox dispatchOutbox = ensureDispatchOutbox(careCase, patient.getRegionCode(),
                    patient.getAddress());
            syncDispatchOutboxState(dispatchOutbox, DispatchOutboxStatus.COMPLETED);
        }
    }

    private void seedOutpatientBookings(LocalDate today, Map<String, DoctorProfile> doctorsByUsername,
            Map<String, Patient> patientsByKey, List<PatientSeed> patientSeeds) {
        if (today.isAfter(FUTURE_SLOT_END_DATE)) {
            return;
        }

        LocalDate historyStart = resolveHistoricalSeedStartDate(today);
        LocalDate outpatientWindowEnd = resolveHistoricalSeedEndDate(today);
        if (outpatientWindowEnd.isBefore(historyStart)) {
            return;
        }

        int availablePatientCount = Math.max(0, patientSeeds.size() - DOCTOR_SEEDS.size());
        int outpatientBookingsPerDoctor = DOCTOR_SEEDS.isEmpty()
                ? 0
                : Math.min(OUTPATIENT_BOOKINGS_PER_DOCTOR, availablePatientCount / DOCTOR_SEEDS.size());
        if (outpatientBookingsPerDoctor <= 0) {
            return;
        }

        int patientSeedStartIndex = DOCTOR_SEEDS.size();
        for (int doctorIndex = 0; doctorIndex < DOCTOR_SEEDS.size(); doctorIndex++) {
            DoctorProfile doctor = getDoctor(doctorsByUsername, DOCTOR_SEEDS.get(doctorIndex).username());
            for (int bookingIndex = 0; bookingIndex < outpatientBookingsPerDoctor; bookingIndex++) {
                int patientSeedIndex = patientSeedStartIndex + (doctorIndex * outpatientBookingsPerDoctor)
                        + bookingIndex;
                PatientSeed seed = patientSeeds.get(patientSeedIndex);
                Patient patient = getPatient(patientsByKey, seed.key());
                LocalDate appointmentDate = outpatientWindowEnd.minusDays(bookingIndex / 2L);
                if (appointmentDate.isBefore(historyStart)) {
                    appointmentDate = historyStart;
                }
                LocalTime startTime = REALISTIC_SLOT_START_TIMES.get(4 + bookingIndex);
                LocalTime endTime = startTime.plusMinutes(30);

                ScheduleSlot slot = ensureSlot(doctor, appointmentDate, startTime, endTime);
                Booking booking = ensureBooking(patient, slot, OUTPATIENT_CHANNEL, BookingStatus.COMPLETED, null, null);
                CareCase careCase = ensureCareCase(booking, null);
                syncCaseStatus(careCase, CaseStatus.COMPLETED);
            }
        }
    }

    static LocalDate resolveUpcomingActiveBookingEndDate(LocalDate today) {
        if (UPCOMING_ACTIVE_BOOKING_DAY_COUNT <= 0) {
            return today.minusDays(1);
        }
        LocalDate candidate = today.plusDays(UPCOMING_ACTIVE_BOOKING_DAY_COUNT - 1L);
        return candidate.isAfter(FUTURE_SLOT_END_DATE) ? FUTURE_SLOT_END_DATE : candidate;
    }

    static LocalDate resolveSeedScheduleStartDate(LocalDate today) {
        return today.plusDays(1);
    }

    static LocalDate resolveHistoricalSeedStartDate(LocalDate today) {
        return LocalDate.of(today.getYear(), 3, 1);
    }

    static LocalDate resolveHistoricalSeedEndDate(LocalDate today) {
        LocalDate historyStart = resolveHistoricalSeedStartDate(today);
        if (today.isBefore(historyStart)) {
            return historyStart.minusDays(1);
        }
        if (!today.getMonth().equals(historyStart.getMonth())) {
            return LocalDate.of(today.getYear(), 3, 31);
        }
        return today.minusDays(1);
    }

    static LocalDate resolvePrimaryPatientHistoricalConsultationDate(
            LocalDate today,
            LocalDate historyStart,
            LocalDate historyEnd) {
        LocalDate targetDate = LocalDate.of(today.getYear(), PRIMARY_PATIENT_HISTORY_MONTH,
                PRIMARY_PATIENT_HISTORY_DAY);
        if (targetDate.isBefore(historyStart)) {
            return historyStart;
        }
        if (targetDate.isAfter(historyEnd)) {
            return historyEnd;
        }
        return targetDate;
    }

    private List<HistoricalBookingPlan> buildHistoricalBookingPlans(List<PatientSeed> patientSeeds) {
        PatientSeed kwonMiSoon = getPatientSeed(patientSeeds, "gim_wp_059");
        PatientSeed jeongJiHwan = getPatientSeed(patientSeeds, "gim_wp_092");
        PatientSeed kimJuDeok = getPatientSeed(patientSeeds, "gim_wp_142");

        Map<String, Integer> remainingBookingsByDoctor = new LinkedHashMap<>();
        remainingBookingsByDoctor.put("seed_prod_doc_im_01", 9);
        remainingBookingsByDoctor.put("seed_prod_doc_im_02", 9);
        remainingBookingsByDoctor.put("seed_prod_doc_ortho_01", 8);
        remainingBookingsByDoctor.put("seed_prod_doc_derm_01", 8);
        remainingBookingsByDoctor.put("seed_prod_doc_neuro_01", 8);
        remainingBookingsByDoctor.put("seed_prod_doc_eye_01", 8);

        consumeDoctorQuota(remainingBookingsByDoctor, PRIMARY_PATIENT_DOCTOR_USERNAME,
                PRIMARY_PATIENT_HISTORICAL_VISIT_COUNT);
        consumeDoctorQuota(remainingBookingsByDoctor, "seed_prod_doc_neuro_01",
                PRIORITY_PATIENT_HISTORICAL_VISIT_COUNT);
        consumeDoctorQuota(remainingBookingsByDoctor, "seed_prod_doc_ortho_01",
                PRIORITY_PATIENT_HISTORICAL_VISIT_COUNT);

        List<PatientSeed> otherPatients = patientSeeds.stream()
                .filter(seed -> !List.of("gim_wp_059", "gim_wp_092", "gim_wp_142").contains(seed.key()))
                .limit(TARGET_HISTORICAL_MISSION_COUNT
                        - PRIMARY_PATIENT_HISTORICAL_VISIT_COUNT
                        - (2L * PRIORITY_PATIENT_HISTORICAL_VISIT_COUNT))
                .toList();
        List<String> otherDoctorAssignments = buildDoctorAssignmentOrder(remainingBookingsByDoctor);
        if (otherPatients.size() != otherDoctorAssignments.size()) {
            throw new IllegalStateException("Historical seed booking plan is imbalanced.");
        }

        int firstOtherChunk = otherPatients.size() / 2;
        List<HistoricalBookingPlan> bookingPlans = new java.util.ArrayList<>(TARGET_HISTORICAL_MISSION_COUNT);
        appendPriorityPatientVisits(bookingPlans, kwonMiSoon, jeongJiHwan, kimJuDeok, 1);
        appendOtherPatientVisits(bookingPlans, otherPatients, otherDoctorAssignments, 0, firstOtherChunk);
        appendPriorityPatientVisits(bookingPlans, kwonMiSoon, jeongJiHwan, kimJuDeok, 2);
        appendOtherPatientVisits(bookingPlans, otherPatients, otherDoctorAssignments, firstOtherChunk,
                otherPatients.size());
        appendPriorityPatientVisits(bookingPlans, kwonMiSoon, jeongJiHwan, kimJuDeok, 3);

        if (bookingPlans.size() != TARGET_HISTORICAL_MISSION_COUNT) {
            throw new IllegalStateException("Historical seed booking count mismatch: " + bookingPlans.size());
        }
        return List.copyOf(bookingPlans);
    }

    private boolean isPrimaryPatientFirstHistoricalVisit(HistoricalBookingPlan bookingPlan) {
        return PRIMARY_PATIENT_KEY.equals(bookingPlan.patientSeed().key()) && bookingPlan.visitSequence() == 1;
    }

    private void appendPriorityPatientVisits(List<HistoricalBookingPlan> bookingPlans, PatientSeed kwonMiSoon,
            PatientSeed jeongJiHwan, PatientSeed kimJuDeok, int visitSequence) {
        if (visitSequence <= PRIMARY_PATIENT_HISTORICAL_VISIT_COUNT) {
            bookingPlans.add(new HistoricalBookingPlan(kwonMiSoon, PRIMARY_PATIENT_DOCTOR_USERNAME, visitSequence));
        }
        bookingPlans.add(new HistoricalBookingPlan(jeongJiHwan, "seed_prod_doc_neuro_01", visitSequence));
        bookingPlans.add(new HistoricalBookingPlan(kimJuDeok, "seed_prod_doc_ortho_01", visitSequence));
    }

    private void appendOtherPatientVisits(List<HistoricalBookingPlan> bookingPlans, List<PatientSeed> otherPatients,
            List<String> otherDoctorAssignments, int startInclusive, int endExclusive) {
        for (int index = startInclusive; index < endExclusive; index++) {
            bookingPlans.add(new HistoricalBookingPlan(otherPatients.get(index), otherDoctorAssignments.get(index), 1));
        }
    }

    private void consumeDoctorQuota(Map<String, Integer> remainingBookingsByDoctor, String doctorUsername, int amount) {
        Integer remaining = remainingBookingsByDoctor.get(doctorUsername);
        if (remaining == null || remaining < amount) {
            throw new IllegalStateException("Doctor booking quota exhausted: " + doctorUsername);
        }
        remainingBookingsByDoctor.put(doctorUsername, remaining - amount);
    }

    private List<String> buildDoctorAssignmentOrder(Map<String, Integer> remainingBookingsByDoctor) {
        List<String> doctorAssignments = new java.util.ArrayList<>();
        boolean assigned;
        do {
            assigned = false;
            for (Map.Entry<String, Integer> entry : remainingBookingsByDoctor.entrySet()) {
                if (entry.getValue() <= 0) {
                    continue;
                }
                doctorAssignments.add(entry.getKey());
                entry.setValue(entry.getValue() - 1);
                assigned = true;
            }
        } while (assigned);
        return List.copyOf(doctorAssignments);
    }

    private List<HistoricalAppointmentSlot> buildHistoricalAppointmentSlots(LocalDate historyStart,
            LocalDate historyEnd,
            int bookingCount) {
        int bookingsPerDay = 3;
        int requiredDayCount = (bookingCount + bookingsPerDay - 1) / bookingsPerDay;
        int availableDayCount = (int) historyStart.datesUntil(historyEnd.plusDays(1)).count();
        if (requiredDayCount > availableDayCount) {
            throw new IllegalStateException("Not enough historical days to create non-overlapping seed bookings.");
        }

        LocalDate firstAppointmentDate = historyEnd.minusDays(requiredDayCount - 1L);
        List<HistoricalAppointmentSlot> appointmentSlots = new java.util.ArrayList<>(bookingCount);
        int[] slotOffsets = { 0, 4, 9 };

        for (int dayIndex = 0; dayIndex < requiredDayCount && appointmentSlots.size() < bookingCount; dayIndex++) {
            LocalDate appointmentDate = firstAppointmentDate.plusDays(dayIndex);
            int baseOffset = dayIndex % 6;
            for (int slotOffset : slotOffsets) {
                if (appointmentSlots.size() >= bookingCount) {
                    break;
                }
                LocalTime startTime = REALISTIC_SLOT_START_TIMES.get(baseOffset + slotOffset);
                appointmentSlots.add(new HistoricalAppointmentSlot(appointmentDate, startTime));
            }
        }

        return List.copyOf(appointmentSlots);
    }

    private String buildHistoricalIntakePublicId(PatientSeed seed, int visitSequence) {
        return "ints_prd_%s_%d".formatted(buildWaypointSuffix(seed.waypointNumber()), visitSequence);
    }

    private String buildSelectionReason(DoctorProfile doctor, PatientSeed seed, int visitSequence) {
        String symptom = switch (doctor.getDepartment()) {
            case "INTERNAL_MEDICINE" -> List.of(
                    "혈압 변동과 어지럼이 반복되었음",
                    "기침과 미열이 며칠째 이어져",
                    "속쓰림과 복부 불편감이 있어").get(Math.floorMod(seed.waypointNumber() + visitSequence, 3));
            case "ORTHOPEDICS" -> List.of(
                    "무릎 통증과 보행 불편이 있어",
                    "허리 통증이 심해져",
                    "어깨 결림과 팔 저림이 있어").get(Math.floorMod(seed.waypointNumber() + visitSequence, 3));
            case "DERMATOLOGY" -> List.of(
                    "팔 안쪽 가려움과 발진이 계속되어",
                    "건조증과 각질이 심해져",
                    "등 부위 붉은 반점이 넓어져").get(Math.floorMod(seed.waypointNumber() + visitSequence, 3));
            case "NEUROLOGY" -> List.of(
                    "어지럼과 두통이 반복되어",
                    "손 저림과 감각 저하가 있어",
                    "수면 중에 떨림과 두근거림이 있어").get(Math.floorMod(seed.waypointNumber() + visitSequence, 3));
            case "OPHTHALMOLOGY" -> List.of(
                    "눈 충혈과 시야 흐림이 있어",
                    "눈꺼풀 이물감과 눈물이 심해",
                    "근거리 시야가 갑자기 흐려져").get(Math.floorMod(seed.waypointNumber() + visitSequence, 3));
            default -> "증상이 반복되어";
        };
        String allergyNote = switch (doctor.getDepartment()) {
            case "INTERNAL_MEDICINE" -> List.of(
                    "페니실린 복용 시 발진 이력이 있어 약 처방 전 확인 필요",
                    "갑각류 섭취 후 두드러기 반응이 있어 식이 안내 필요",
                    "조영제 알러지 이력이 있어 검사 전 고지 요청").get(Math.floorMod(seed.waypointNumber() * 2 + visitSequence, 3));
            case "ORTHOPEDICS" -> List.of(
                    "소염진통제 복용 시 속쓰림이 있어 위장약 동반 복용 필요",
                    "파스 접착제에 피부 발적이 있어 대체 처치 선호",
                    "라텍스 장갑 접촉 시 가려움 반응이 있어 주의 필요").get(Math.floorMod(seed.waypointNumber() * 2 + visitSequence, 3));
            case "DERMATOLOGY" -> List.of(
                    "향이 강한 보습제 사용 시 접촉성 발진 이력이 있음",
                    "꽃가루 알러지로 환절기 피부 증상이 쉽게 악화됨",
                    "니켈 접촉 시 손등에 가려움이 올라와 금속 자극 주의").get(Math.floorMod(seed.waypointNumber() * 2 + visitSequence, 3));
            case "NEUROLOGY" -> List.of(
                    "수면유도제 복용 후 과도한 졸림이 있어 저용량 선호",
                    "카페인 과민 반응이 있어 약 복용 시간 안내 필요",
                    "진통제 복용 후 메스꺼움 이력이 있어 식후 복용 희망").get(Math.floorMod(seed.waypointNumber() * 2 + visitSequence, 3));
            case "OPHTHALMOLOGY" -> List.of(
                    "인공눈물 보존제에 따가움이 있어 무보존제 제형 선호",
                    "항생제 안약 점안 후 눈 주위 발적 이력이 있어 성분 확인 필요",
                    "렌즈 세정액 사용 시 충혈이 심해져 대체 용품 사용 중").get(Math.floorMod(seed.waypointNumber() * 2 + visitSequence, 3));
            default -> "복용 약 알러지 여부를 다시 확인할 필요가 있음";
        };
        String visitType = visitSequence > 1 ? "재진" : "초진";
        return " %s 증상으로 %s %s 예약. 특이사항(알러지): %s"
                .formatted(symptom, doctor.getDepartmentName(), visitType, allergyNote);
    }

    private PatientSeed getPatientSeed(List<PatientSeed> patientSeeds, String patientKey) {
        return patientSeeds.stream()
                .filter(seed -> Objects.equals(seed.key(), patientKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Seed patient definition not found: " + patientKey));
    }

    private java.util.Set<String> approvedGuardianPatientKeys() {
        return java.util.Set.of("gim_wp_059", "gim_wp_092", "gim_wp_142");
    }

    private java.util.Set<String> buildPendingGuardianPatientKeys(
            List<PatientSeed> patientSeeds,
            java.util.Set<String> approvedPatientKeys) {
        java.util.Set<String> pendingPatientKeys = new java.util.LinkedHashSet<>();
        for (PatientSeed seed : patientSeeds) {
            if (approvedPatientKeys.contains(seed.key())) {
                continue;
            }

            pendingPatientKeys.add(seed.key());
            if (pendingPatientKeys.size() >= PENDING_GUARDIAN_LINK_COUNT) {
                break;
            }
        }
        return pendingPatientKeys;
    }

    private void pruneUnusedSeedGuardians(List<PatientSeed> patientSeeds, java.util.Set<String> targetPatientKeys) {
        List<String> targetGuardianUsernames = new java.util.ArrayList<>();
        for (PatientSeed seed : patientSeeds) {
            if (targetPatientKeys.contains(seed.key())) {
                targetGuardianUsernames.add(seed.guardianUsername());
            }
        }

        if (targetGuardianUsernames.isEmpty()) {
            return;
        }

        entityManager.createQuery("""
                delete from PatientGuardianLink l
                 where l.guardianUser.role = :role
                   and l.guardianUser.username like :seedGuardianUsernamePattern
                   and l.guardianUser.username not in :guardianUsernames
                """)
                .setParameter("role", Role.GUARDIAN)
                .setParameter("seedGuardianUsernamePattern", "seed_prod_guardian_%")
                .setParameter("guardianUsernames", targetGuardianUsernames)
                .executeUpdate();
        entityManager.createQuery("""
                delete from User u
                 where u.role = :role
                   and u.username like :seedGuardianUsernamePattern
                   and u.username not in :guardianUsernames
                """)
                .setParameter("role", Role.GUARDIAN)
                .setParameter("seedGuardianUsernamePattern", "seed_prod_guardian_%")
                .setParameter("guardianUsernames", targetGuardianUsernames)
                .executeUpdate();
        entityManager.flush();
    }

    private void resetSeedClinicalArtifacts(Map<String, Patient> patientsByKey,
            Map<String, DoctorProfile> doctorsByUsername) {
        List<Long> patientIds = patientsByKey.values().stream()
                .map(Patient::getId)
                .filter(Objects::nonNull)
                .toList();
        List<Long> doctorIds = doctorsByUsername.values().stream()
                .map(DoctorProfile::getId)
                .filter(Objects::nonNull)
                .toList();
        if (patientIds.isEmpty() || doctorIds.isEmpty()) {
            return;
        }

        List<Long> bookingIds = entityManager.createQuery("""
                select b.id
                  from Booking b
                 where b.patient.id in :patientIds
                    or b.doctor.id in :doctorIds
                """, Long.class)
                .setParameter("patientIds", patientIds)
                .setParameter("doctorIds", doctorIds)
                .getResultList();
        List<Long> caseIds = entityManager.createQuery("""
                select c.id
                  from CareCase c
                 where c.patient.id in :patientIds
                    or c.doctor.id in :doctorIds
                """, Long.class)
                .setParameter("patientIds", patientIds)
                .setParameter("doctorIds", doctorIds)
                .getResultList();
        java.util.Set<Long> intakeSessionIds = new java.util.LinkedHashSet<>(entityManager.createQuery("""
                select i.id
                  from IntakeSession i
                 where i.patient.id in :patientIds
                """, Long.class)
                .setParameter("patientIds", patientIds)
                .getResultList());
        if (!bookingIds.isEmpty()) {
            intakeSessionIds.addAll(entityManager.createQuery("""
                    select distinct b.intakeSession.id
                      from Booking b
                     where b.id in :bookingIds
                       and b.intakeSession is not null
                    """, Long.class)
                    .setParameter("bookingIds", bookingIds)
                    .getResultList());
        }

        if (!caseIds.isEmpty()) {
            entityManager.createQuery("""
                    delete from DispatchOutbox d
                     where d.careCase.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
            entityManager.createQuery("""
                    delete from VitalMeasurement v
                     where v.careCase.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
            entityManager.createQuery("""
                    delete from ConsultationSummary cs
                     where cs.session.careCase.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
            entityManager.createQuery("""
                    delete from ConsultationSession s
                     where s.careCase.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
            entityManager.createQuery("""
                    delete from Mission m
                     where m.careCase.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
            entityManager.createQuery("""
                    delete from CareCase c
                     where c.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
        }

        if (!bookingIds.isEmpty()) {
            entityManager.createQuery("""
                    delete from Booking b
                     where b.id in :bookingIds
                    """)
                    .setParameter("bookingIds", bookingIds)
                    .executeUpdate();
        }

        deleteOrphanIntakeSessions(intakeSessionIds);

        entityManager.createQuery("""
                delete from ScheduleSlot s
                 where s.doctor.id in :doctorIds
                """)
                .setParameter("doctorIds", doctorIds)
                .executeUpdate();

        entityManager.flush();
    }

    private void pruneUpcomingSeedArtifacts(LocalDate upcomingStart, LocalDate upcomingEnd,
            List<PatientSeed> patientSeeds, Map<String, Patient> patientsByKey) {
        List<Long> targetPatientIds = patientSeeds.stream()
                .map(PatientSeed::key)
                .map(patientsByKey::get)
                .filter(Objects::nonNull)
                .map(Patient::getId)
                .filter(Objects::nonNull)
                .toList();
        if (targetPatientIds.isEmpty()) {
            return;
        }

        List<Long> bookingIds = entityManager.createQuery("""
                select b.id
                  from Booking b
                 where b.patient.id in :patientIds
                   and b.channel = :channel
                   and b.appointmentDate >= :upcomingStart
                   and b.appointmentDate <= :upcomingEnd
                """, Long.class)
                .setParameter("patientIds", targetPatientIds)
                .setParameter("channel", WEB_SIMULATOR_CHANNEL)
                .setParameter("upcomingStart", upcomingStart)
                .setParameter("upcomingEnd", upcomingEnd)
                .getResultList();
        if (bookingIds.isEmpty()) {
            return;
        }

        List<Long> caseIds = entityManager.createQuery("""
                select c.id
                  from CareCase c
                 where c.booking.id in :bookingIds
                """, Long.class)
                .setParameter("bookingIds", bookingIds)
                .getResultList();
        List<Long> slotIds = entityManager.createQuery("""
                select distinct b.slot.id
                  from Booking b
                 where b.id in :bookingIds
                """, Long.class)
                .setParameter("bookingIds", bookingIds)
                .getResultList();
        List<Long> intakeSessionIds = entityManager.createQuery("""
                select distinct b.intakeSession.id
                  from Booking b
                 where b.id in :bookingIds
                   and b.intakeSession is not null
                """, Long.class)
                .setParameter("bookingIds", bookingIds)
                .getResultList();

        if (!caseIds.isEmpty()) {
            entityManager.createQuery("""
                    delete from DispatchOutbox d
                     where d.careCase.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
            entityManager.createQuery("""
                    delete from ConsultationSession s
                     where s.careCase.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
            entityManager.createQuery("""
                    delete from Mission m
                     where m.careCase.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
            entityManager.createQuery("""
                    delete from CareCase c
                     where c.id in :caseIds
                    """)
                    .setParameter("caseIds", caseIds)
                    .executeUpdate();
        }

        entityManager.createQuery("""
                delete from Booking b
                 where b.id in :bookingIds
                """)
                .setParameter("bookingIds", bookingIds)
                .executeUpdate();

        if (!slotIds.isEmpty()) {
            entityManager.createQuery("""
                    update ScheduleSlot s
                       set s.booked = false
                     where s.id in :slotIds
                    """)
                    .setParameter("slotIds", slotIds)
                    .executeUpdate();
        }

        deleteOrphanIntakeSessions(intakeSessionIds);

        entityManager.flush();
    }

    private void deleteOrphanIntakeSessions(Collection<Long> intakeSessionIds) {
        if (intakeSessionIds.isEmpty()) {
            return;
        }

        LinkedHashSet<Long> orphanIntakeSessionIds = new LinkedHashSet<>(intakeSessionIds);
        orphanIntakeSessionIds.removeAll(entityManager.createQuery("""
                select distinct b.intakeSession.id
                  from Booking b
                 where b.intakeSession is not null
                   and b.intakeSession.id in :intakeSessionIds
                """, Long.class)
                .setParameter("intakeSessionIds", intakeSessionIds)
                .getResultList());
        orphanIntakeSessionIds.removeAll(entityManager.createQuery("""
                select distinct c.intakeSession.id
                  from CareCase c
                 where c.intakeSession is not null
                   and c.intakeSession.id in :intakeSessionIds
                """, Long.class)
                .setParameter("intakeSessionIds", intakeSessionIds)
                .getResultList());

        if (orphanIntakeSessionIds.isEmpty()) {
            return;
        }

        entityManager.createQuery("""
                delete from IntakeSession i
                 where i.id in :intakeSessionIds
                """)
                .setParameter("intakeSessionIds", orphanIntakeSessionIds)
                .executeUpdate();
    }

    private void seedFutureSlots(LocalDate today, Map<String, DoctorProfile> doctorsByUsername) {
        if (today.isAfter(FUTURE_SLOT_END_DATE)) {
            return;
        }

        LocalTime currentTime = KstTime.now().toLocalTime();
        pruneTodayFutureSeedSlots(today, currentTime, doctorsByUsername);

        for (LocalDate slotDate = today; !slotDate.isAfter(FUTURE_SLOT_END_DATE); slotDate = slotDate.plusDays(1)) {
            for (DoctorProfile doctor : doctorsByUsername.values()) {
                for (LocalTime startTime : REALISTIC_SLOT_START_TIMES) {
                    if (slotDate.isEqual(today) && !startTime.isAfter(currentTime)) {
                        continue;
                    }
                    ensureSlot(doctor, slotDate, startTime, startTime.plusMinutes(30));
                }
            }
        }
    }

    private void pruneTodayFutureSeedSlots(LocalDate today, LocalTime currentTime,
            Map<String, DoctorProfile> doctorsByUsername) {
        List<Long> doctorIds = doctorsByUsername.values().stream()
                .map(DoctorProfile::getId)
                .filter(Objects::nonNull)
                .toList();
        if (doctorIds.isEmpty()) {
            return;
        }

        entityManager.createQuery("""
                delete from ScheduleSlot s
                 where s.doctor.id in :doctorIds
                   and s.slotDate = :slotDate
                   and s.booked = false
                   and s.startTime <= :currentTime
                """)
                .setParameter("doctorIds", doctorIds)
                .setParameter("slotDate", today)
                .setParameter("currentTime", currentTime)
                .executeUpdate();
        entityManager.flush();
    }

    private User ensureUser(String username, String name, Role role, User approver, ApprovalStatus targetStatus) {
        User user = userRepository.findByUsername(username).orElseGet(() -> userRepository.save(
                User.builder()
                        .username(username)
                        .passwordHash(passwordEncoder.encode(defaultPassword))
                        .name(name)
                        .role(role)
                        .build()));
        syncSeedUserPassword(user);
        syncUserBasics(user, name, role);
        syncApprovalStatus(user, approver, targetStatus);
        return user;
    }

    private void syncSeedUserPassword(User user) {
        String currentPasswordHash = user.getPasswordHash();
        boolean passwordMatches = false;
        if (currentPasswordHash != null) {
            try {
                passwordMatches = passwordEncoder.matches(defaultPassword, currentPasswordHash);
            } catch (IllegalArgumentException ex) {
                log.warn("Seed user password hash is unreadable. Replacing with app.seed.default-password. username={}",
                        user.getUsername());
            }
        }

        if (passwordMatches) {
            return;
        }

        user.changePasswordHash(passwordEncoder.encode(defaultPassword));
        entityManager.flush();
        log.info("Seed user password synced from app.seed.default-password. username={}", user.getUsername());
    }

    private void syncUserBasics(User user, String name, Role role) {
        if (Objects.equals(user.getName(), name) && user.getRole() == role) {
            return;
        }

        entityManager.createQuery("""
                update User u
                   set u.name = :name,
                       u.role = :role
                 where u.id = :id
                """)
                .setParameter("name", name)
                .setParameter("role", role)
                .setParameter("id", user.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.refresh(user);
    }

    private void syncApprovalStatus(User user, User approver, ApprovalStatus targetStatus) {
        if (user.getApprovalStatus() == targetStatus) {
            return;
        }
        LocalDateTime now = KstTime.now();
        if (targetStatus == ApprovalStatus.APPROVED) {
            user.approve(approver, now);
            return;
        }
        if (targetStatus == ApprovalStatus.REJECTED) {
            user.reject(approver, now);
            return;
        }

        entityManager.createQuery("""
                update User u
                   set u.active = false,
                       u.approvalStatus = :approvalStatus,
                       u.approvedByUser = null,
                       u.approvedAt = null
                 where u.id = :id
                """)
                .setParameter("approvalStatus", ApprovalStatus.PENDING)
                .setParameter("id", user.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.refresh(user);
    }

    private DoctorProfile ensureDoctorProfile(User user, String department, String departmentName) {
        DoctorProfile doctorProfile = doctorProfileRepository.findByUserUsername(user.getUsername())
                .orElseGet(() -> doctorProfileRepository.save(
                        DoctorProfile.builder()
                                .user(user)
                                .department(department)
                                .departmentName(departmentName)
                                .build()));

        if (!Objects.equals(doctorProfile.getDepartment(), department)
                || !Objects.equals(doctorProfile.getDepartmentName(), departmentName)) {
            entityManager.createQuery("""
                    update DoctorProfile d
                       set d.department = :department,
                           d.departmentName = :departmentName
                     where d.id = :id
                    """)
                    .setParameter("department", department)
                    .setParameter("departmentName", departmentName)
                    .setParameter("id", doctorProfile.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.refresh(doctorProfile);
        }

        return doctorProfile;
    }

    private Vehicle ensureVehicle(VehicleSeed seed) {
        vehicleRepository.findByRegionCodeAndIsActiveTrue(seed.regionCode())
                .filter(existing -> !Objects.equals(existing.getCode(), seed.code()))
                .ifPresent(existing -> {
                    throw new IllegalStateException(
                            "Active vehicle conflict for region %s: %s".formatted(seed.regionCode(),
                                    existing.getCode()));
                });

        Vehicle vehicle = vehicleRepository.findByCode(seed.code()).orElseGet(() -> vehicleRepository.save(
                Vehicle.builder()
                        .code(seed.code())
                        .regionCode(seed.regionCode())
                        .displayName(seed.displayName())
                        .isActive(true)
                        .operationalStatus(OperationalStatus.OPERATIONAL)
                        .statusChangedAt(KstTime.now())
                        .build()));

        if (!Objects.equals(vehicle.getPublicId(), seed.publicId())
                || !Objects.equals(vehicle.getRegionCode(), seed.regionCode())
                || !Objects.equals(vehicle.getDisplayName(), seed.displayName())
                || !vehicle.isActive()
                || vehicle.getOperationalStatus() != OperationalStatus.OPERATIONAL
                || vehicle.getStatusReason() != null) {
            entityManager.createQuery("""
                    update Vehicle v
                       set v.publicId = :publicId,
                           v.regionCode = :regionCode,
                           v.displayName = :displayName,
                           v.isActive = true,
                           v.operationalStatus = :operationalStatus,
                           v.statusChangedAt = :statusChangedAt,
                           v.statusReason = null
                     where v.id = :id
                    """)
                    .setParameter("publicId", seed.publicId())
                    .setParameter("regionCode", seed.regionCode())
                    .setParameter("displayName", seed.displayName())
                    .setParameter("operationalStatus", OperationalStatus.OPERATIONAL)
                    .setParameter("statusChangedAt", KstTime.now())
                    .setParameter("id", vehicle.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.refresh(vehicle);
        }

        return vehicle;
    }

    private Patient ensurePatient(String name, LocalDate birthDate, PatientGender gender, String regionCode,
            String address, String phone, String referenceImagePath, User uploadedBy) {
        String birthDate6 = birthDate.format(BIRTH_DATE6_FORMAT);
        Patient patient = patientRepository.findByPhone(phone)
                .or(() -> (referenceImagePath == null
                        ? java.util.Optional.<Patient>empty()
                        : patientRepository.findAllByReferenceImagePathOrderByIdAsc(referenceImagePath).stream()
                                .findFirst()))
                .or(() -> patientRepository.findAllByNameAndBirthDate6(name, birthDate6).stream().findFirst())
                .orElseGet(() -> patientRepository.save(
                        Patient.builder()
                                .name(name)
                                .birthDate(birthDate)
                                .gender(gender)
                                .regionCode(regionCode)
                                .address(address)
                                .phone(phone)
                                .build()));

        if (!Objects.equals(patient.getName(), name)
                || !Objects.equals(patient.getBirthDate(), birthDate)
                || patient.getGender() != gender
                || !Objects.equals(patient.getRegionCode(), regionCode)
                || !Objects.equals(patient.getAddress(), address)
                || !Objects.equals(patient.getPhone(), phone)) {
            patient.updateProfile(name, birthDate, gender, regionCode, address, phone);
        }

        if (referenceImagePath != null
                && (!patient.hasReferenceImage()
                        || !Objects.equals(patient.getReferenceImagePath(), referenceImagePath))) {
            patient.updateReferenceImage(referenceImagePath, uploadedBy, KstTime.now());
        }

        return patient;
    }

    private void ensureGuardianLink(Patient patient, User guardianUser, String relation,
            GuardianLinkStatus targetStatus,
            User approver) {
        PatientGuardianLink link = patientGuardianLinkRepository.findByPatientAndGuardianUser(patient, guardianUser)
                .orElseGet(() -> patientGuardianLinkRepository.save(
                        PatientGuardianLink.builder()
                                .patient(patient)
                                .guardianUser(guardianUser)
                                .relation(relation)
                                .build()));

        if (!Objects.equals(link.getRelation(), relation)) {
            entityManager.createQuery("""
                    update PatientGuardianLink l
                       set l.relation = :relation
                     where l.id = :id
                    """)
                    .setParameter("relation", relation)
                    .setParameter("id", link.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.refresh(link);
        }

        if (targetStatus == GuardianLinkStatus.APPROVED && link.getStatus() != GuardianLinkStatus.APPROVED) {
            link.approve(approver, KstTime.now());
            return;
        }
        if (targetStatus == GuardianLinkStatus.REJECTED && link.getStatus() != GuardianLinkStatus.REJECTED) {
            link.reject(approver, KstTime.now());
            return;
        }
        if (targetStatus == GuardianLinkStatus.PENDING && link.getStatus() != GuardianLinkStatus.PENDING) {
            entityManager.createQuery("""
                    update PatientGuardianLink l
                       set l.status = :status,
                           l.approvedByUser = null,
                           l.approvedAt = null
                     where l.id = :id
                    """)
                    .setParameter("status", GuardianLinkStatus.PENDING)
                    .setParameter("id", link.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.refresh(link);
        }
    }

    private IntakeSession ensureIntakeSession(Patient patient, String callerNumber, IntakeChannel channel,
            String department, String departmentName, String selectionReason, List<String> offeredSlotIds,
            CompletionReason completionReason) {
        return ensureIntakeSession(
                null,
                patient,
                callerNumber,
                channel,
                department,
                departmentName,
                selectionReason,
                offeredSlotIds,
                completionReason);
    }

    private IntakeSession ensureIntakeSession(String intakePublicId, Patient patient, String callerNumber,
            IntakeChannel channel, String department, String departmentName, String selectionReason,
            List<String> offeredSlotIds,
            CompletionReason completionReason) {
        IntakeSession intakeSession = intakePublicId == null
                ? intakeSessionRepository.findFirstByCallerNumberAndChannelOrderByIdAsc(callerNumber, channel)
                        .orElseGet(() -> intakeSessionRepository.save(
                                IntakeSession.builder()
                                        .patient(patient)
                                        .callerNumber(callerNumber)
                                        .channel(channel)
                                        .build()))
                : intakeSessionRepository.findByPublicId(intakePublicId)
                        .orElseGet(() -> intakeSessionRepository.save(
                                IntakeSession.builder()
                                        .patient(patient)
                                        .callerNumber(callerNumber)
                                        .channel(channel)
                                        .build()));

        if (intakePublicId != null && !Objects.equals(intakeSession.getPublicId(), intakePublicId)) {
            entityManager.createQuery("""
                    update IntakeSession i
                       set i.publicId = :publicId
                     where i.id = :id
                    """)
                    .setParameter("publicId", intakePublicId)
                    .setParameter("id", intakeSession.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.refresh(intakeSession);
        }

        if (intakeSession.getChannel() != channel) {
            entityManager.createQuery("""
                    update IntakeSession i
                       set i.channel = :channel
                     where i.id = :id
                    """)
                    .setParameter("channel", channel)
                    .setParameter("id", intakeSession.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.refresh(intakeSession);
        }

        if (!Objects.equals(intakeSession.getCallerNumber(), callerNumber)) {
            entityManager.createQuery("""
                    update IntakeSession i
                       set i.callerNumber = :callerNumber
                     where i.id = :id
                    """)
                    .setParameter("callerNumber", callerNumber)
                    .setParameter("id", intakeSession.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.refresh(intakeSession);
        }

        if (intakeSession.getPatient() == null
                || !Objects.equals(intakeSession.getPatient().getId(), patient.getId())) {
            intakeSession.bindPatient(patient, KstTime.now());
        }

        intakeSession.recordSelection(department, departmentName, ConfidenceLevel.HIGH, false, selectionReason,
                offeredSlotIds, KstTime.now());
        if (intakeSession.isActive()) {
            intakeSession.complete(completionReason, KstTime.now());
        }
        return intakeSession;
    }

    private void syncIntakeSessionTimeline(IntakeSession intakeSession, LocalDateTime lastActivityAt,
            LocalDateTime endedAt, CompletionReason completionReason) {
        entityManager.createQuery("""
                update IntakeSession i
                   set i.status = :status,
                       i.completionReason = :completionReason,
                       i.lastActivityAt = :lastActivityAt,
                       i.selectionUpdatedAt = :lastActivityAt,
                       i.endedAt = :endedAt
                 where i.id = :id
                """)
                .setParameter("status", IntakeStatus.COMPLETED)
                .setParameter("completionReason", completionReason)
                .setParameter("lastActivityAt", lastActivityAt)
                .setParameter("endedAt", endedAt)
                .setParameter("id", intakeSession.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.refresh(intakeSession);
    }

    private ScheduleSlot ensureSlot(DoctorProfile doctor, LocalDate slotDate, LocalTime startTime, LocalTime endTime) {
        ScheduleSlot slot = scheduleSlotRepository
                .findByDoctorUserUsernameAndSlotDateAndStartTime(doctor.getUser().getUsername(), slotDate, startTime)
                .orElseGet(() -> scheduleSlotRepository.save(
                        ScheduleSlot.builder()
                                .doctor(doctor)
                                .slotDate(slotDate)
                                .startTime(startTime)
                                .endTime(endTime)
                                .build()));

        if (!Objects.equals(slot.getEndTime(), endTime)) {
            entityManager.createQuery("""
                    update ScheduleSlot s
                       set s.endTime = :endTime
                     where s.id = :id
                    """)
                    .setParameter("endTime", endTime)
                    .setParameter("id", slot.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.refresh(slot);
        }
        return slot;
    }

    private Booking ensureBooking(Patient patient, ScheduleSlot slot, String channel, BookingStatus targetStatus,
            IntakeSession intakeSession, String cancelReason) {
        Booking booking = bookingRepository.findBySlot(slot).orElseGet(() -> bookingRepository.save(
                Booking.builder()
                        .patient(patient)
                        .intakeSession(intakeSession)
                        .slot(slot)
                        .doctor(slot.getDoctor())
                        .channel(channel)
                        .appointmentDate(slot.getSlotDate())
                        .startTime(slot.getStartTime())
                        .endTime(slot.getEndTime())
                        .build()));

        if (targetStatus == BookingStatus.CANCELLED) {
            slot.markAvailable();
        } else if (!slot.isBooked()) {
            slot.markBooked();
        }

        entityManager.createQuery("""
                update Booking b
                   set b.patient = :patient,
                       b.intakeSession = :intakeSession,
                       b.slot = :slot,
                       b.doctor = :doctor,
                       b.channel = :channel,
                       b.appointmentDate = :appointmentDate,
                       b.startTime = :startTime,
                       b.endTime = :endTime,
                       b.status = :status,
                       b.cancelReason = :cancelReason,
                       b.cancelledAt = :cancelledAt
                 where b.id = :id
                """)
                .setParameter("patient", patient)
                .setParameter("intakeSession", intakeSession)
                .setParameter("slot", slot)
                .setParameter("doctor", slot.getDoctor())
                .setParameter("channel", channel)
                .setParameter("appointmentDate", slot.getSlotDate())
                .setParameter("startTime", slot.getStartTime())
                .setParameter("endTime", slot.getEndTime())
                .setParameter("status", targetStatus)
                .setParameter("cancelReason", targetStatus == BookingStatus.CANCELLED ? cancelReason : null)
                .setParameter("cancelledAt", null)
                .setParameter("id", booking.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.refresh(booking);
        return booking;
    }

    private CareCase ensureCareCase(Booking booking, IntakeSession intakeSession) {
        CareCase careCase = careCaseRepository.findByBooking(booking).orElseGet(() -> careCaseRepository.save(
                CareCase.builder()
                        .booking(booking)
                        .patient(booking.getPatient())
                        .doctor(booking.getDoctor())
                        .intakeSession(intakeSession)
                        .build()));

        entityManager.createQuery("""
                update CareCase c
                   set c.booking = :booking,
                       c.patient = :patient,
                       c.doctor = :doctor,
                       c.intakeSession = :intakeSession
                 where c.id = :id
                """)
                .setParameter("booking", booking)
                .setParameter("patient", booking.getPatient())
                .setParameter("doctor", booking.getDoctor())
                .setParameter("intakeSession", intakeSession)
                .setParameter("id", careCase.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.refresh(careCase);
        return careCase;
    }

    private DispatchOutbox ensureDispatchOutbox(CareCase careCase, String regionCode, String destination) {
        DispatchOutbox outbox = dispatchOutboxRepository.findWithPatientByCareCasePublicId(careCase.getPublicId())
                .orElseGet(() -> dispatchOutboxRepository.save(DispatchOutbox.builder()
                        .careCase(careCase)
                        .regionCode(regionCode)
                        .destination(destination)
                        .build()));

        entityManager.createQuery("""
                update DispatchOutbox d
                   set d.careCase = :careCase,
                       d.regionCode = :regionCode,
                       d.destination = :destination
                 where d.id = :id
                """)
                .setParameter("careCase", careCase)
                .setParameter("regionCode", regionCode)
                .setParameter("destination", destination)
                .setParameter("id", outbox.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.refresh(outbox);
        return outbox;
    }

    private void syncDispatchOutboxState(DispatchOutbox outbox, DispatchOutboxStatus targetStatus) {
        entityManager.createQuery("""
                update DispatchOutbox d
                   set d.status = :status
                 where d.id = :id
                """)
                .setParameter("status", targetStatus)
                .setParameter("id", outbox.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.refresh(outbox);
    }

    private void syncCaseStatus(CareCase careCase, CaseStatus targetStatus) {
        if (careCase.getStatus() == targetStatus) {
            return;
        }

        entityManager.createQuery("""
                update CareCase c
                   set c.status = :status
                 where c.id = :id
                """)
                .setParameter("status", targetStatus)
                .setParameter("id", careCase.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.refresh(careCase);
    }

    private Mission ensureMission(CareCase careCase, String vehicleId, String destination, LocalDateTime dispatchedAt,
            LocalDateTime estimatedArrivalTime, Integer targetWaypointNumber) {
        Mission mission = missionRepository.findByCareCase(careCase).orElseGet(() -> missionRepository.save(
                Mission.builder()
                        .careCase(careCase)
                        .vehicleId(vehicleId)
                        .destination(destination)
                        .dispatchedAt(dispatchedAt)
                        .estimatedArrivalTime(estimatedArrivalTime)
                        .targetWaypointNumber(targetWaypointNumber)
                        .build()));

        if (!Objects.equals(mission.getVehicleId(), vehicleId)
                || !Objects.equals(mission.getDestination(), destination)
                || !Objects.equals(mission.getDispatchedAt(), dispatchedAt)
                || !Objects.equals(mission.getEstimatedArrivalTime(), estimatedArrivalTime)
                || !Objects.equals(mission.getTargetWaypointNumber(), targetWaypointNumber)) {
            entityManager.createQuery("""
                    update Mission m
                       set m.vehicleId = :vehicleId,
                           m.destination = :destination,
                           m.dispatchedAt = :dispatchedAt,
                           m.estimatedArrivalTime = :estimatedArrivalTime,
                           m.targetWaypointNumber = :targetWaypointNumber
                     where m.id = :id
                    """)
                    .setParameter("vehicleId", vehicleId)
                    .setParameter("destination", destination)
                    .setParameter("dispatchedAt", dispatchedAt)
                    .setParameter("estimatedArrivalTime", estimatedArrivalTime)
                    .setParameter("targetWaypointNumber", targetWaypointNumber)
                    .setParameter("id", mission.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.refresh(mission);
        }
        return mission;
    }

    private void syncMissionState(Mission mission, String vehicleId, String destination, LocalDateTime dispatchedAt,
            LocalDateTime estimatedArrivalTime, MissionPhase phase, MissionPhase previousPhase, BigDecimal latitude,
            BigDecimal longitude, LocalDateTime completedAt, Integer targetWaypointNumber) {
        entityManager.createQuery("""
                update Mission m
                   set m.vehicleId = :vehicleId,
                       m.destination = :destination,
                       m.dispatchedAt = :dispatchedAt,
                       m.estimatedArrivalTime = :estimatedArrivalTime,
                       m.targetWaypointNumber = :targetWaypointNumber,
                       m.phase = :phase,
                       m.previousPhase = :previousPhase,
                       m.latitude = :latitude,
                       m.longitude = :longitude,
                       m.completedAt = :completedAt,
                       m.lastTelemetrySourceEventId = null,
                       m.lastTelemetrySeqNo = null,
                       m.lastTelemetryAt = null
                 where m.id = :id
                """)
                .setParameter("vehicleId", vehicleId)
                .setParameter("destination", destination)
                .setParameter("dispatchedAt", dispatchedAt)
                .setParameter("estimatedArrivalTime", estimatedArrivalTime)
                .setParameter("targetWaypointNumber", targetWaypointNumber)
                .setParameter("phase", phase)
                .setParameter("previousPhase", previousPhase)
                .setParameter("latitude", latitude)
                .setParameter("longitude", longitude)
                .setParameter("completedAt", completedAt)
                .setParameter("id", mission.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.refresh(mission);
    }

    private ConsultationSession ensureConsultationSession(CareCase careCase, String roomId, String livekitUrlValue) {
        ConsultationSession session = consultationSessionRepository.findByCareCase(careCase)
                .orElseGet(() -> consultationSessionRepository.save(
                        ConsultationSession.builder()
                                .careCase(careCase)
                                .roomId(roomId)
                                .livekitUrl(livekitUrlValue)
                                .build()));

        if (!Objects.equals(session.getRoomId(), roomId) || !Objects.equals(session.getLivekitUrl(), livekitUrlValue)) {
            entityManager.createQuery("""
                    update ConsultationSession s
                       set s.roomId = :roomId,
                           s.livekitUrl = :livekitUrl
                     where s.id = :id
                    """)
                    .setParameter("roomId", roomId)
                    .setParameter("livekitUrl", livekitUrlValue)
                    .setParameter("id", session.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.refresh(session);
        }
        return session;
    }

    private void syncConsultationSessionState(ConsultationSession session, ConsultationSessionStatus targetStatus,
            LocalDateTime joinedAt, LocalDateTime startedAt, LocalDateTime endedAt, Integer durationMinutes) {
        ConnectionState doctorState = targetStatus == ConsultationSessionStatus.IN_PROGRESS
                ? ConnectionState.CONNECTED
                : ConnectionState.DISCONNECTED;
        ConnectionState patientState = targetStatus == ConsultationSessionStatus.IN_PROGRESS
                ? ConnectionState.CONNECTED
                : ConnectionState.DISCONNECTED;

        entityManager.createQuery("""
                update ConsultationSession s
                   set s.status = :status,
                       s.roomId = :roomId,
                       s.livekitUrl = :livekitUrl,
                       s.doctorConnectionState = :doctorState,
                       s.patientConnectionState = :patientState,
                       s.doctorJoinedAt = :doctorJoinedAt,
                       s.patientJoinedAt = :patientJoinedAt,
                       s.startedAt = :startedAt,
                       s.endedAt = :endedAt,
                       s.durationMinutes = :durationMinutes
                 where s.id = :id
                """)
                .setParameter("status", targetStatus)
                .setParameter("roomId", session.getRoomId())
                .setParameter("livekitUrl", session.getLivekitUrl())
                .setParameter("doctorState", doctorState)
                .setParameter("patientState", patientState)
                .setParameter("doctorJoinedAt", joinedAt)
                .setParameter("patientJoinedAt", joinedAt == null ? null : joinedAt.plusMinutes(1))
                .setParameter("startedAt", startedAt)
                .setParameter("endedAt", endedAt)
                .setParameter("durationMinutes", durationMinutes)
                .setParameter("id", session.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.refresh(session);
    }

    private ConsultationSummary ensureConsultationSummary(ConsultationSession session, String summaryNote,
            boolean prescriptionIssued, String prescriptionNote, boolean needsFollowUp) {
        ConsultationSummary summary = consultationSummaryRepository.findBySession(session)
                .orElseGet(() -> consultationSummaryRepository.save(
                        ConsultationSummary.builder()
                                .session(session)
                                .summaryNote(summaryNote)
                                .prescriptionIssued(prescriptionIssued)
                                .prescriptionNote(prescriptionNote)
                                .needsFollowUp(needsFollowUp)
                                .build()));

        if (!Objects.equals(summary.getSummaryNote(), summaryNote)
                || summary.isPrescriptionIssued() != prescriptionIssued
                || !Objects.equals(summary.getPrescriptionNote(), prescriptionNote)
                || summary.isNeedsFollowUp() != needsFollowUp) {
            summary.update(summaryNote, prescriptionIssued, prescriptionNote, needsFollowUp);
        }
        return summary;
    }

    private Patient getPatient(Map<String, Patient> patientsByKey, String patientKey) {
        Patient patient = patientsByKey.get(patientKey);
        if (patient == null) {
            throw new IllegalStateException("Seed patient not found: " + patientKey);
        }
        return patient;
    }

    private DoctorProfile getDoctor(Map<String, DoctorProfile> doctorsByUsername, String username) {
        DoctorProfile doctor = doctorsByUsername.get(username);
        if (doctor == null) {
            throw new IllegalStateException("Seed doctor not found: " + username);
        }
        return doctor;
    }

    private Vehicle getVehicle(Map<String, Vehicle> vehiclesByCode, String vehicleCode) {
        Vehicle vehicle = vehiclesByCode.get(vehicleCode);
        if (vehicle == null) {
            throw new IllegalStateException("Seed vehicle not found: " + vehicleCode);
        }
        return vehicle;
    }

    private String resolveSeedLivekitUrl() {
        return livekitUrl == null || livekitUrl.isBlank() ? LIVEKIT_URL_PLACEHOLDER : livekitUrl;
    }

    private static String buildWaypointSuffix(int waypointNumber) {
        return "%03d".formatted(waypointNumber);
    }

    private static String buildWaypointPhone(int waypointNumber) {
        return "01000000" + buildWaypointSuffix(waypointNumber);
    }

    private static String buildGuardianUsername(int waypointNumber) {
        return "seed_prod_guardian_wp_" + buildWaypointSuffix(waypointNumber);
    }

    private static List<PatientSeed> buildPriorityPatientSeeds() {
        return List.of(
                new PatientSeed(
                        PRIMARY_PATIENT_KEY,
                        PRIMARY_PATIENT_NAME,
                        LocalDate.of(1958, 10, 6),
                        PatientGender.MALE,
                        "GIMCHEON",
                        PRIMARY_PATIENT_ADDRESS,
                        PRIMARY_PATIENT_PHONE,
                        "patients/pat_prd_gim_waypoint59/reference.jpg",
                        59,
                        buildGuardianUsername(59),
                        "권현우",
                        DEFAULT_GUARDIAN_RELATION),
                new PatientSeed(
                        "gim_wp_092",
                        "정지환",
                        LocalDate.of(1960, 9, 2),
                        PatientGender.MALE,
                        "GIMCHEON",
                        "경상북도 김천시 증산면 유성길 6-9",
                        buildWaypointPhone(92),
                        "patients/pat_prd_gim_waypoint92/reference.jpg",
                        92,
                        buildGuardianUsername(92),
                        "정소윤",
                        DEFAULT_GUARDIAN_RELATION),
                new PatientSeed(
                        "gim_wp_142",
                        "김주덕",
                        LocalDate.of(1955, 1, 24),
                        PatientGender.MALE,
                        "GIMCHEON",
                        "경상북도 김천시 증산면 유성길 2길 7",
                        buildWaypointPhone(142),
                        "patients/pat_prd_gim_waypoint142/reference.jpg",
                        142,
                        buildGuardianUsername(142),
                        "김현수",
                        DEFAULT_GUARDIAN_RELATION));
    }

    private static List<PatientSeed> buildPriorityPatientSeedsLegacy() {
        return List.of(
                new PatientSeed(
                        "gim_wp_059",
                        "권미정",
                        LocalDate.of(1958, 10, 6),
                        PatientGender.FEMALE,
                        "GIMCHEON",
                        "경상북도 김천시 증산면 장전4길 14",
                        "01042488119",
                        "patients/pat_prd_gim_waypoint59/reference.jpg",
                        59,
                        buildGuardianUsername(59),
                        "권현우",
                        DEFAULT_GUARDIAN_RELATION),
                new PatientSeed(
                        "gim_wp_092",
                        "정지환",
                        LocalDate.of(1960, 9, 2),
                        PatientGender.MALE,
                        "GIMCHEON",
                        "경상북도 김천시 증산면 장전3길 6-9",
                        "01049163720",
                        "patients/pat_prd_gim_waypoint92/reference.jpg",
                        92,
                        buildGuardianUsername(92),
                        "정소연",
                        DEFAULT_GUARDIAN_RELATION),
                new PatientSeed(
                        "gim_wp_142",
                        "이복순",
                        LocalDate.of(1955, 1, 24),
                        PatientGender.FEMALE,
                        "GIMCHEON",
                        "경상북도 김천시 증산면 장전2길 7",
                        buildWaypointPhone(142),
                        "patients/pat_prd_gim_waypoint142/reference.jpg",
                        142,
                        buildGuardianUsername(142),
                        "정은희",
                        DEFAULT_GUARDIAN_RELATION));
    }

    private static String buildSyntheticPatientName(int waypointNumber) {
        int zeroBased = Math.max(0, waypointNumber - 1);
        List<String> givenNames = waypointNumber % 2 == 0
                ? SYNTHETIC_FEMALE_PATIENT_GIVEN_NAMES
                : SYNTHETIC_MALE_PATIENT_GIVEN_NAMES;
        return buildSyntheticFullName(zeroBased, givenNames, 7);
    }

    private static String buildSyntheticGuardianName(int waypointNumber) {
        return buildSyntheticFullName(Math.max(0, waypointNumber + 36), SYNTHETIC_GUARDIAN_GIVEN_NAMES, 11);
    }

    private static String buildSyntheticFullName(int seed, List<String> givenNames, int stride) {
        int normalizedSeed = Math.max(0, seed);
        String lastName = SYNTHETIC_LAST_NAMES.get(normalizedSeed % SYNTHETIC_LAST_NAMES.size());
        int givenNameIndex = (normalizedSeed * stride + (normalizedSeed / SYNTHETIC_LAST_NAMES.size()))
                % givenNames.size();
        return lastName + givenNames.get(givenNameIndex);
    }

    private static String buildSyntheticWaypointAddress(int waypointNumber) {
        int zeroBased = Math.max(0, waypointNumber - 1);
        String roadName = SYNTHETIC_ROAD_NAMES.get(zeroBased % SYNTHETIC_ROAD_NAMES.size());
        int buildingNumber = (zeroBased / SYNTHETIC_ROAD_NAMES.size()) + SYNTHETIC_ADDRESS_BUILDING_NUMBER_OFFSET;
        return "경상북도 김천시 증산면 " + roadName + " " + buildingNumber;
    }

    private static List<LocalTime> buildRealisticSlotStartTimes() {
        List<LocalTime> slotStartTimes = new java.util.ArrayList<>();
        for (LocalTime startTime = REALISTIC_SLOT_OPEN_TIME; !startTime.isAfter(REALISTIC_SLOT_CLOSE_TIME); startTime =
                startTime.plusMinutes(30)) {
            slotStartTimes.add(startTime);
        }
        return List.copyOf(slotStartTimes);
    }

    private static String buildTopologyWaypointAddress(int waypointNumber) {
        return TOPOLOGY_ADDRESS_PREFIX + waypointNumber;
    }

    private record HistoricalBookingPlan(PatientSeed patientSeed, String doctorUsername, int visitSequence) {
    }

    private record HistoricalAppointmentSlot(LocalDate appointmentDate, LocalTime startTime) {
    }

    private record DoctorSeed(String username, String name, String department, String departmentName) {
    }

    private record VehicleSeed(String publicId, String code, String regionCode, String displayName) {
    }

    private record PatientSeed(String key, String name, LocalDate birthDate, PatientGender gender, String regionCode,
            String address, String phone, String referenceImagePath, Integer waypointNumber, String guardianUsername,
            String guardianName, String guardianRelation) {
    }
}
