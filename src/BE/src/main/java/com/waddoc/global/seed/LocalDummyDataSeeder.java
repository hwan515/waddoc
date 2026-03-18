package com.waddoc.global.seed;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.booking.repository.BookingRepository;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
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
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import com.waddoc.domain.patient.entity.GuardianLinkStatus;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true")
public class LocalDummyDataSeeder implements ApplicationRunner {

    private static final LocalTime SLOT_0900 = LocalTime.of(9, 0);
    private static final LocalTime SLOT_0930 = LocalTime.of(9, 30);
    private static final LocalTime SLOT_1000 = LocalTime.of(10, 0);
    private static final LocalTime SLOT_1030 = LocalTime.of(10, 30);
    private static final LocalTime SLOT_1100 = LocalTime.of(11, 0);
    private static final LocalTime SLOT_1130 = LocalTime.of(11, 30);
    private static final LocalTime SLOT_1400 = LocalTime.of(14, 0);
    private static final LocalTime SLOT_1430 = LocalTime.of(14, 30);
    private static final String DEFAULT_CHANNEL = "PHONE";
    private static final String GIMCHEON_JEUNGSAN = "GIMCHEON_JEUNGSAN";
    private static final String ADDRESS_JANGJEON_CLINIC = "경북 김천시 증산면 장전1길 69";
    private static final String ADDRESS_JANGJEON_SIDE = "경북 김천시 증산면 장전3길 19";
    private static final String ADDRESS_HWANGJEOM_MAIN = "경북 김천시 증산면 원황점길 422";
    private static final String ADDRESS_HWANGJEOM_VALLEY = "경북 김천시 증산면 원황점길 436-33";
    private static final String ADDRESS_GEUMGOK_MAIN = "경북 김천시 증산면 금곡리2길 89";
    private static final String ADDRESS_GEUMGOK_SUB = "경북 김천시 증산면 금곡리2길 29-20";

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

        User adminUser = ensureApprovedUser("seed_admin", "로컬 관리자", Role.ADMIN, null);
        User approvedGuardianUser = ensureApprovedUser("seed_guardian_approved", "보호자 승인 계정", Role.GUARDIAN, adminUser);
        User pendingGuardianUser = ensurePendingUser("seed_guardian_pending", "보호자 승인 대기", Role.GUARDIAN);
        User pendingDoctorUser = ensurePendingUser("seed_doc_pending_kang", "강대기의사", Role.DOCTOR);
        DoctorProfile doctorKim = ensureDoctor(
                adminUser, "seed_doc_im_kim", "김의사", "INTERNAL_MEDICINE", "내과");
        DoctorProfile doctorPark = ensureDoctor(
                adminUser, "seed_doc_im_park", "박의사", "INTERNAL_MEDICINE", "내과");
        DoctorProfile doctorLee = ensureDoctor(
                adminUser, "seed_doc_derm_lee", "이의사", "DERMATOLOGY", "피부과");
        DoctorProfile doctorChoi = ensureDoctor(
                adminUser, "seed_doc_ortho_choi", "최의사", "ORTHOPEDICS", "정형외과");
        DoctorProfile doctorJung = ensureDoctor(
                adminUser, "seed_doc_neuro_jung", "정의사", "NEUROLOGY", "신경과");
        DoctorProfile doctorHan = ensureDoctor(
                adminUser, "seed_doc_eye_han", "한의사", "OPHTHALMOLOGY", "안과");
        ensureDoctorProfile(pendingDoctorUser, "FAMILY_MEDICINE", "가정의학과");

        Patient completedCasePatient = ensurePatient(
                "홍길동", LocalDate.of(1958, 3, 15), GIMCHEON_JEUNGSAN, ADDRESS_JANGJEON_CLINIC, "01049163720",
                "seed/patients/hong-gildong-reference.jpg", adminUser);
        Patient dispatchedCasePatient = ensurePatient(
                "김영희", LocalDate.of(1964, 8, 21), GIMCHEON_JEUNGSAN, ADDRESS_HWANGJEOM_MAIN, "01055554444",
                "seed/patients/kim-younghee-reference.jpg", adminUser);
        Patient enRouteCasePatient = ensurePatient(
                "박순자", LocalDate.of(1951, 11, 2), GIMCHEON_JEUNGSAN, ADDRESS_GEUMGOK_MAIN, "01033337777",
                "seed/patients/park-soonja-reference.jpg", adminUser);
        Patient verifyingCasePatient = ensurePatient(
                "이철수", LocalDate.of(1956, 6, 27), GIMCHEON_JEUNGSAN, ADDRESS_JANGJEON_SIDE, "01066668888",
                "seed/patients/lee-cheolsu-reference.jpg", adminUser);
        Patient consultingCasePatient = ensurePatient(
                "최말순", LocalDate.of(1949, 1, 8), GIMCHEON_JEUNGSAN, ADDRESS_HWANGJEOM_VALLEY, "01077779999",
                "seed/patients/choi-malsun-reference.jpg", adminUser);
        Patient returningCasePatient = ensurePatient(
                "정미숙", LocalDate.of(1961, 4, 18), GIMCHEON_JEUNGSAN, ADDRESS_GEUMGOK_SUB, "01088886666",
                "seed/patients/jung-misuk-reference.jpg", adminUser);

