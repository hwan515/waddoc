package com.waddoc.domain.booking.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.dto.*;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.booking.repository.BookingRepository;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.doctor.entity.ScheduleSlot;
import com.waddoc.domain.doctor.repository.ScheduleSlotRepository;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.sms.SmsService;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class BookingService {

    private static final DateTimeFormatter BOOKING_SMS_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd, HH:mm");

    private final IntakeSessionRepository intakeSessionRepository;
    private final ScheduleSlotRepository scheduleSlotRepository;
    private final BookingRepository bookingRepository;
    private final CareCaseRepository careCaseRepository;
    private final AuditLogService auditLogService;
    private final SmsService smsService;

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

        ScheduleSlot slot = scheduleSlotRepository.findByPublicId(request.getSlotId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SLOT_NOT_FOUND));

        if (!session.isSlotOffered(request.getSlotId())) {
            throw new BusinessException(ErrorCode.SLOT_NOT_IN_RECOMMENDATION);
        }

        if (slot.isBooked()) {
            throw new BusinessException(ErrorCode.BOOKING_SLOT_CONFLICT);
        }

        slot.markBooked();

        Booking booking = Booking.builder()
                .patient(patient)
                .intakeSession(session)
                .slot(slot)
                .doctor(slot.getDoctor())
                .channel(session.getChannel().name())
                .appointmentDate(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .build();

        try {
            bookingRepository.save(booking);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.BOOKING_SLOT_CONFLICT);
        }

        CareCase careCase = CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(slot.getDoctor())
                .intakeSession(session)
                .build();
        careCaseRepository.save(careCase);

        session.touch();

        // TTS 메시지 생성
        String doctorName = slot.getDoctor().getUser().getName();
        String departmentName = slot.getDoctor().getDepartmentName();
        int month = slot.getSlotDate().getMonthValue();
        int day = slot.getSlotDate().getDayOfMonth();
        int hour = slot.getStartTime().getHour();
        String amPm = hour < 12 ? "오전" : "오후";
        int displayHour = hour <= 12 ? hour : hour - 12;
        String ttsMessage = String.format(
                "%s %s 선생님, %d월 %d일 %s %d시 예약이 완료되었습니다.",
                departmentName, doctorName, month, day, amPm, displayHour);
        String smsMessage = buildBookingCreatedSms(patient, slot, doctorName, departmentName);

        sendSmsSafely(session.getCallerNumber(), smsMessage, booking.getPublicId(), "BOOKING_CREATED");

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

        return cancelBookingInternal(booking, request, "SYSTEM", "SYSTEM", session.getCallerNumber());
    }

    /** 4.5 — 인증 기반 예약 취소 */
    @Transactional
    public CancelBookingResponse cancelBooking(String bookingId, CancelBookingRequest request,
                                                String actorId, String actorRole) {
        Booking booking = bookingRepository.findByPublicId(bookingId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOKING_NOT_FOUND));

        String callerNumber = booking.getPatient().getPhone();

        return cancelBookingInternal(booking, request,
                actorId != null ? actorId : "SYSTEM",
                actorRole != null ? actorRole : "SYSTEM",
                callerNumber);
    }

    private CancelBookingResponse cancelBookingInternal(Booking booking, CancelBookingRequest request,
                                                         String actorId, String actorRole, String callerNumber) {
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

        sendSmsSafely(callerNumber, ttsMessage, booking.getPublicId(), "BOOKING_CANCELLED");

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

    private void sendSmsSafely(String recipientPhone, String message, String bookingId, String eventType) {
        if (recipientPhone == null || recipientPhone.isBlank()) {
            return;
        }

        try {
            smsService.send(recipientPhone, message);
        } catch (Exception e) {
            log.error("SMS send failed. eventType={}, bookingId={}, recipientPhone={}",
                    eventType, bookingId, recipientPhone, e);
        }
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
}
