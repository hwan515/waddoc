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
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import com.waddoc.domain.patient.repository.PatientGuardianLinkRepository;
import com.waddoc.domain.patient.repository.PatientRepository;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.domain.user.repository.UserRepository;
import com.waddoc.global.type.ApprovalStatus;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class LocalDummyDataSeeder implements ApplicationRunner {

    private static final String DEFAULT_CHANNEL = "PHONE";
    private static final String LIVEKIT_URL = "ws://localhost:7880";
    private static final String REGION_GIMCHEON_JEUNGSAN = "GIMCHEON_JEUNGSAN";
    private static final String ADDRESS_PREFIX = "경북 김천시 증산면 ";
    private static final int PATIENT_COUNT = 80;
    private static final int GUARDIAN_COUNT = 28;

    private static final List<LocalTime> SLOT_START_TIMES = List.of(
            LocalTime.of(9, 0), LocalTime.of(9, 30), LocalTime.of(10, 0), LocalTime.of(10, 30),
            LocalTime.of(11, 0), LocalTime.of(11, 30), LocalTime.of(14, 0), LocalTime.of(14, 30),
            LocalTime.of(15, 0), LocalTime.of(15, 30), LocalTime.of(16, 0), LocalTime.of(16, 30),
            LocalTime.of(17, 0), LocalTime.of(17, 30)
    );

    private static final List<DayOfWeek> WEEKDAYS = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    );
    private static final List<DayOfWeek> MON_WED_FRI = List.of(
            DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY
    );
    private static final List<DayOfWeek> TUE_THU_SAT = List.of(
            DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY
    );
    private static final List<DayOfWeek> MON_THU = List.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY);
    private static final List<DayOfWeek> WED_SAT = List.of(DayOfWeek.WEDNESDAY, DayOfWeek.SATURDAY);

    private static final List<DoctorSeed> DOCTOR_SEEDS = List.of(
            new DoctorSeed("seed_doc_im_01", "김도현", "INTERNAL_MEDICINE", "내과", ApprovalStatus.APPROVED, WEEKDAYS),
            new DoctorSeed("seed_doc_im_02", "박지연", "INTERNAL_MEDICINE", "내과", ApprovalStatus.APPROVED, WEEKDAYS),
            new DoctorSeed("seed_doc_im_03", "이성훈", "INTERNAL_MEDICINE", "내과", ApprovalStatus.APPROVED, WEEKDAYS),
            new DoctorSeed("seed_doc_im_04", "조은서", "INTERNAL_MEDICINE", "내과", ApprovalStatus.APPROVED, WEEKDAYS),
            new DoctorSeed("seed_doc_ortho_01", "최준혁", "ORTHOPEDICS", "정형외과", ApprovalStatus.APPROVED, MON_WED_FRI),
            new DoctorSeed("seed_doc_ortho_02", "강민석", "ORTHOPEDICS", "정형외과", ApprovalStatus.APPROVED, MON_WED_FRI),
            new DoctorSeed("seed_doc_ortho_03", "윤서진", "ORTHOPEDICS", "정형외과", ApprovalStatus.APPROVED, MON_WED_FRI),
            new DoctorSeed("seed_doc_derm_01", "임수빈", "DERMATOLOGY", "피부과", ApprovalStatus.APPROVED, TUE_THU_SAT),
            new DoctorSeed("seed_doc_derm_02", "한지후", "DERMATOLOGY", "피부과", ApprovalStatus.APPROVED, TUE_THU_SAT),
            new DoctorSeed("seed_doc_neuro_01", "정유진", "NEUROLOGY", "신경과", ApprovalStatus.APPROVED, MON_THU),
            new DoctorSeed("seed_doc_neuro_02", "오태경", "NEUROLOGY", "신경과", ApprovalStatus.APPROVED, MON_THU),
            new DoctorSeed("seed_doc_eye_01", "장소라", "OPHTHALMOLOGY", "안과", ApprovalStatus.APPROVED, WED_SAT),
            new DoctorSeed("seed_doc_family_pending", "백현우", "FAMILY_MEDICINE", "가정의학과", ApprovalStatus.PENDING, List.of()),
            new DoctorSeed("seed_doc_im_pending", "서지훈", "INTERNAL_MEDICINE", "내과", ApprovalStatus.PENDING, List.of())
    );

    private static final List<String> PATIENT_SURNAMES = List.of("김", "이", "박", "최", "정", "강", "조", "윤", "장", "임", "한", "오");
    private static final List<String> FEMALE_PATIENT_GIVEN_NAMES = List.of("영희", "순자", "말순", "춘자", "정숙", "옥자", "미숙", "연자", "복순", "경자", "명자", "금순");
    private static final List<String> MALE_PATIENT_GIVEN_NAMES = List.of("영수", "철수", "성호", "동수", "만수", "기동", "정호", "태수", "병철", "정남", "종수", "상호");
    private static final List<String> GUARDIAN_GIVEN_NAMES = List.of("민지", "지훈", "수진", "현우", "은정", "준호", "혜진", "성민", "도윤", "예진", "수현", "태현", "현정", "유진", "소연", "재훈");
    private static final List<String> ROAD_NAMES = List.of("장전1길", "장전2길", "장전3길", "황점길", "금곡길", "황점1길", "금곡1길", "장전마을길");
    private static final List<String> RELATIONS = List.of("배우자", "아들", "딸", "며느리", "사위", "손자", "손녀", "조카");
    private static final List<String> CANCEL_REASONS = List.of("환자 사정으로 일정 변경", "보호자 요청으로 예약 취소", "현장 상황으로 재예약 예정", "증상 호전으로 취소", "중복 예약 확인 후 취소");

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
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    @org.springframework.beans.factory.annotation.Value("${app.seed.default-password}")
    private String defaultPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now();
        LocalDate historyStart = today.minusDays(18);
        LocalDate scheduleEnd = resolveScheduleEnd(today);

        User adminUser = ensureUser("seed_admin", "로컬 관리자", Role.ADMIN, null, ApprovalStatus.APPROVED);
        DoctorSeedBundle doctorBundle = seedDoctors(adminUser);
        GuardianSeedBundle guardianBundle = seedGuardians(adminUser);
        List<Patient> patients = seedPatients(adminUser, PATIENT_COUNT);

        seedGuardianLinks(patients, guardianBundle, adminUser);
        seedDepartmentSlots(doctorBundle.approvedDoctors(), historyStart, scheduleEnd);
        seedJourneys(patients, doctorBundle.approvedDoctors(), today, scheduleEnd);

        log.info(
                "Expanded local dummy data seeded. adminUsername={}, approvedDoctorCount={}, guardianCount={}, patientCount={}, defaultPassword={}, sampleDoctorUsernames={}, samplePatientPhones={}",
                adminUser.getUsername(),
                doctorBundle.approvedDoctors().size(),
                guardianBundle.allUsers().size(),
                patients.size(),
                defaultPassword,
                doctorBundle.approvedDoctors().stream().map(doctor -> doctor.getUser().getUsername()).limit(5).toList(),
                patients.stream().map(Patient::getPhone).limit(6).toList()
        );
    }

    private DoctorSeedBundle seedDoctors(User adminUser) {
        List<DoctorProfile> approvedDoctors = new ArrayList<>();
        List<User> pendingDoctors = new ArrayList<>();

        for (DoctorSeed seed : DOCTOR_SEEDS) {
            User user = ensureUser(seed.username(), seed.name(), Role.DOCTOR, adminUser, seed.approvalStatus());
            DoctorProfile doctorProfile = ensureDoctorProfile(user, seed.department(), seed.departmentName());
            if (seed.approvalStatus() == ApprovalStatus.APPROVED) {
                approvedDoctors.add(doctorProfile);
            } else {
                pendingDoctors.add(user);
            }
        }

        approvedDoctors.sort(Comparator.comparing(doctor -> doctor.getUser().getUsername()));
        return new DoctorSeedBundle(approvedDoctors, pendingDoctors);
    }

    private GuardianSeedBundle seedGuardians(User adminUser) {
        List<User> approved = new ArrayList<>();
        List<User> pending = new ArrayList<>();
        List<User> rejected = new ArrayList<>();

        for (int i = 0; i < GUARDIAN_COUNT; i++) {
            ApprovalStatus approvalStatus = guardianApprovalStatus(i);
            String username = String.format("seed_guardian_%02d", i + 1);
            String name = buildGuardianName(i);
            User guardian = ensureUser(username, name, Role.GUARDIAN, adminUser, approvalStatus);
            if (approvalStatus == ApprovalStatus.APPROVED) {
                approved.add(guardian);
            } else if (approvalStatus == ApprovalStatus.PENDING) {
                pending.add(guardian);
            } else {
                rejected.add(guardian);
            }
        }

        return new GuardianSeedBundle(approved, pending, rejected);
    }

    private List<Patient> seedPatients(User adminUser, int count) {
        List<Patient> patients = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            patients.add(ensurePatient(
                    buildPatientName(i),
                    buildPatientBirthDate(i),
                    REGION_GIMCHEON_JEUNGSAN,
                    buildPatientAddress(i),
                    buildPatientPhone(i),
                    String.format("seed/patients/patient-%02d-reference.jpg", i + 1),
                    adminUser
            ));
        }
        return patients;
    }

    private void seedGuardianLinks(List<Patient> patients, GuardianSeedBundle guardianBundle, User adminUser) {
        for (int i = 0; i < 24; i++) {
            ensureGuardianLink(
                    patients.get(i),
                    guardianBundle.approvedUsers().get(i % guardianBundle.approvedUsers().size()),
                    RELATIONS.get(i % RELATIONS.size()),
                    GuardianLinkStatus.APPROVED,
                    adminUser
            );
        }

        for (int i = 0; i < 8; i++) {
            ensureGuardianLink(
                    patients.get(24 + i),
                    guardianBundle.pendingUsers().get(i % guardianBundle.pendingUsers().size()),
                    RELATIONS.get((i + 2) % RELATIONS.size()),
                    GuardianLinkStatus.PENDING,
                    adminUser
            );
        }

        for (int i = 0; i < 4; i++) {
            ensureGuardianLink(
                    patients.get(32 + i),
                    guardianBundle.rejectedUsers().get(i % guardianBundle.rejectedUsers().size()),
                    RELATIONS.get((i + 4) % RELATIONS.size()),
                    GuardianLinkStatus.REJECTED,
                    adminUser
            );
        }
    }

    private void seedDepartmentSlots(List<DoctorProfile> approvedDoctors, LocalDate startDate, LocalDate endDate) {
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            for (DoctorProfile doctor : approvedDoctors) {
                if (!isWorkingDay(doctor, date)) {
                    continue;
                }
                for (LocalTime startTime : SLOT_START_TIMES) {
                    ensureSlot(doctor, date, startTime, startTime.plusMinutes(30));
                }
            }
        }
    }

    private void seedJourneys(List<Patient> patients, List<DoctorProfile> approvedDoctors, LocalDate today, LocalDate scheduleEnd) {
        Map<String, List<DoctorProfile>> doctorsByDepartment = approvedDoctors.stream()
                .collect(Collectors.groupingBy(
                        DoctorProfile::getDepartment,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            list.sort(Comparator.comparing(doctor -> doctor.getUser().getUsername()));
                            return list;
                        })
                ));
        Map<String, Integer> doctorCursor = new HashMap<>();
        SeedSequence sequence = new SeedSequence();

        seedHistoricalCompletedCases(patients.subList(0, 14), doctorsByDepartment, doctorCursor, today, sequence);
        seedActiveMissionCases(patients.subList(14, 24), doctorsByDepartment, doctorCursor, today, sequence);
        seedFutureConfirmedBookings(patients.subList(24, 58), doctorsByDepartment, doctorCursor, today, scheduleEnd);
        seedCancelledBookings(patients.subList(58, 68), doctorsByDepartment, doctorCursor, today, scheduleEnd);
        seedIntakeOnlySessions(patients.subList(68, patients.size()), doctorsByDepartment, doctorCursor, today);
    }

    private void seedHistoricalCompletedCases(List<Patient> patients,
                                              Map<String, List<DoctorProfile>> doctorsByDepartment,
                                              Map<String, Integer> doctorCursor,
                                              LocalDate today,
                                              SeedSequence sequence) {
        List<String> departments = List.of(
                "INTERNAL_MEDICINE", "ORTHOPEDICS", "DERMATOLOGY", "NEUROLOGY", "OPHTHALMOLOGY",
                "INTERNAL_MEDICINE", "ORTHOPEDICS", "DERMATOLOGY", "NEUROLOGY", "INTERNAL_MEDICINE",
                "ORTHOPEDICS", "INTERNAL_MEDICINE", "OPHTHALMOLOGY", "DERMATOLOGY"
        );

        for (int i = 0; i < patients.size(); i++) {
            seedJourney(
                    patients.get(i),
                    nextDoctor(doctorsByDepartment, doctorCursor, departments.get(i % departments.size())),
                    today.minusDays(i + 1L),
                    SLOT_START_TIMES.get(i % SLOT_START_TIMES.size()),
                    BookingStatus.COMPLETED,
                    CaseStatus.COMPLETED,
                    CompletionReason.BOOKING_CREATED,
                    MissionPhase.COMPLETED,
                    ConsultationSessionStatus.COMPLETED,
                    true,
                    true,
                    null,
                    sequence,
                    i
            );
        }
    }

    private void seedActiveMissionCases(List<Patient> patients,
                                        Map<String, List<DoctorProfile>> doctorsByDepartment,
                                        Map<String, Integer> doctorCursor,
                                        LocalDate today,
                                        SeedSequence sequence) {
        List<ActiveJourneyPlan> plans = List.of(
                new ActiveJourneyPlan("INTERNAL_MEDICINE", 0, LocalTime.of(9, 30), BookingStatus.CONFIRMED, CaseStatus.PREPARING, MissionPhase.DISPATCHED, null, false),
                new ActiveJourneyPlan("ORTHOPEDICS", 0, LocalTime.of(11, 0), BookingStatus.CONFIRMED, CaseStatus.PREPARING, MissionPhase.EN_ROUTE, null, false),
                new ActiveJourneyPlan("DERMATOLOGY", 1, LocalTime.of(14, 0), BookingStatus.CONFIRMED, CaseStatus.PREPARING, MissionPhase.ARRIVED, ConsultationSessionStatus.READY, false),
                new ActiveJourneyPlan("NEUROLOGY", 1, LocalTime.of(15, 30), BookingStatus.CONFIRMED, CaseStatus.IN_PROGRESS, MissionPhase.VERIFYING, ConsultationSessionStatus.READY, false),
                new ActiveJourneyPlan("INTERNAL_MEDICINE", 2, LocalTime.of(10, 0), BookingStatus.CONFIRMED, CaseStatus.IN_PROGRESS, MissionPhase.CONSULTING, ConsultationSessionStatus.IN_PROGRESS, false),
                new ActiveJourneyPlan("OPHTHALMOLOGY", 2, LocalTime.of(16, 0), BookingStatus.CONFIRMED, CaseStatus.IN_PROGRESS, MissionPhase.CONSULTING, ConsultationSessionStatus.IN_PROGRESS, false),
                new ActiveJourneyPlan("ORTHOPEDICS", 3, LocalTime.of(9, 0), BookingStatus.COMPLETED, CaseStatus.IN_PROGRESS, MissionPhase.RETURNING, ConsultationSessionStatus.COMPLETED, true),
                new ActiveJourneyPlan("DERMATOLOGY", 4, LocalTime.of(17, 0), BookingStatus.CONFIRMED, CaseStatus.PREPARING, MissionPhase.DISPATCHED, null, false),
                new ActiveJourneyPlan("INTERNAL_MEDICINE", 5, LocalTime.of(14, 30), BookingStatus.CONFIRMED, CaseStatus.PREPARING, MissionPhase.EN_ROUTE, null, false),
                new ActiveJourneyPlan("NEUROLOGY", 6, LocalTime.of(17, 30), BookingStatus.CONFIRMED, CaseStatus.IN_PROGRESS, MissionPhase.VERIFYING, ConsultationSessionStatus.READY, false)
        );

        for (int i = 0; i < patients.size(); i++) {
            ActiveJourneyPlan plan = plans.get(i);
            seedJourney(
                    patients.get(i),
                    nextDoctor(doctorsByDepartment, doctorCursor, plan.department()),
                    today.plusDays(plan.dayOffset()),
                    plan.startTime(),
                    plan.bookingStatus(),
                    plan.caseStatus(),
                    CompletionReason.BOOKING_CREATED,
                    plan.missionPhase(),
                    plan.sessionStatus(),
                    true,
                    plan.createSummary(),
                    null,
                    sequence,
                    100 + i
            );
        }
    }

    private void seedFutureConfirmedBookings(List<Patient> patients,
                                             Map<String, List<DoctorProfile>> doctorsByDepartment,
                                             Map<String, Integer> doctorCursor,
                                             LocalDate today,
                                             LocalDate scheduleEnd) {
        List<String> departmentRotation = List.of(
                "INTERNAL_MEDICINE", "ORTHOPEDICS", "DERMATOLOGY", "NEUROLOGY",
                "INTERNAL_MEDICINE", "OPHTHALMOLOGY", "INTERNAL_MEDICINE", "ORTHOPEDICS"
        );

        for (int i = 0; i < patients.size(); i++) {
            LocalDate appointmentDate = today.plusDays(3L + i);
            if (appointmentDate.isAfter(scheduleEnd)) {
                appointmentDate = scheduleEnd.minusDays(i % 5L);
            }
            seedJourney(
                    patients.get(i),
                    nextDoctor(doctorsByDepartment, doctorCursor, departmentRotation.get(i % departmentRotation.size())),
                    appointmentDate,
                    SLOT_START_TIMES.get((i + 4) % SLOT_START_TIMES.size()),
                    BookingStatus.CONFIRMED,
                    CaseStatus.CREATED,
                    CompletionReason.BOOKING_CREATED,
                    null,
                    null,
                    false,
                    false,
                    null,
                    null,
                    200 + i
            );
        }
    }

    private void seedCancelledBookings(List<Patient> patients,
                                       Map<String, List<DoctorProfile>> doctorsByDepartment,
                                       Map<String, Integer> doctorCursor,
                                       LocalDate today,
                                       LocalDate scheduleEnd) {
        List<String> departmentRotation = List.of("INTERNAL_MEDICINE", "ORTHOPEDICS", "DERMATOLOGY", "NEUROLOGY", "OPHTHALMOLOGY");

        for (int i = 0; i < patients.size(); i++) {
            LocalDate appointmentDate = today.plusDays(5L + (i * 2L));
            if (appointmentDate.isAfter(scheduleEnd)) {
                appointmentDate = scheduleEnd.minusDays((i % 4L) + 1L);
            }
            seedJourney(
                    patients.get(i),
                    nextDoctor(doctorsByDepartment, doctorCursor, departmentRotation.get(i % departmentRotation.size())),
                    appointmentDate,
                    SLOT_START_TIMES.get((i + 2) % SLOT_START_TIMES.size()),
                    BookingStatus.CANCELLED,
                    CaseStatus.CANCELLED,
                    CompletionReason.BOOKING_CREATED,
                    null,
                    null,
                    false,
                    false,
                    CANCEL_REASONS.get(i % CANCEL_REASONS.size()),
                    null,
                    300 + i
            );
        }
    }

    private void seedIntakeOnlySessions(List<Patient> patients,
                                        Map<String, List<DoctorProfile>> doctorsByDepartment,
                                        Map<String, Integer> doctorCursor,
                                        LocalDate today) {
        CompletionReason[] completionReasons = {
                CompletionReason.EXISTING_BOOKING_CHECKED, CompletionReason.EXISTING_BOOKING_CHECKED,
                CompletionReason.EXISTING_BOOKING_CHECKED, CompletionReason.EXISTING_BOOKING_CHECKED,
                CompletionReason.EXISTING_BOOKING_CHECKED, CompletionReason.EXISTING_BOOKING_CHECKED,
                CompletionReason.NO_INPUT_TIMEOUT, CompletionReason.NO_INPUT_TIMEOUT, CompletionReason.NO_INPUT_TIMEOUT,
                CompletionReason.USER_HANGUP, CompletionReason.USER_HANGUP, CompletionReason.USER_HANGUP
        };
        List<String> departmentRotation = List.of("INTERNAL_MEDICINE", "ORTHOPEDICS", "DERMATOLOGY", "NEUROLOGY", "OPHTHALMOLOGY");

        for (int i = 0; i < patients.size(); i++) {
            DoctorProfile doctor = nextDoctor(doctorsByDepartment, doctorCursor, departmentRotation.get(i % departmentRotation.size()));
            LocalDate slotDate = today.plusDays(7L + i);
            LocalTime slotTime = SLOT_START_TIMES.get((i + 1) % SLOT_START_TIMES.size());
            ScheduleSlot slot = ensureSlot(doctor, slotDate, slotTime, slotTime.plusMinutes(30));
            IntakeSession intakeSession = ensureIntakeSession(
                    patients.get(i),
                    patients.get(i).getPhone(),
                    doctor.getDepartment(),
                    doctor.getDepartmentName(),
                    buildSelectionReason(doctor.getDepartment(), 400 + i),
                    List.of(slot.getPublicId()),
                    completionReasons[i]
            );
            LocalDateTime activityTime = LocalDateTime.of(slotDate, slotTime.minusMinutes(20));
            syncIntakeSessionTimeline(intakeSession, activityTime, activityTime.plusMinutes(4), completionReasons[i]);
        }
    }

    private void seedJourney(Patient patient,
                             DoctorProfile doctor,
                             LocalDate appointmentDate,
                             LocalTime startTime,
                             BookingStatus bookingStatus,
                             CaseStatus caseStatus,
                             CompletionReason completionReason,
                             MissionPhase missionPhase,
                             ConsultationSessionStatus sessionStatus,
                             boolean createMission,
                             boolean createSummary,
                             String cancelReason,
                             SeedSequence sequence,
                             int seedIndex) {
        ScheduleSlot slot = ensureSlot(doctor, appointmentDate, startTime, startTime.plusMinutes(30));
        IntakeSession intakeSession = ensureIntakeSession(
                patient,
                patient.getPhone(),
                doctor.getDepartment(),
                doctor.getDepartmentName(),
                buildSelectionReason(doctor.getDepartment(), seedIndex),
                List.of(slot.getPublicId()),
                completionReason
        );

        LocalDateTime intakeBaseTime = LocalDateTime.of(appointmentDate, startTime.minusMinutes(25));
        syncIntakeSessionTimeline(intakeSession, intakeBaseTime, intakeBaseTime.plusMinutes(5), completionReason);

        Booking booking = ensureBooking(patient, slot, bookingStatus, intakeSession, cancelReason);
        CareCase careCase = ensureCareCase(booking, intakeSession);
        syncCaseStatus(careCase, caseStatus);

        if (createMission && missionPhase != null) {
            LocalDateTime dispatchedAt = LocalDateTime.of(appointmentDate, startTime.minusMinutes(50));
            LocalDateTime eta = LocalDateTime.of(appointmentDate, startTime.minusMinutes(15));
            LocalDateTime missionCompletedAt = missionPhase == MissionPhase.COMPLETED
                    ? LocalDateTime.of(appointmentDate, startTime.plusMinutes(45))
                    : null;

            Mission mission = ensureMission(
                    careCase,
                    String.format("GIMCHEON-%02d", sequence.nextVehicle()),
                    patient.getAddress(),
                    dispatchedAt,
                    eta,
                    missionPhase,
                    coordinateValue(35.8612, seedIndex),
                    coordinateValue(128.0581, seedIndex + 7)
            );
            syncMissionTimeline(mission, dispatchedAt, eta, missionCompletedAt);
        }

        if (sessionStatus != null) {
            ConsultationSession session = ensureConsultationSession(
                    careCase,
                    String.format("seed-room-%03d", sequence.nextRoom()),
                    LIVEKIT_URL,
                    sessionStatus,
                    resolveDurationMinutes(sessionStatus, seedIndex)
            );

            LocalDateTime joinedAt = LocalDateTime.of(appointmentDate, startTime.plusMinutes(3));
            LocalDateTime startedAt = sessionStatus == ConsultationSessionStatus.READY
                    ? null
                    : LocalDateTime.of(appointmentDate, startTime.plusMinutes(5));
            LocalDateTime endedAt = sessionStatus == ConsultationSessionStatus.COMPLETED
                    ? LocalDateTime.of(appointmentDate, startTime.plusMinutes(5 + resolveDurationMinutes(sessionStatus, seedIndex)))
                    : null;

            syncConsultationSessionState(
                    session,
                    sessionStatus,
                    joinedAt,
                    startedAt,
                    endedAt,
                    resolveDurationMinutes(sessionStatus, seedIndex)
            );

            if (createSummary) {
                ensureConsultationSummary(
                        session,
                        buildSummaryNote(doctor.getDepartment(), seedIndex),
                        shouldIssuePrescription(doctor.getDepartment(), seedIndex),
                        buildPrescriptionNote(doctor.getDepartment(), seedIndex),
                        needsFollowUp(doctor.getDepartment(), seedIndex)
                );
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
                        .build()
        ));

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
        DoctorProfile doctorProfile = doctorProfileRepository.findByUserUsername(user.getUsername()).orElseGet(() ->
                doctorProfileRepository.save(
                        DoctorProfile.builder()
                                .user(user)
                                .department(department)
                                .departmentName(departmentName)
                                .build()
                )
        );

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

    private Patient ensurePatient(String name, LocalDate birthDate, String regionCode, String address, String phone,
                                  String referenceImagePath, User uploadedBy) {
        String birthDate6 = birthDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        Patient patient = patientRepository.findByPhone(phone)
                .or(() -> patientRepository.findAllByNameAndBirthDate6(name, birthDate6).stream().findFirst())
                .orElseGet(() -> patientRepository.save(
                        Patient.builder()
                                .name(name)
                                .birthDate(birthDate)
                                .regionCode(regionCode)
                                .address(address)
                                .phone(phone)
                                .build()
                ));

        if (!name.equals(patient.getName())
                || !birthDate.equals(patient.getBirthDate())
                || !regionCode.equals(patient.getRegionCode())
                || !address.equals(patient.getAddress())
                || !phone.equals(patient.getPhone())) {
            patient.updateProfile(name, birthDate, regionCode, address, phone);
        }

        if (referenceImagePath != null
                && (!patient.hasReferenceImage() || !referenceImagePath.equals(patient.getReferenceImagePath()))) {
            patient.updateReferenceImage(referenceImagePath, uploadedBy);
        }

        return patient;
    }

    private PatientGuardianLink ensureGuardianLink(Patient patient, User guardianUser, String relation,
                                                   GuardianLinkStatus targetStatus, User approver) {
        PatientGuardianLink link = patientGuardianLinkRepository.findByPatientAndGuardianUser(patient, guardianUser)
                .orElseGet(() -> patientGuardianLinkRepository.save(
                        PatientGuardianLink.builder()
                                .patient(patient)
                                .guardianUser(guardianUser)
                                .relation(relation)
                                .build()
                ));

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
        } else if (targetStatus == GuardianLinkStatus.REJECTED && link.getStatus() != GuardianLinkStatus.REJECTED) {
            link.reject(approver);
        } else if (targetStatus == GuardianLinkStatus.PENDING && link.getStatus() != GuardianLinkStatus.PENDING) {
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

        return link;
    }

    private IntakeSession ensureIntakeSession(Patient patient, String callerNumber,
                                              String department, String departmentName,
                                              String selectionReason,
                                              List<String> offeredSlotIds,
                                              CompletionReason completionReason) {
        IntakeSession intakeSession = intakeSessionRepository
                .findFirstByCallerNumberAndChannelOrderByIdAsc(callerNumber, IntakeChannel.PHONE)
                .orElseGet(() -> intakeSessionRepository.save(
                        IntakeSession.builder()
                                .callerNumber(callerNumber)
                                .channel(IntakeChannel.PHONE)
                                .build()
                ));

        if (intakeSession.getPatient() == null || !intakeSession.getPatient().getId().equals(patient.getId())) {
            intakeSession.bindPatient(patient);
        }

        intakeSession.recordSelection(
                department,
                departmentName,
                ConfidenceLevel.HIGH,
                false,
                selectionReason,
                offeredSlotIds
        );

        if (intakeSession.isActive()) {
            intakeSession.complete(completionReason);
        }

        return intakeSession;
    }

    private void syncIntakeSessionTimeline(IntakeSession intakeSession,
                                           LocalDateTime lastActivityAt,
                                           LocalDateTime endedAt,
                                           CompletionReason completionReason) {
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
        return scheduleSlotRepository
                .findByDoctorUserUsernameAndSlotDateAndStartTime(doctor.getUser().getUsername(), slotDate, startTime)
                .orElseGet(() -> scheduleSlotRepository.save(
                        ScheduleSlot.builder()
                                .doctor(doctor)
                                .slotDate(slotDate)
                                .startTime(startTime)
                                .endTime(endTime)
                                .build()
                ));
    }

    private Booking ensureBooking(Patient patient, ScheduleSlot slot, BookingStatus targetStatus,
                                  IntakeSession intakeSession, String cancelReason) {
        Booking booking = bookingRepository.findBySlot(slot).orElseGet(() -> {
            if (!slot.isBooked() && targetStatus != BookingStatus.CANCELLED) {
                slot.markBooked();
            }

            return bookingRepository.save(
                    Booking.builder()
                            .patient(patient)
                            .intakeSession(intakeSession)
                            .slot(slot)
                            .doctor(slot.getDoctor())
                            .channel(DEFAULT_CHANNEL)
                            .appointmentDate(slot.getSlotDate())
                            .startTime(slot.getStartTime())
                            .endTime(slot.getEndTime())
                            .build()
            );
        });

        if (targetStatus == BookingStatus.CANCELLED) {
            slot.markAvailable();
        } else if (!slot.isBooked()) {
            slot.markBooked();
        }

        entityManager.createQuery("""
                        update Booking b
                           set b.intakeSession = :intakeSession,
                               b.status = :status,
                               b.cancelReason = :cancelReason,
                               b.cancelledAt = :cancelledAt
                         where b.id = :id
                        """)
                .setParameter("intakeSession", intakeSession)
                .setParameter("status", targetStatus)
                .setParameter("cancelReason", targetStatus == BookingStatus.CANCELLED ? cancelReason : null)
                .setParameter("cancelledAt", targetStatus == BookingStatus.CANCELLED
                        ? LocalDateTime.of(slot.getSlotDate(), slot.getStartTime().minusMinutes(35))
                        : null)
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
                        .build()
        ));

        if (intakeSession != null && (careCase.getIntakeSession() == null
                || !careCase.getIntakeSession().getId().equals(intakeSession.getId()))) {
            entityManager.createQuery("""
                            update CareCase c
                               set c.intakeSession = :intakeSession
                             where c.id = :id
                            """)
                    .setParameter("intakeSession", intakeSession)
                    .setParameter("id", careCase.getId())
                    .executeUpdate();
            entityManager.flush();
            entityManager.refresh(careCase);
        }

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

    private Mission ensureMission(CareCase careCase, String vehicleId, String destination,
                                  LocalDateTime dispatchedAt, LocalDateTime estimatedArrivalTime,
                                  MissionPhase targetPhase, BigDecimal latitude, BigDecimal longitude) {
        Mission mission = missionRepository.findByCareCase(careCase).orElseGet(() -> missionRepository.save(
                Mission.builder()
                        .careCase(careCase)
                        .vehicleId(vehicleId)
                        .destination(destination)
                        .dispatchedAt(dispatchedAt)
                        .estimatedArrivalTime(estimatedArrivalTime)
                        .build()
        ));

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

        if (latitude != null && longitude != null) {
            mission.updateLocation(latitude, longitude);
        }

        if (mission.getPhase() != targetPhase) {
            mission.updatePhase(targetPhase);
        }

        return mission;
    }

    private void syncMissionTimeline(Mission mission,
                                     LocalDateTime dispatchedAt,
                                     LocalDateTime estimatedArrivalTime,
                                     LocalDateTime completedAt) {
        entityManager.createQuery("""
                        update Mission m
                           set m.dispatchedAt = :dispatchedAt,
                               m.estimatedArrivalTime = :estimatedArrivalTime,
                               m.completedAt = :completedAt
                         where m.id = :id
                        """)
                .setParameter("dispatchedAt", dispatchedAt)
                .setParameter("estimatedArrivalTime", estimatedArrivalTime)
                .setParameter("completedAt", completedAt)
                .setParameter("id", mission.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.refresh(mission);
    }

    private ConsultationSession ensureConsultationSession(CareCase careCase, String roomId, String livekitUrl,
                                                          ConsultationSessionStatus targetStatus, Integer durationMinutes) {
        ConsultationSession session = consultationSessionRepository.findByCareCase(careCase).orElseGet(() ->
                consultationSessionRepository.save(
                        ConsultationSession.builder()
                                .careCase(careCase)
                                .roomId(roomId)
                                .livekitUrl(livekitUrl)
                                .build()
                )
        );

        if (targetStatus == ConsultationSessionStatus.READY && session.getStatus() == ConsultationSessionStatus.CREATED) {
            session.markReady();
        }
        if (targetStatus == ConsultationSessionStatus.IN_PROGRESS && session.getStatus() != ConsultationSessionStatus.IN_PROGRESS) {
            session.connectDoctor();
            session.connectPatient();
        }
        if (targetStatus == ConsultationSessionStatus.COMPLETED && session.getStatus() != ConsultationSessionStatus.COMPLETED) {
            if (session.getStatus() == ConsultationSessionStatus.CREATED) {
                session.markReady();
            }
            if (session.getStatus() != ConsultationSessionStatus.IN_PROGRESS) {
                session.start();
            }
            session.complete(durationMinutes != null ? durationMinutes : 15);
        }

        return session;
    }

    private void syncConsultationSessionState(ConsultationSession session,
                                              ConsultationSessionStatus targetStatus,
                                              LocalDateTime joinedAt,
                                              LocalDateTime startedAt,
                                              LocalDateTime endedAt,
                                              Integer durationMinutes) {
        ConnectionState doctorState = targetStatus == ConsultationSessionStatus.READY
                ? ConnectionState.DISCONNECTED
                : ConnectionState.CONNECTED;
        ConnectionState patientState = targetStatus == ConsultationSessionStatus.READY
                ? ConnectionState.DISCONNECTED
                : ConnectionState.CONNECTED;

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
                .setParameter("doctorJoinedAt", targetStatus == ConsultationSessionStatus.READY ? null : joinedAt)
                .setParameter("patientJoinedAt", targetStatus == ConsultationSessionStatus.READY ? null : joinedAt.plusMinutes(1))
                .setParameter("startedAt", startedAt)
                .setParameter("endedAt", endedAt)
                .setParameter("durationMinutes", durationMinutes)
                .setParameter("id", session.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.refresh(session);
    }

    private ConsultationSummary ensureConsultationSummary(ConsultationSession session, String summaryNote,
                                                          boolean prescriptionIssued, String prescriptionNote,
                                                          boolean needsFollowUp) {
        ConsultationSummary summary = consultationSummaryRepository.findBySession(session).orElseGet(() ->
                consultationSummaryRepository.save(
                        ConsultationSummary.builder()
                                .session(session)
                                .summaryNote(summaryNote)
                                .prescriptionIssued(prescriptionIssued)
                                .prescriptionNote(prescriptionNote)
                                .needsFollowUp(needsFollowUp)
                                .build()
                )
        );
        summary.update(summaryNote, prescriptionIssued, prescriptionNote, needsFollowUp);
        return summary;
    }

    private DoctorProfile nextDoctor(Map<String, List<DoctorProfile>> doctorsByDepartment,
                                     Map<String, Integer> doctorCursor,
                                     String department) {
        List<DoctorProfile> doctors = doctorsByDepartment.get(department);
        if (doctors == null || doctors.isEmpty()) {
            throw new IllegalStateException("No doctor seeded for department: " + department);
        }

        int cursor = doctorCursor.getOrDefault(department, 0);
        doctorCursor.put(department, cursor + 1);
        return doctors.get(cursor % doctors.size());
    }

    private boolean isWorkingDay(DoctorProfile doctor, LocalDate date) {
        DoctorSeed doctorSeed = DOCTOR_SEEDS.stream()
                .filter(seed -> seed.username().equals(doctor.getUser().getUsername()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Doctor seed metadata missing for " + doctor.getUser().getUsername()));
        return doctorSeed.workDays().contains(date.getDayOfWeek());
    }

    private LocalDate resolveScheduleEnd(LocalDate today) {
        LocalDate aprilEnd = LocalDate.of(today.getYear(), 4, 30);
        return today.isAfter(aprilEnd) ? today.plusDays(42) : aprilEnd;
    }

    private ApprovalStatus guardianApprovalStatus(int index) {
        if (index < 20) {
            return ApprovalStatus.APPROVED;
        }
        if (index < 26) {
            return ApprovalStatus.PENDING;
        }
        return ApprovalStatus.REJECTED;
    }

    private String buildPatientName(int index) {
        String surname = PATIENT_SURNAMES.get(index % PATIENT_SURNAMES.size());
        List<String> givenNames = index % 2 == 0 ? FEMALE_PATIENT_GIVEN_NAMES : MALE_PATIENT_GIVEN_NAMES;
        return surname + givenNames.get((index / PATIENT_SURNAMES.size()) % givenNames.size());
    }

    private String buildGuardianName(int index) {
        return PATIENT_SURNAMES.get((index + 3) % PATIENT_SURNAMES.size())
                + GUARDIAN_GIVEN_NAMES.get(index % GUARDIAN_GIVEN_NAMES.size());
    }

    private LocalDate buildPatientBirthDate(int index) {
        int year;
        if (index < 16) {
            year = 1938 + (index % 9);
        } else if (index < 44) {
            year = 1947 + (index % 10);
        } else if (index < 68) {
            year = 1957 + (index % 10);
        } else if (index < 76) {
            year = 1967 + (index % 10);
        } else {
            year = 1978 + (index % 8);
        }
        return LocalDate.of(year, (index % 12) + 1, ((index * 3) % 28) + 1);
    }

    private String buildPatientAddress(int index) {
        String roadName = ROAD_NAMES.get(index % ROAD_NAMES.size());
        int houseNumber = 19 + (index * 3);
        return index % 4 == 0
                ? ADDRESS_PREFIX + roadName + " " + houseNumber + "-" + ((index % 7) + 1)
                : ADDRESS_PREFIX + roadName + " " + houseNumber;
    }

    private String buildPatientPhone(int index) {
        return String.format("010%08d", 51000000 + index);
    }

    private String buildSelectionReason(String department, int seedIndex) {
        List<String> reasons = switch (department) {
            case "INTERNAL_MEDICINE" -> List.of("어지럼과 혈압 변동으로 내과 상담 요청", "기침과 미열이 이어져 내과 예약 희망", "복통과 소화불량 증상으로 내과 진료 요청");
            case "ORTHOPEDICS" -> List.of("무릎 통증과 보행 불편으로 정형외과 예약 요청", "허리 통증이 심해져 정형외과 진료 필요", "어깨 관절 통증으로 정형외과 상담 요청");
            case "DERMATOLOGY" -> List.of("가려움과 발진이 반복되어 피부과 진료 요청", "팔 부위 습진 악화로 피부과 예약 희망", "두드러기 증상 확인을 위해 피부과 상담 요청");
            case "NEUROLOGY" -> List.of("두통과 어지럼이 반복되어 신경과 진료 요청", "손 저림 증상으로 신경과 상담 필요", "머리가 무겁고 집중이 어려워 신경과 예약");
            case "OPHTHALMOLOGY" -> List.of("시야 흐림과 눈 충혈로 안과 예약 요청", "안구 건조와 이물감으로 안과 상담 희망", "눈부심이 심해져 안과 진료 필요");
            default -> List.of("증상 확인을 위해 진료 예약을 진행함");
        };
        return reasons.get(seedIndex % reasons.size());
    }

    private String buildSummaryNote(String department, int seedIndex) {
        List<String> notes = switch (department) {
            case "INTERNAL_MEDICINE" -> List.of("혈압과 복용 약을 점검했고 당분간 현재 처방을 유지하기로 안내했습니다.", "감기 증상은 경미해 대증 치료와 수분 섭취를 중심으로 설명했습니다.", "복통은 식습관과 복용 중인 약을 확인해 경과 관찰 후 재평가하기로 했습니다.");
            case "ORTHOPEDICS" -> List.of("무릎 관절 통증은 퇴행성 변화 가능성이 있어 보행량 조절과 찜질을 안내했습니다.", "허리 통증은 급성 악화 소견은 없어 약 복용과 자세 교정을 설명했습니다.", "어깨 통증은 반복 사용 영향이 커 보여 스트레칭과 추적 관찰을 권고했습니다.");
            case "DERMATOLOGY" -> List.of("접촉성 피부염 가능성이 높아 자극 회피와 연고 사용법을 설명했습니다.", "가려움은 야간 악화 양상이 있어 보습과 항히스타민 복용을 안내했습니다.", "발진은 급성 중증 소견은 없어 경과 관찰과 재내원 기준을 설명했습니다.");
            case "NEUROLOGY" -> List.of("긴장성 두통 가능성이 높아 수면과 수분 섭취, 복약 방법을 안내했습니다.", "어지럼은 탈수와 기립성 저혈압 가능성을 함께 설명하고 주의사항을 전달했습니다.", "손 저림은 경추성 가능성을 배제할 수 없어 증상 지속 시 추가 평가를 권고했습니다.");
            case "OPHTHALMOLOGY" -> List.of("안구 건조 증상이 주된 문제로 보여 인공눈물 사용과 생활 습관을 안내했습니다.", "충혈은 염증 소견이 경미해 점안제 사용과 악화 시 재내원 기준을 설명했습니다.", "시야 흐림은 급성 응급 소견은 없어 약물 사용 후 경과를 보기로 했습니다.");
            default -> List.of("진료 후 경과 관찰과 복약 지도를 안내했습니다.");
        };
        return notes.get(seedIndex % notes.size());
    }

    private String buildPrescriptionNote(String department, int seedIndex) {
        if (!shouldIssuePrescription(department, seedIndex)) {
            return null;
        }
        return switch (department) {
            case "INTERNAL_MEDICINE" -> "혈압약 또는 소화기 증상 완화 약 7일분 처방";
            case "ORTHOPEDICS" -> "소염진통제와 근이완제 5일분 처방";
            case "DERMATOLOGY" -> "연고와 항히스타민제 사용법 안내";
            case "NEUROLOGY" -> "두통 조절 약과 생활 관리 지침 안내";
            case "OPHTHALMOLOGY" -> "점안제와 인공눈물 사용법 안내";
            default -> "기본 복약 지도를 제공";
        };
    }

    private boolean shouldIssuePrescription(String department, int seedIndex) {
        if ("DERMATOLOGY".equals(department) || "OPHTHALMOLOGY".equals(department)) {
            return true;
        }
        return seedIndex % 3 != 0;
    }

    private boolean needsFollowUp(String department, int seedIndex) {
        if ("NEUROLOGY".equals(department) || "ORTHOPEDICS".equals(department)) {
            return true;
        }
        return seedIndex % 4 == 0;
    }

    private Integer resolveDurationMinutes(ConsultationSessionStatus sessionStatus, int seedIndex) {
        if (sessionStatus == null || sessionStatus == ConsultationSessionStatus.READY) {
            return null;
        }
        return 12 + (seedIndex % 9);
    }

    private BigDecimal coordinateValue(double base, int seedIndex) {
        double offset = (seedIndex % 15) * 0.00073d;
        return bd(String.format(java.util.Locale.US, "%.7f", base + offset));
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private record DoctorSeed(String username, String name, String department, String departmentName,
                              ApprovalStatus approvalStatus, List<DayOfWeek> workDays) {
    }

    private record DoctorSeedBundle(List<DoctorProfile> approvedDoctors, List<User> pendingDoctors) {
    }

    private record GuardianSeedBundle(List<User> approvedUsers, List<User> pendingUsers, List<User> rejectedUsers) {
        private List<User> allUsers() {
            List<User> allUsers = new ArrayList<>(approvedUsers);
            allUsers.addAll(pendingUsers);
            allUsers.addAll(rejectedUsers);
            return allUsers;
        }
    }

    private record ActiveJourneyPlan(String department, int dayOffset, LocalTime startTime,
                                     BookingStatus bookingStatus, CaseStatus caseStatus,
                                     MissionPhase missionPhase, ConsultationSessionStatus sessionStatus,
                                     boolean createSummary) {
    }

    private static final class SeedSequence {
        private int vehicle = 1;
        private int room = 1;

        private int nextVehicle() {
            return vehicle++;
        }

        private int nextRoom() {
            return room++;
        }
    }
}