        ensureGuardianLink(dispatchedCasePatient, approvedGuardianUser, "DAUGHTER", GuardianLinkStatus.APPROVED, adminUser);
        ensureGuardianLink(enRouteCasePatient, approvedGuardianUser, "NEPHEW", GuardianLinkStatus.APPROVED, adminUser);
        ensureGuardianLink(completedCasePatient, pendingGuardianUser, "SON", GuardianLinkStatus.PENDING, adminUser);
        ensureGuardianLink(consultingCasePatient, pendingGuardianUser, "DAUGHTER_IN_LAW", GuardianLinkStatus.PENDING, adminUser);

        seedDepartmentSlots(List.of(
                doctorKim,
                doctorPark,
                doctorLee,
                doctorChoi,
                doctorJung,
                doctorHan
        ), today);

        ScheduleSlot pastPreferredSlot = ensureSlot(doctorKim, today.minusDays(30), SLOT_1000, SLOT_1030);
        IntakeSession pastCompletedIntake = ensureIntakeSession(
                completedCasePatient,
                "01049163720",
                "INTERNAL_MEDICINE",
                "내과",
                "혈압 관리와 기존 처방 약 복용 상담",
                List.of(pastPreferredSlot.getPublicId()),
                CompletionReason.BOOKING_CREATED
        );
        Booking pastCompletedBooking = ensureBooking(completedCasePatient, pastPreferredSlot, BookingStatus.COMPLETED, pastCompletedIntake);
        CareCase pastCase = ensureCareCase(pastCompletedBooking, pastCompletedIntake);
        ensureMission(
                pastCase,
                "GIMCHEON-01",
                completedCasePatient.getAddress(),
                MissionPhase.COMPLETED,
                bd("35.8672000"),
                bd("128.0589000")
        );
        ConsultationSession pastSession = ensureConsultationSession(
                pastCase,
                "seed-room-jangjeon-completed",
                "ws://localhost:7880",
                ConsultationSessionStatus.COMPLETED,
                18
        );
        ensureConsultationSummary(
                pastSession,
                "혈압 및 기초 문진 확인 후 만성질환 약 처방 유지.",
                true,
                "기존 복용약 14일분 유지",
                true
        );

        ScheduleSlot futureExistingSlot = ensureSlot(doctorPark, today.plusDays(2), SLOT_0900, SLOT_0930);
        IntakeSession futureConfirmedIntake = ensureIntakeSession(
                dispatchedCasePatient,
                "01055554444",
                "INTERNAL_MEDICINE",
                "내과",
                "만성질환 경과 확인과 재진 예약",
                List.of(futureExistingSlot.getPublicId()),
                CompletionReason.BOOKING_CREATED
        );
        Booking futureConfirmedBooking = ensureBooking(dispatchedCasePatient, futureExistingSlot, BookingStatus.CONFIRMED, futureConfirmedIntake);
        CareCase futureCase = ensureCareCase(futureConfirmedBooking, futureConfirmedIntake);
        ensureMission(
                futureCase,
                "GIMCHEON-02",
                dispatchedCasePatient.getAddress(),
                MissionPhase.DISPATCHED,
                bd("35.8506398"),
                bd("128.0542159")
        );
        ensureConsultationSession(
                futureCase,
                "seed-room-hwangjeom-dispatched",
                "ws://localhost:7880",
                ConsultationSessionStatus.READY,
                null
        );

        ScheduleSlot enRouteSlot = ensureSlot(doctorLee, today.plusDays(1), SLOT_1100, SLOT_1130);
        IntakeSession enRouteIntake = ensureIntakeSession(
                enRouteCasePatient,
                "01033337777",
                "DERMATOLOGY",
                "피부과",
                "팔과 목 부위 발진 악화",
                List.of(enRouteSlot.getPublicId()),
                CompletionReason.BOOKING_CREATED
        );
        Booking enRouteBooking = ensureBooking(enRouteCasePatient, enRouteSlot, BookingStatus.CONFIRMED, enRouteIntake);
        CareCase enRouteCase = ensureCareCase(enRouteBooking, enRouteIntake);
        ensureMission(
                enRouteCase,
                "GIMCHEON-03",
                enRouteCasePatient.getAddress(),
                MissionPhase.EN_ROUTE,
                bd("35.8822245"),
                bd("128.0461659")
        );
        ensureConsultationSession(
                enRouteCase,
                "seed-room-geumgok-enroute",
                "ws://localhost:7880",
                ConsultationSessionStatus.READY,
                null
        );

