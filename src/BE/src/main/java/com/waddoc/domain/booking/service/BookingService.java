package com.waddoc.domain.booking.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.dto.*;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.booking.repository.BookingRepository;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.dispatch.entity.DispatchOutbox;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.domain.dispatch.service.WaypointAddressResolver;
import com.waddoc.domain.doctor.entity.ScheduleSlot;
import com.waddoc.domain.doctor.repository.ScheduleSlotRepository;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
import com.waddoc.domain.notification.dto.NewBookingNotificationPayload;
import com.waddoc.domain.notification.event.SmsRequestMessage;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.service.MissionCommandService;
import com.waddoc.domain.vehicle.repository.VehicleRepository;
import com.waddoc.global.config.KafkaTopics;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.sms.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 예약 생성 시 슬롯 선점부터 케이스/배차 준비, 알림 발행까지 한 트랜잭션 흐름으로 묶는다.
 */
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final DateTimeFormatter BOOKING_SMS_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd, HH:mm");

    private final IntakeSessionRepository intakeSessionRepository;
    private final ScheduleSlotRepository scheduleSlotRepository;
    private final BookingRepository bookingRepository;
    private final CareCaseRepository careCaseRepository;
    private final DispatchOutboxRepository dispatchOutboxRepository;
    private final MissionCommandService missionCommandService;
    private final AuditLogService auditLogService;
    private final SmsService smsService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final VehicleRepository vehicleRepository;
    private final WaypointAddressResolver waypointAddressResolver;

    /** 4.1 — 예약 생성 */
    @Transactional
    public CreateBookingResponse createBooking(String sessionId, CreateBookingRequest request) {
        IntakeSession session = findActiveSession(sessionId);

        Patient patient = session.getPatient();
        if (patient == null) {
            throw new BusinessException(ErrorCode.PATIENT_NOT_BOUND);
        }

        if (!session.hasRecommendation()) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_NOT_FOUND);
        }

        lockRegionCapacity(patient.getRegionCode());

        ScheduleSlot slot = scheduleSlotRepository.findByPublicId(request.getSlotId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));

        if (!session.isSlotOffered(request.getSlotId())) {
            throw new BusinessException(ErrorCode.SLOT_NOT_IN_RECOMMENDATION);
        }

        if (!isSlotBookable(slot, java.time.LocalDate.now(), java.time.LocalTime.now())) {
            throw new BusinessException(ErrorCode.BOOKING_SLOT_EXPIRED);
        }

        if (slot.isBooked()) {
            throw new BusinessException(ErrorCode.BOOKING_SLOT_CONFLICT);
        }

        if (hasActiveRegionBookingConflict(patient.getRegionCode(), slot.getSlotDate(), slot.getStartTime(), slot.getEndTime())) {
            throw new BusinessException(ErrorCode.BOOKING_VEHICLE_CONFLICT);
        }

        slot.markBooked();

        Booking booking = Booking.builder()
                .patient(patient)
                .intakeSession(session)
                .slot(slot)
                .doctor(slot.getDoctor())
                .channel(session.getChannel().name())
                .appointmentDate(slot.getSlotDate())
                .regionCode(patient.getRegionCode())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .build();

        try {
            bookingRepository.save(booking);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(resolveBookingConflictErrorCode(e));
        }

        CareCase careCase = CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(slot.getDoctor())
                .intakeSession(session)
                .build();
        careCaseRepository.save(careCase);
        // 배차 요청은 DB에 먼저 적재하고, 별도 relay가 Kafka로 내보낸다.
        DispatchOutbox dispatchOutbox = dispatchOutboxRepository.save(DispatchOutbox.builder()
                .careCase(careCase)
                .regionCode(patient.getRegionCode())
                .destination(patient.getAddress())
                .build());
        ensureCreatedMission(careCase, patient);

        session.touch();

        // TTS 메시지 생성
        String doctorName = slot.getDoctor().getUser().getName();
        String departmentName = slot.getDoctor().getDepartmentName();
        int month = slot.getSlotDate().getMonthValue();
        int day = slot.getSlotDate().getDayOfMonth();
        String ttsMessage = String.format(
                "%s %s 선생님, %d월 %d일 %s 예약이 완료되었습니다.",
                departmentName, doctorName, month, day, formatTimeForTts(slot.getStartTime()));
        String smsMessage = buildBookingCreatedSms(patient, slot, doctorName, departmentName);
        NewBookingNotificationPayload notificationPayload = NewBookingNotificationPayload.from(booking, careCase);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 예약 생성 트랜잭션이 확정된 뒤에만 외부 알림을 발행한다.
                    publishBookingCreatedSms(session.getCallerNumber(), smsMessage, booking.getPublicId());
                    publishBookingCreatedDoctorNotification(slot.getDoctor().getPublicId(), notificationPayload);
                }
            });
        } else {
            // Mockito 단위 테스트나 직접 호출 경로에서는 트랜잭션 동기화가 없을 수 있다.
            publishBookingCreatedSms(session.getCallerNumber(), smsMessage, booking.getPublicId());
            publishBookingCreatedDoctorNotification(slot.getDoctor().getPublicId(), notificationPayload);
        }

        // 감사 로그
        String correlationId = "corr_bk_" + booking.getPublicId();
        auditLogService.log(
                "BOOKING_CREATED",
                "BOOKING",
                booking.getPublicId(),
                correlationId,
                Map.of("patientId", patient.getPublicId(),
                       "slotId", slot.getPublicId(),
                       "doctorId", slot.getDoctor().getPublicId(),
                       "sessionId", session.getPublicId())
        );
        auditLogService.log(
                "CASE_CREATED",
                "CARE_CASE",
                careCase.getPublicId(),
                correlationId,
                Map.of("bookingId", booking.getPublicId(),
                       "patientId", patient.getPublicId())
        );

        return CreateBookingResponse.of(booking, careCase, ttsMessage);
    }

    /** 4.2 — 기존 예약 조회 */
    @Transactional(readOnly = true)
    public BookingListResponse getExistingBookings(String sessionId, String status) {
        IntakeSession session = intakeSessionRepository.findByPublicId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTAKE_SESSION_NOT_FOUND));

        Patient patient = session.getPatient();
        if (patient == null) {
            throw new BusinessException(ErrorCode.PATIENT_NOT_BOUND);
        }

        List<Booking> bookings;
        if (status != null && !status.isBlank()) {
            BookingStatus bookingStatus = BookingStatus.valueOf(status.toUpperCase());
            bookings = bookingRepository.findByPatientAndStatus(patient, bookingStatus);
        } else {
            bookings = bookingRepository.findByPatient(patient);
        }

        List<BookingSummaryResponse> summaries = bookings.stream()
                .map(BookingSummaryResponse::from)
                .toList();

        return BookingListResponse.of(summaries);
    }

    /** 4.3 — 예약 상세 조회 (인증 기반) */
    @Transactional(readOnly = true)
    public BookingDetailResponse getBookingDetail(String bookingId, String actorId, String actorRole) {
        Booking booking = bookingRepository.findByPublicId(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));
        String caseId = careCaseRepository.findByBooking(booking)
                .map(CareCase::getPublicId)
                .orElse(null);

        String phone = booking.getPatient().getPhone();

        // 감사 로그
        String correlationId = "corr_bk_" + booking.getPublicId();
        auditLogService.log(
                "BOOKING_VIEWED",
                "BOOKING",
                booking.getPublicId(),
                correlationId,
                actorId != null ? actorId : "SYSTEM",
                actorRole != null ? actorRole : "SYSTEM",
                Map.of("viewedBy", actorId != null ? actorId : "SYSTEM")
        );

        return BookingDetailResponse.from(booking, phone, caseId);
    }

    /** 4.4 — 세션 기반 예약 취소 (무인증, 시뮬레이터) */
    @Transactional
    public CancelBookingResponse cancelBookingBySession(String sessionId, String bookingId, CancelBookingRequest request) {
        IntakeSession session = findActiveSession(sessionId);

        Patient patient = session.getPatient();
        if (patient == null) {
            throw new BusinessException(ErrorCode.PATIENT_NOT_BOUND);
        }

        Booking booking = bookingRepository.findByPublicId(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));

        if (!booking.getPatient().getId().equals(patient.getId())) {
            throw new BusinessException(ErrorCode.PATIENT_MISMATCH);
        }

        return cancelBookingInternal(booking, request, "SYSTEM", "SYSTEM");
    }

    /** 4.5 — 인증 기반 예약 취소 */
    @Transactional
    public CancelBookingResponse cancelBooking(String bookingId, CancelBookingRequest request,
                                                String actorId, String actorRole) {
        Booking booking = bookingRepository.findByPublicId(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));

        return cancelBookingInternal(booking, request,
                actorId != null ? actorId : "SYSTEM",
                actorRole != null ? actorRole : "SYSTEM");
    }

    private CancelBookingResponse cancelBookingInternal(Booking booking, CancelBookingRequest request,
                                                         String actorId, String actorRole) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new BusinessException(ErrorCode.BOOKING_ALREADY_CANCELLED);
        }
        if (!booking.isCancellable()) {
            throw new BusinessException(ErrorCode.BOOKING_NOT_CANCELLABLE);
        }

        String reason = request != null ? request.getCancelReason() : null;
        booking.cancel(reason);
        booking.getSlot().markAvailable();

        careCaseRepository.findByBooking(booking).ifPresent(CareCase::cancel);

        String ttsMessage = "예약이 취소되었습니다.";

        // 감사 로그
        String correlationId = "corr_bk_" + booking.getPublicId();
        auditLogService.log(
                "BOOKING_CANCELLED",
                "BOOKING",
                booking.getPublicId(),
                correlationId,
                actorId,
                actorRole,
                Map.of("cancelReason", reason != null ? reason : "",
                       "patientId", booking.getPatient().getPublicId())
        );

        return CancelBookingResponse.from(booking, ttsMessage);
    }

    private IntakeSession findActiveSession(String sessionId) {
        IntakeSession session = intakeSessionRepository.findByPublicId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTAKE_SESSION_NOT_FOUND));

        if (!session.isActive()) {
            throw new BusinessException(ErrorCode.SESSION_STATE_INVALID);
        }

        return session;
    }

    private void publishBookingCreatedSms(String recipientPhone, String message, String bookingId) {
        if (recipientPhone == null || recipientPhone.isBlank()) {
            return;
        }

        kafkaTemplate.send(
                KafkaTopics.SMS_REQUESTS_TOPIC,
                new SmsRequestMessage(recipientPhone, message, bookingId)
        );
    }

    private void publishBookingCreatedDoctorNotification(String doctorId, NewBookingNotificationPayload payload) {
        kafkaTemplate.send(KafkaTopics.DOCTOR_NOTIFICATIONS_TOPIC, doctorId, payload);
    }

    private String buildBookingCreatedSms(Patient patient, ScheduleSlot slot, String doctorName, String departmentName) {
        String contactNumber = normalizeDisplayPhone(smsService.getContactNumber());
        String appointmentDateTime = LocalDateTime.of(slot.getSlotDate(), slot.getStartTime())
                .format(BOOKING_SMS_DATE_TIME_FORMATTER);

        return String.format(
                "예약 확인%n[왔닥]%n안녕하세요, %s님.%n진료 예약이 아래와 같이 확정되었습니다.%n%n일시: %s%n의사: %s (%s)%n※ 유의사항%n%n예약 시간 10분 전까지 준비 부탁드립니다.%n변경이나 취소를 원하실 경우 최소 하루 전까지 연락 주시기 바랍니다.%n%n☎ 문의: %s",
                patient.getName(),
                appointmentDateTime,
                doctorName,
                departmentName,
                contactNumber
        );
    }

    private String normalizeDisplayPhone(String phoneNumber) {
        if (phoneNumber == null) {
            return "";
        }
        return phoneNumber.replaceAll("[^0-9]", "");
    }

    private String formatTimeForTts(java.time.LocalTime time) {
        int hour = time.getHour();
        String amPm = hour < 12 ? "오전" : "오후";
        int displayHour = hour == 0 ? 12 : (hour <= 12 ? hour : hour - 12);
        int minute = time.getMinute();

        // 30분 단위 슬롯이 많아서 "9시"와 "9시 30분"을 명확히 구분해 읽어준다.
        if (minute == 0) {
            return String.format("%s %d시", amPm, displayHour);
        }
        return String.format("%s %d시 %d분", amPm, displayHour, minute);
    }

    private Mission ensureCreatedMission(CareCase careCase, Patient patient) {
        WaypointAddressResolver.ResolvedTarget resolvedTarget = waypointAddressResolver.resolve(patient.getAddress());
        return missionCommandService.createMissionForDispatch(
                careCase,
                null,
                patient.getAddress(),
                null,
                resolvedTarget.waypointNumber()
        );
    }

    private void lockRegionCapacity(String regionCode) {
        if (regionCode == null || regionCode.isBlank()) {
            return;
        }

        vehicleRepository.findFirstByRegionCodeOrderByCreatedAtAsc(regionCode);
    }

    private boolean hasActiveRegionBookingConflict(
            String regionCode,
            java.time.LocalDate appointmentDate,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime
    ) {
        if (regionCode == null || regionCode.isBlank()) {
            return false;
        }

        return bookingRepository.existsActiveRegionBookingConflict(
                regionCode,
                appointmentDate,
                startTime,
                endTime,
                BookingStatus.CANCELLED
        );
    }

    static boolean isSlotBookable(
            ScheduleSlot slot,
            java.time.LocalDate today,
            java.time.LocalTime currentTime
    ) {
        if (slot.getSlotDate().isAfter(today)) {
            return true;
        }
        if (slot.getSlotDate().isBefore(today)) {
            return false;
        }
        return slot.getStartTime().isAfter(currentTime);
    }

    private ErrorCode resolveBookingConflictErrorCode(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase();
                if (normalized.contains("uq_booking_region_date_start_active")) {
                    return ErrorCode.BOOKING_VEHICLE_CONFLICT;
                }
                if (normalized.contains("idx_booking_slot_active")) {
                    return ErrorCode.BOOKING_SLOT_CONFLICT;
                }
            }
            current = current.getCause();
        }
        return ErrorCode.BOOKING_SLOT_CONFLICT;
    }
}
