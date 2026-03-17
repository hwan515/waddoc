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

        Patient newBookingPatient = ensurePatient(
                "홍길동", LocalDate.of(1958, 3, 15), "ULLEUNG", "울릉군 북면", "01012345678",
                "seed/patients/hong-gildong-reference.jpg", adminUser);
        Patient existingBookingPatient = ensurePatient(
                "김영희", LocalDate.of(1964, 8, 21), "ULLEUNG", "울릉군 서면", "01055554444",
                "seed/patients/kim-younghee-reference.jpg", adminUser);

        ensureGuardianLink(existingBookingPatient, approvedGuardianUser, "DAUGHTER", GuardianLinkStatus.APPROVED, adminUser);
        ensureGuardianLink(newBookingPatient, pendingGuardianUser, "SON", GuardianLinkStatus.PENDING, adminUser);

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
                newBookingPatient,
                "01012345678",
                "INTERNAL_MEDICINE",
                "내과",
                List.of(pastPreferredSlot.getPublicId()),
                CompletionReason.BOOKING_CREATED
        );
        Booking pastCompletedBooking = ensureBooking(newBookingPatient, pastPreferredSlot, BookingStatus.COMPLETED, pastCompletedIntake);
        CareCase pastCase = ensureCareCase(pastCompletedBooking, pastCompletedIntake);
        ensureMission(
                pastCase,
                "ULLEUNG-01",
                newBookingPatient.getAddress(),
                MissionPhase.COMPLETED,
                new BigDecimal("37.4841000"),
                new BigDecimal("130.9055000")
        );
        ConsultationSession pastSession = ensureConsultationSession(
                pastCase,
                "seed-room-past-completed",
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
                existingBookingPatient,
                "01055554444",
                "INTERNAL_MEDICINE",
                "내과",
                List.of(futureExistingSlot.getPublicId()),
                CompletionReason.BOOKING_CREATED
        );
        Booking futureConfirmedBooking = ensureBooking(existingBookingPatient, futureExistingSlot, BookingStatus.CONFIRMED, futureConfirmedIntake);
        CareCase futureCase = ensureCareCase(futureConfirmedBooking, futureConfirmedIntake);
        ensureMission(
                futureCase,
                "ULLEUNG-02",
                existingBookingPatient.getAddress(),
                MissionPhase.DISPATCHED,
                new BigDecimal("37.4872000"),
                new BigDecimal("130.8999000")
        );
        ensureConsultationSession(
                futureCase,
                "seed-room-upcoming",
                "ws://localhost:7880",
                ConsultationSessionStatus.READY,
                null
        );

        log.info(
                "Local dummy data seeded. adminUsername={}, defaultPassword={}, newBookingPhone={}, existingBookingPhone={}, approvedGuardianUsername={}, pendingGuardianUsername={}, pendingDoctorUsername={}",
                adminUser.getUsername(),
                defaultPassword,
                "01012345678",
                "01055554444",
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
        Patient patient = patientRepository.findByPhone(phone)
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
                "로컬 시드용 진료과 선택 결과",
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
        return consultationSummaryRepository.findBySession(session).orElseGet(() ->
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
    }
}