        ScheduleSlot verifyingSlot = ensureSlot(doctorChoi, today.plusDays(1), SLOT_1400, SLOT_1430);
        IntakeSession verifyingIntake = ensureIntakeSession(
                verifyingCasePatient,
                "01066668888",
                "ORTHOPEDICS",
                "정형외과",
                "무릎 통증과 보행 불편",
                List.of(verifyingSlot.getPublicId()),
                CompletionReason.BOOKING_CREATED
        );
        Booking verifyingBooking = ensureBooking(verifyingCasePatient, verifyingSlot, BookingStatus.CONFIRMED, verifyingIntake);
        CareCase verifyingCase = ensureCareCase(verifyingBooking, verifyingIntake);
        ensureMission(
                verifyingCase,
                "GIMCHEON-04",
                verifyingCasePatient.getAddress(),
                MissionPhase.VERIFYING,
                bd("35.8679186"),
                bd("128.0592880")
        );
        ensureConsultationSession(
                verifyingCase,
                "seed-room-jangjeon-verifying",
                "ws://localhost:7880",
                ConsultationSessionStatus.READY,
                null
        );

        ScheduleSlot consultingSlot = ensureSlot(doctorJung, today.plusDays(3), SLOT_1000, SLOT_1030);
        IntakeSession consultingIntake = ensureIntakeSession(
                consultingCasePatient,
                "01077779999",
                "NEUROLOGY",
                "신경과",
                "어지럼과 두통이 반복됨",
                List.of(consultingSlot.getPublicId()),
                CompletionReason.BOOKING_CREATED
        );
        Booking consultingBooking = ensureBooking(consultingCasePatient, consultingSlot, BookingStatus.CONFIRMED, consultingIntake);
        CareCase consultingCase = ensureCareCase(consultingBooking, consultingIntake);
        ensureMission(
                consultingCase,
                "GIMCHEON-05",
                consultingCasePatient.getAddress(),
                MissionPhase.CONSULTING,
                bd("35.8494890"),
                bd("128.0538504")
        );
        ensureConsultationSession(
                consultingCase,
                "seed-room-hwangjeom-consulting",
                "ws://localhost:7880",
                ConsultationSessionStatus.IN_PROGRESS,
                null
        );

        ScheduleSlot returningSlot = ensureSlot(doctorHan, today.minusDays(3), SLOT_1400, SLOT_1430);
        IntakeSession returningIntake = ensureIntakeSession(
                returningCasePatient,
                "01088886666",
                "OPHTHALMOLOGY",
                "안과",
                "시야 흐림과 안구 건조감",
                List.of(returningSlot.getPublicId()),
                CompletionReason.BOOKING_CREATED
        );
        Booking returningBooking = ensureBooking(returningCasePatient, returningSlot, BookingStatus.COMPLETED, returningIntake);
        CareCase returningCase = ensureCareCase(returningBooking, returningIntake);
        ensureMission(
                returningCase,
                "GIMCHEON-06",
                returningCasePatient.getAddress(),
                MissionPhase.RETURNING,
                bd("35.8828819"),
                bd("128.0431673")
        );
        ConsultationSession returningSession = ensureConsultationSession(
                returningCase,
                "seed-room-geumgok-returning",
                "ws://localhost:7880",
                ConsultationSessionStatus.COMPLETED,
                12
        );
        ensureConsultationSummary(
                returningSession,
                "안구 건조증 완화제 처방 후 2주 뒤 재평가 안내.",
                true,
                "인공눈물 1일 4회 점안",
                true
        );

