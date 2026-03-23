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
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
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

    private static final String DEFAULT_CHANNEL = "PHONE";
    private static final String ADMIN_USERNAME = "seed_prod_admin";
    private static final String ADMIN_NAME = "운영 더미 관리자";
    private static final String GUARDIAN_USERNAME = "seed_prod_guardian_01";
    private static final String GUARDIAN_NAME = "김보호";
    private static final String LIVEKIT_URL_PLACEHOLDER = "__SET_LIVEKIT_URL__";
    private static final DateTimeFormatter BIRTH_DATE6_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");
    private static final LocalDate FUTURE_SLOT_END_DATE = LocalDate.of(2026, 4, 13);

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

    private static final List<PatientSeed> PATIENT_SEEDS = List.of(
            new PatientSeed("gim_sudo01", "홍길동", LocalDate.of(1911, 11, 11), PatientGender.MALE, "GIMCHEON",
                    "경북 김천시 증산면 수도리 수도길 865-13-1438", "01011111111", "patients/pat_prd_gim_sudo01/reference.jpg"),
            new PatientSeed("gim_hwang01", "김철수", LocalDate.of(1968, 4, 15), PatientGender.MALE, "GIMCHEON",
                    "경북 김천시 증산면 황점리 황점1길 70-100-807", "01049163720", "patients/pat_prd_gim_hwang01/reference.jpg"),
            new PatientSeed("gim_jirye01", "박영수", LocalDate.of(1957, 9, 3), PatientGender.MALE, "GIMCHEON",
                    "경북 김천시 지례면 박곡리 지례예술촌길 390-427", "01033333333", "patients/pat_prd_gim_jirye01/reference.jpg"),
            new PatientSeed("andong01", "안순자", LocalDate.of(1954, 2, 18), PatientGender.FEMALE, "ANDONG",
                    "경북 안동시 임동면 사월리(보마골) 한절골길 356-384", "01044444444", "patients/pat_prd_andong01/reference.jpg"),
            new PatientSeed("yj_marak01", "최복례", LocalDate.of(1952, 8, 21), PatientGender.FEMALE, "YEONGJU",
                    "경북 영주시 단산면 마락리 영단로 1236-1-1522-4", "01055555555", "patients/pat_prd_yj_marak01/reference.jpg"),
            new PatientSeed("yj_nam01", "이영희", LocalDate.of(1948, 3, 17), PatientGender.FEMALE, "YEONGJU",
                    "경북 영주시 부석면 남대리 영부로 847-3-1199-24(남대리)", "01066666666", "patients/pat_prd_yj_nam01/reference.jpg"),
            new PatientSeed("yj_nam02", "정말자", LocalDate.of(1959, 12, 9), PatientGender.FEMALE, "YEONGJU",
                    "경북 영주시 부석면 남대리 영부로890번길 17-236(남대리)", "01077777777", "patients/pat_prd_yj_nam02/reference.jpg"),
            new PatientSeed("sj_oeseo01", "장영호", LocalDate.of(1961, 7, 26), PatientGender.MALE, "SANGJU",
                    "경북 상주시 외서면 대전2리 갈골 하나동1길, 하나동2길, 송죽동1길, 송죽동2길, 행복동길, 낙원동길", "01088888888",
                    "patients/pat_prd_sj_oeseo01/reference.jpg"),
            new PatientSeed("sj_euncheok01", "서금자", LocalDate.of(1950, 10, 12), PatientGender.FEMALE, "SANGJU",
                    "경북 상주시 은척면 장암2리 수예길 16-132", "01099999999", "patients/pat_prd_sj_euncheok01/reference.jpg"),
            new PatientSeed("sj_hwanam01", "윤복순", LocalDate.of(1949, 5, 30), PatientGender.FEMALE, "SANGJU",
                    "경북 상주시 화남면 동관2리 평온동관로 3791-385", "01012341234", "patients/pat_prd_sj_hwanam01/reference.jpg"),
            new PatientSeed("sj_hwanam02", "오정자", LocalDate.of(1956, 1, 8), PatientGender.FEMALE, "SANGJU",
                    "경북 상주시 화남면 동관2리 비룡동관로 967-1162", "01023452345", "patients/pat_prd_sj_hwanam02/reference.jpg"));

    private static final List<GuardianLinkSeed> GUARDIAN_LINK_SEEDS = List.of(
            new GuardianLinkSeed("gim_sudo01", "배우자"),
            new GuardianLinkSeed("gim_hwang01", "아들"),
            new GuardianLinkSeed("gim_jirye01", "딸"),
            new GuardianLinkSeed("andong01", "자부"));

    private static final List<ScenarioSeed> SCENARIO_SEEDS = List.of(
            new ScenarioSeed("gim_sudo01", "seed_prod_doc_im_01", -10, LocalTime.of(9, 0), "김천 수도리 권역 차량 단말 본인인증 검증용 예약", -70,
                    -65, CaseStatus.PREPARING,
                    new MissionSeed("GIMCHEON-01", MissionPhase.ARRIVED, MissionPhase.EN_ROUTE, "36.1115000", "128.0708000", -50, -20),
                    new ConsultationSeed(ConsultationSessionStatus.READY, "prod-room-gim-01", null, null)),
            new ScenarioSeed("gim_hwang01", "seed_prod_doc_im_02", -9, LocalTime.of(9, 30), "김천 황점리 미래 예약 조회용 예약", 0, 0, CaseStatus.CREATED, null,
                    null),
            new ScenarioSeed("gim_jirye01", "seed_prod_doc_ortho_01", -8, LocalTime.of(10, 0), "김천 지례 권역 미래 예약 조회용 예약", 0, 0, CaseStatus.CREATED,
                    null, null),
            new ScenarioSeed("andong01", "seed_prod_doc_neuro_01", -7, LocalTime.of(11, 0), "안동 임동면 본인인증 진행중 시나리오 검증용 예약", -60, -55,
                    CaseStatus.IN_PROGRESS,
                    new MissionSeed("ANDONG-01", MissionPhase.VERIFYING, MissionPhase.ARRIVED, "36.6987000", "128.8223000", -60, -25),
                    new ConsultationSeed(ConsultationSessionStatus.READY, "prod-room-andong-01", null, null)),
            new ScenarioSeed("yj_marak01", "seed_prod_doc_derm_01", -6, LocalTime.of(13, 0), "영주 단산면 active mission 검증용 예약", -50, -45,
                    CaseStatus.PREPARING,
                    new MissionSeed("YEONGJU-01", MissionPhase.ARRIVED, MissionPhase.EN_ROUTE, "36.8049000", "128.6240000", -45, -10),
                    new ConsultationSeed(ConsultationSessionStatus.READY, "prod-room-yeongju-01", null, null)),
            new ScenarioSeed("yj_nam01", "seed_prod_doc_im_02", -5, LocalTime.of(14, 0), "영주 남대리 미래 예약 조회용 예약", 0, 0, CaseStatus.CREATED, null, null),
            new ScenarioSeed("yj_nam02", "seed_prod_doc_eye_01", -4, LocalTime.of(15, 0), "영주 남대리 안과 미래 예약 조회용 예약", 0, 0, CaseStatus.CREATED, null,
                    null),
            new ScenarioSeed("sj_oeseo01", "seed_prod_doc_ortho_01", -3, LocalTime.of(16, 0), "상주 외서면 화상진료 진행중 시나리오 검증용 예약", -40, -35,
                    CaseStatus.IN_PROGRESS,
                    new MissionSeed("SANGJU-01", MissionPhase.CONSULTING, MissionPhase.VERIFYING, "36.4109000", "128.1591000", -55, -15),
                    new ConsultationSeed(ConsultationSessionStatus.IN_PROGRESS, "prod-room-sangju-01", -20, -18)),
            new ScenarioSeed("sj_euncheok01", "seed_prod_doc_im_01", -2, LocalTime.of(10, 30), "상주 은척면 미래 예약 조회용 예약", 0, 0, CaseStatus.CREATED, null,
                    null),
            new ScenarioSeed("sj_hwanam01", "seed_prod_doc_neuro_01", -1, LocalTime.of(11, 0), "상주 화남면 미래 예약 조회용 예약", 0, 0, CaseStatus.CREATED, null,
                    null),
            new ScenarioSeed("sj_hwanam02", "seed_prod_doc_derm_01", -1, LocalTime.of(11, 30), "상주 화남면 피부과 미래 예약 조회용 예약", 0, 0, CaseStatus.CREATED,
                    null, null));

    private static final List<FutureSlotSeed> FUTURE_SLOT_SEEDS = List.of(
            new FutureSlotSeed("seed_prod_doc_im_01", LocalTime.of(9, 0), LocalTime.of(9, 30)),
            new FutureSlotSeed("seed_prod_doc_im_02", LocalTime.of(9, 30), LocalTime.of(10, 0)),
            new FutureSlotSeed("seed_prod_doc_ortho_01", LocalTime.of(10, 0), LocalTime.of(10, 30)),
            new FutureSlotSeed("seed_prod_doc_neuro_01", LocalTime.of(11, 0), LocalTime.of(11, 30)),
            new FutureSlotSeed("seed_prod_doc_derm_01", LocalTime.of(13, 0), LocalTime.of(13, 30)),
            new FutureSlotSeed("seed_prod_doc_im_02", LocalTime.of(14, 0), LocalTime.of(14, 30)),
            new FutureSlotSeed("seed_prod_doc_eye_01", LocalTime.of(15, 0), LocalTime.of(15, 30)),
            new FutureSlotSeed("seed_prod_doc_ortho_01", LocalTime.of(16, 0), LocalTime.of(16, 30)));

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
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        User adminUser = ensureUser(ADMIN_USERNAME, ADMIN_NAME, Role.ADMIN, null, ApprovalStatus.APPROVED);
        Map<String, DoctorProfile> doctorsByUsername = seedDoctors(adminUser);
        User guardianUser = ensureUser(GUARDIAN_USERNAME, GUARDIAN_NAME, Role.GUARDIAN, adminUser, ApprovalStatus.APPROVED);
        Map<String, Vehicle> vehiclesByCode = seedVehicles();
        Map<String, Patient> patientsByKey = seedPatients(adminUser);

        seedGuardianLinks(patientsByKey, guardianUser, adminUser);
        seedScenarios(today, now, doctorsByUsername, patientsByKey, vehiclesByCode);
        seedFutureSlots(today, doctorsByUsername);

        log.info("Prod-like dummy data synced. adminUsername={}, doctorCount={}, vehicleCodes={}, patientCount={}, activeMissionCount={}, futureSlotEnd={}",
                adminUser.getUsername(), doctorsByUsername.size(), vehiclesByCode.keySet(), patientsByKey.size(),
                SCENARIO_SEEDS.stream().filter(seed -> seed.missionSeed() != null).count(), FUTURE_SLOT_END_DATE);
    }

    private Map<String, DoctorProfile> seedDoctors(User adminUser) {
        Map<String, DoctorProfile> doctorsByUsername = new LinkedHashMap<>();
        for (DoctorSeed seed : DOCTOR_SEEDS) {
            User user = ensureUser(seed.username(), seed.name(), Role.DOCTOR, adminUser, ApprovalStatus.APPROVED);
            doctorsByUsername.put(seed.username(), ensureDoctorProfile(user, seed.department(), seed.departmentName()));
        }
        return doctorsByUsername;
    }

    private Map<String, Vehicle> seedVehicles() {
        Map<String, Vehicle> vehiclesByCode = new LinkedHashMap<>();
        for (VehicleSeed seed : VEHICLE_SEEDS) {
            vehiclesByCode.put(seed.code(), ensureVehicle(seed));
        }
        return vehiclesByCode;
    }

    private Map<String, Patient> seedPatients(User adminUser) {
        Map<String, Patient> patientsByKey = new LinkedHashMap<>();
        for (PatientSeed seed : PATIENT_SEEDS) {
            patientsByKey.put(seed.key(), ensurePatient(
                    seed.name(), seed.birthDate(), seed.gender(), seed.regionCode(), seed.address(), seed.phone(),
                    seed.referenceImagePath(), adminUser));
        }
        return patientsByKey;
    }

    private void seedGuardianLinks(Map<String, Patient> patientsByKey, User guardianUser, User adminUser) {
        for (GuardianLinkSeed seed : GUARDIAN_LINK_SEEDS) {
            ensureGuardianLink(getPatient(patientsByKey, seed.patientKey()), guardianUser, seed.relation(),
                    GuardianLinkStatus.APPROVED, adminUser);
        }
    }

    private void seedScenarios(LocalDate today, LocalDateTime now, Map<String, DoctorProfile> doctorsByUsername,
            Map<String, Patient> patientsByKey, Map<String, Vehicle> vehiclesByCode) {
        for (ScenarioSeed seed : SCENARIO_SEEDS) {
            Patient patient = getPatient(patientsByKey, seed.patientKey());
            DoctorProfile doctor = getDoctor(doctorsByUsername, seed.doctorUsername());
            ScheduleSlot slot = ensureSlot(doctor, today.plusDays(seed.dayOffset()), seed.startTime(),
                    seed.startTime().plusMinutes(30));

            IntakeSession intakeSession = ensureIntakeSession(
                    patient, patient.getPhone(), doctor.getDepartment(), doctor.getDepartmentName(),
                    seed.selectionReason(), List.of(slot.getPublicId()), CompletionReason.BOOKING_CREATED);
            syncIntakeSessionTimeline(intakeSession, now.plusMinutes(seed.lastActivityOffsetMinutes()),
                    now.plusMinutes(seed.endedOffsetMinutes()), CompletionReason.BOOKING_CREATED);

            Booking booking = ensureBooking(patient, slot, BookingStatus.CONFIRMED, intakeSession, null);
            CareCase careCase = ensureCareCase(booking, intakeSession);
            syncCaseStatus(careCase, seed.caseStatus());

            if (seed.missionSeed() != null) {
                Vehicle vehicle = getVehicle(vehiclesByCode, seed.missionSeed().vehicleCode());
                LocalDateTime dispatchedAt = now.plusMinutes(seed.missionSeed().dispatchedOffsetMinutes());
                LocalDateTime eta = now.plusMinutes(seed.missionSeed().etaOffsetMinutes());
                Mission mission = ensureMission(careCase, vehicle.getPublicId(), patient.getAddress(), dispatchedAt, eta);
                syncMissionState(mission, vehicle.getPublicId(), patient.getAddress(), dispatchedAt, eta,
                        seed.missionSeed().phase(), seed.missionSeed().previousPhase(),
                        bd(seed.missionSeed().latitude()), bd(seed.missionSeed().longitude()), null);
            }

            if (seed.consultationSeed() != null) {
                ConsultationSession session = ensureConsultationSession(careCase, seed.consultationSeed().roomId(),
                        resolveSeedLivekitUrl());
                syncConsultationSessionState(session, seed.consultationSeed().status(),
                        resolveTimestamp(now, seed.consultationSeed().doctorJoinedOffsetMinutes()),
                        resolveTimestamp(now, seed.consultationSeed().startedOffsetMinutes()), null, null);
            }
        }
    }

    private void seedFutureSlots(LocalDate today, Map<String, DoctorProfile> doctorsByUsername) {
        // 예약 추천이 "가장 빠른 오늘 슬롯"부터 안내되도록 조회용 미래 슬롯도 오늘부터 채운다.
        LocalDate startDate = today;
        if (startDate.isAfter(FUTURE_SLOT_END_DATE)) {
            return;
        }

        for (LocalDate slotDate = startDate; !slotDate.isAfter(FUTURE_SLOT_END_DATE); slotDate = slotDate.plusDays(1)) {
            for (FutureSlotSeed seed : FUTURE_SLOT_SEEDS) {
                ensureSlot(getDoctor(doctorsByUsername, seed.doctorUsername()), slotDate, seed.startTime(), seed.endTime());
            }
        }
    }

    private User ensureUser(String username, String name, Role role, User approver, ApprovalStatus targetStatus) {
        User user = userRepository.findByUsername(username).orElseGet(() -> userRepository.save(
                User.builder()
                        .username(username)
                        .passwordHash(passwordEncoder.encode(defaultPassword))
                        .name(name)
                        .role(role)
                        .build()));
        syncUserBasics(user, name, role);
        syncApprovalStatus(user, approver, targetStatus);
        return user;
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
        if (targetStatus == ApprovalStatus.APPROVED) {
            user.approve(approver);
            return;
        }
        if (targetStatus == ApprovalStatus.REJECTED) {
            user.reject(approver);
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
                            "Active vehicle conflict for region %s: %s".formatted(seed.regionCode(), existing.getCode()));
                });

        Vehicle vehicle = vehicleRepository.findByCode(seed.code()).orElseGet(() -> vehicleRepository.save(
                Vehicle.builder()
                        .code(seed.code())
                        .regionCode(seed.regionCode())
                        .displayName(seed.displayName())
                        .isActive(true)
                        .operationalStatus(OperationalStatus.OPERATIONAL)
                        .statusChangedAt(LocalDateTime.now())
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
                    .setParameter("statusChangedAt", LocalDateTime.now())
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
                && (!patient.hasReferenceImage() || !Objects.equals(patient.getReferenceImagePath(), referenceImagePath))) {
            patient.updateReferenceImage(referenceImagePath, uploadedBy);
        }

        return patient;
    }

    private void ensureGuardianLink(Patient patient, User guardianUser, String relation, GuardianLinkStatus targetStatus,
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
            link.approve(approver);
            return;
        }
        if (targetStatus == GuardianLinkStatus.REJECTED && link.getStatus() != GuardianLinkStatus.REJECTED) {
            link.reject(approver);
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

    private IntakeSession ensureIntakeSession(Patient patient, String callerNumber, String department,
            String departmentName, String selectionReason, List<String> offeredSlotIds,
            CompletionReason completionReason) {
        IntakeSession intakeSession = intakeSessionRepository
                .findFirstByCallerNumberAndChannelOrderByIdAsc(callerNumber, IntakeChannel.PHONE)
                .orElseGet(() -> intakeSessionRepository.save(
                        IntakeSession.builder()
                                .callerNumber(callerNumber)
                                .channel(IntakeChannel.PHONE)
                                .build()));

        if (intakeSession.getPatient() == null || !Objects.equals(intakeSession.getPatient().getId(), patient.getId())) {
            intakeSession.bindPatient(patient);
        }

        intakeSession.recordSelection(department, departmentName, ConfidenceLevel.HIGH, false, selectionReason,
                offeredSlotIds);
        if (intakeSession.isActive()) {
            intakeSession.complete(completionReason);
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

    private Booking ensureBooking(Patient patient, ScheduleSlot slot, BookingStatus targetStatus,
            IntakeSession intakeSession, String cancelReason) {
        Booking booking = bookingRepository.findBySlot(slot).orElseGet(() -> bookingRepository.save(
                Booking.builder()
                        .patient(patient)
                        .intakeSession(intakeSession)
                        .slot(slot)
                        .doctor(slot.getDoctor())
                        .channel(DEFAULT_CHANNEL)
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
                .setParameter("channel", DEFAULT_CHANNEL)
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
            LocalDateTime estimatedArrivalTime) {
        Mission mission = missionRepository.findByCareCase(careCase).orElseGet(() -> missionRepository.save(
                Mission.builder()
                        .careCase(careCase)
                        .vehicleId(vehicleId)
                        .destination(destination)
                        .dispatchedAt(dispatchedAt)
                        .estimatedArrivalTime(estimatedArrivalTime)
                        .build()));

        if (!Objects.equals(mission.getVehicleId(), vehicleId)
                || !Objects.equals(mission.getDestination(), destination)
                || !Objects.equals(mission.getDispatchedAt(), dispatchedAt)
                || !Objects.equals(mission.getEstimatedArrivalTime(), estimatedArrivalTime)) {
            entityManager.createQuery("""
                    update Mission m
                       set m.vehicleId = :vehicleId,
                           m.destination = :destination,
                           m.dispatchedAt = :dispatchedAt,
                           m.estimatedArrivalTime = :estimatedArrivalTime
                     where m.id = :id
                    """)
                    .setParameter("vehicleId", vehicleId)
                    .setParameter("destination", destination)
                    .setParameter("dispatchedAt", dispatchedAt)
                    .setParameter("estimatedArrivalTime", estimatedArrivalTime)
                    .setParameter("id", mission.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.refresh(mission);
        }
        return mission;
    }

    private void syncMissionState(Mission mission, String vehicleId, String destination, LocalDateTime dispatchedAt,
            LocalDateTime estimatedArrivalTime, MissionPhase phase, MissionPhase previousPhase, BigDecimal latitude,
            BigDecimal longitude, LocalDateTime completedAt) {
        entityManager.createQuery("""
                update Mission m
                   set m.vehicleId = :vehicleId,
                       m.destination = :destination,
                       m.dispatchedAt = :dispatchedAt,
                       m.estimatedArrivalTime = :estimatedArrivalTime,
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

    private LocalDateTime resolveTimestamp(LocalDateTime baseTime, Integer offsetMinutes) {
        return offsetMinutes == null ? null : baseTime.plusMinutes(offsetMinutes);
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private record DoctorSeed(String username, String name, String department, String departmentName) {
    }

    private record VehicleSeed(String publicId, String code, String regionCode, String displayName) {
    }

    private record PatientSeed(String key, String name, LocalDate birthDate, PatientGender gender, String regionCode,
            String address, String phone, String referenceImagePath) {
    }

    private record GuardianLinkSeed(String patientKey, String relation) {
    }

    private record ScenarioSeed(String patientKey, String doctorUsername, int dayOffset, LocalTime startTime,
            String selectionReason, int endedOffsetMinutes, int lastActivityOffsetMinutes, CaseStatus caseStatus,
            MissionSeed missionSeed, ConsultationSeed consultationSeed) {
    }

    private record MissionSeed(String vehicleCode, MissionPhase phase, MissionPhase previousPhase, String latitude,
            String longitude, int dispatchedOffsetMinutes, int etaOffsetMinutes) {
    }

    private record ConsultationSeed(ConsultationSessionStatus status, String roomId, Integer doctorJoinedOffsetMinutes,
            Integer startedOffsetMinutes) {
    }

    private record FutureSlotSeed(String doctorUsername, LocalTime startTime, LocalTime endTime) {
    }
}