        log.info(
                "Local dummy data seeded. adminUsername={}, defaultPassword={}, samplePatientPhones={}, approvedGuardianUsername={}, pendingGuardianUsername={}, pendingDoctorUsername={}",
                adminUser.getUsername(),
                defaultPassword,
                List.of(
                        completedCasePatient.getPhone(),
                        dispatchedCasePatient.getPhone(),
                        enRouteCasePatient.getPhone(),
                        verifyingCasePatient.getPhone(),
                        consultingCasePatient.getPhone(),
                        returningCasePatient.getPhone()
                ),
                approvedGuardianUser.getUsername(),
                pendingGuardianUser.getUsername(),
                pendingDoctorUser.getUsername()
        );
    }

    private void seedDepartmentSlots(List<DoctorProfile> doctors, LocalDate today) {
        for (int offset = 1; offset <= 5; offset++) {
            LocalDate slotDate = today.plusDays(offset);
            for (DoctorProfile doctor : doctors) {
                ensureSlot(doctor, slotDate, SLOT_1000, SLOT_1030);
                ensureSlot(doctor, slotDate, SLOT_1400, SLOT_1430);
            }
        }

        ensureSlot(doctors.get(1), today.plusDays(1), SLOT_0900, SLOT_0930);
        ensureSlot(doctors.get(2), today.plusDays(1), SLOT_1100, SLOT_1130);
    }

    private User ensureApprovedUser(String username, String name, Role role, User approver) {
        return ensureUser(username, name, role, approver, true);
    }

    private User ensurePendingUser(String username, String name, Role role) {
        return ensureUser(username, name, role, null, false);
    }

    private User ensureUser(String username, String name, Role role, User approver, boolean approved) {
        User user = userRepository.findByUsername(username).orElseGet(() -> userRepository.save(
                User.builder()
                        .username(username)
                        .passwordHash(passwordEncoder.encode(defaultPassword))
                        .name(name)
                        .role(role)
                        .build()
        ));

        if (approved && user.getApprovalStatus() != ApprovalStatus.APPROVED) {
            user.approve(approver);
        }

        return user;
    }

    private DoctorProfile ensureDoctor(User approver, String username, String name, String department, String departmentName) {
        User user = ensureApprovedUser(username, name, Role.DOCTOR, approver);
        return ensureDoctorProfile(user, department, departmentName);
    }

    private DoctorProfile ensureDoctorProfile(User user, String department, String departmentName) {
        return doctorProfileRepository.findByUserUsername(user.getUsername()).orElseGet(() -> doctorProfileRepository.save(
                DoctorProfile.builder()
                        .user(user)
                        .department(department)
                        .departmentName(departmentName)
                        .build()
        ));
    }

    private Patient ensurePatient(String name, LocalDate birthDate, String regionCode, String address, String phone,
                                  String referenceImagePath, User uploadedBy) {
        String birthDate6 = birthDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        Patient patient = patientRepository.findByPhone(phone)
                .or(() -> patientRepository.findAllByNameAndBirthDate6(name, birthDate6).stream().findFirst())
                .orElseGet(() -> {
                    return patientRepository.save(
                            Patient.builder()
                                    .name(name)
                                    .birthDate(birthDate)
                                    .regionCode(regionCode)
                                    .address(address)
                                    .phone(phone)
                                    .build()
                    );
                });

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

        if (targetStatus == GuardianLinkStatus.APPROVED && link.getStatus() != GuardianLinkStatus.APPROVED) {
            link.approve(approver);
        }

        if (targetStatus == GuardianLinkStatus.REJECTED && link.getStatus() != GuardianLinkStatus.REJECTED) {
            link.reject(approver);
        }

        return link;
    }

    private IntakeSession ensureIntakeSession(Patient patient, String callerNumber,
                                              String department, String departmentName,
                                              String selectionReason,
                                              List<String> offeredSlotIds, CompletionReason completionReason) {
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

    private Booking ensureBooking(Patient patient, ScheduleSlot slot, BookingStatus targetStatus, IntakeSession intakeSession) {
        Booking booking = bookingRepository.findBySlot(slot).orElseGet(() -> {
            if (!slot.isBooked()) {
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

        if (!slot.isBooked()) {
            slot.markBooked();
        }

        if (intakeSession != null && (booking.getIntakeSession() == null
                || !booking.getIntakeSession().getId().equals(intakeSession.getId()))) {
            entityManager.createQuery("update Booking b set b.intakeSession = :intakeSession where b.id = :id")
                    .setParameter("intakeSession", intakeSession)
                    .setParameter("id", booking.getId())
                    .executeUpdate();
            entityManager.flush();
        }

        if (targetStatus == BookingStatus.COMPLETED && booking.getStatus() != BookingStatus.COMPLETED) {
            entityManager.createQuery("update Booking b set b.status = :status where b.id = :id")
                    .setParameter("status", BookingStatus.COMPLETED)
                    .setParameter("id", booking.getId())
                    .executeUpdate();
            entityManager.flush();
        }

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
            entityManager.createQuery("update CareCase c set c.intakeSession = :intakeSession where c.id = :id")
                    .setParameter("intakeSession", intakeSession)
                    .setParameter("id", careCase.getId())
                    .executeUpdate();
            entityManager.flush();
        }

        return careCase;
    }

    private Mission ensureMission(CareCase careCase, String vehicleId, String destination,
                                  MissionPhase targetPhase, BigDecimal latitude, BigDecimal longitude) {
        Mission mission = missionRepository.findByCareCase(careCase).orElseGet(() -> missionRepository.save(
                Mission.builder()
                        .careCase(careCase)
                        .vehicleId(vehicleId)
                        .destination(destination)
                        .build()
        ));

        if (latitude != null && longitude != null) {
            mission.updateLocation(latitude, longitude);
        }

        if (mission.getPhase() != targetPhase) {
            mission.updatePhase(targetPhase);
        }

        return mission;
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

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
