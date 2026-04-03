package com.waddoc.domain.booking.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.booking.dto.BookingDetailResponse;
import com.waddoc.domain.booking.dto.CreateBookingRequest;
import com.waddoc.domain.booking.dto.CreateBookingResponse;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.repository.BookingRepository;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.consultation.service.ConsultationLiveKitService;
import com.waddoc.domain.dispatch.entity.DispatchOutbox;
import com.waddoc.domain.dispatch.repository.DispatchOutboxRepository;
import com.waddoc.domain.dispatch.service.WaypointAddressResolver;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.doctor.entity.ScheduleSlot;
import com.waddoc.domain.doctor.repository.ScheduleSlotRepository;
import com.waddoc.domain.intake.entity.ConfidenceLevel;
import com.waddoc.domain.intake.entity.IntakeChannel;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
import com.waddoc.domain.notification.dto.NewBookingNotificationPayload;
import com.waddoc.domain.notification.event.SmsRequestMessage;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientGender;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.mission.service.MissionCommandService;
import com.waddoc.domain.vehicle.entity.Vehicle;
import com.waddoc.domain.vehicle.repository.VehicleRepository;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.config.KafkaTopics;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.sms.SmsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private IntakeSessionRepository intakeSessionRepository;

    @Mock
    private ScheduleSlotRepository scheduleSlotRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private CareCaseRepository careCaseRepository;

    @Mock
    private DispatchOutboxRepository dispatchOutboxRepository;

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private ConsultationSessionRepository consultationSessionRepository;

    @Mock
    private MissionCommandService missionCommandService;

    @Mock
    private ConsultationLiveKitService consultationLiveKitService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private SmsService smsService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private WaypointAddressResolver waypointAddressResolver;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void createBooking_publishesKafkaMessagesAndCreatesDispatchOutbox() {
        when(waypointAddressResolver.resolve("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69"))
                .thenReturn(new WaypointAddressResolver.ResolvedTarget(null));

        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded")
                .name("Doctor Kim")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("Internal Medicine")
                .build();

        ScheduleSlot slot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 30))
                .endTime(LocalTime.of(11, 0))
                .build();

        Patient patient = Patient.builder()
                .name("Patient Park")
                .birthDate(LocalDate.of(1958, 3, 15))
                .gender(PatientGender.FEMALE)
                .regionCode("ULLEUNG")
                .address("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69")
                .phone("01012345678")
                .build();

        IntakeSession session = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .channel(IntakeChannel.WEB_SIMULATOR)
                .build();
        Vehicle vehicle = Vehicle.builder()
                .code("ULLEUNG-01")
                .regionCode("ULLEUNG")
                .displayName("Ulleung vehicle 1")
                .build();
        session.recordSelection(
                "INTERNAL_MEDICINE",
                "Internal Medicine",
                ConfidenceLevel.HIGH,
                false,
                "department selected",
                List.of(slot.getPublicId())
        );

        CreateBookingRequest request = new CreateBookingRequest();
        ReflectionTestUtils.setField(request, "slotId", slot.getPublicId());

        when(intakeSessionRepository.findByPublicId(session.getPublicId())).thenReturn(Optional.of(session));
        when(vehicleRepository.findFirstByRegionCodeOrderByCreatedAtAsc("ULLEUNG")).thenReturn(Optional.of(vehicle));
        when(scheduleSlotRepository.findByPublicId(slot.getPublicId())).thenReturn(Optional.of(slot));
        when(bookingRepository.existsActiveRegionBookingConflict("ULLEUNG", slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(), com.waddoc.domain.booking.entity.BookingStatus.CANCELLED))
                .thenReturn(false);
        when(smsService.getContactNumber()).thenReturn("01049163720");
        doAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            ReflectionTestUtils.setField(booking, "createdAt", LocalDateTime.of(2026, 3, 18, 12, 0));
            return booking;
        }).when(bookingRepository).save(any(Booking.class));
        when(dispatchOutboxRepository.save(any(DispatchOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();

        CreateBookingResponse response;
        try {
            response = bookingService.createBooking(session.getPublicId(), request);

            List<TransactionSynchronization> synchronizations =
                    TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).hasSize(1);
            synchronizations.forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        ArgumentCaptor<SmsRequestMessage> smsCaptor = ArgumentCaptor.forClass(SmsRequestMessage.class);
        ArgumentCaptor<NewBookingNotificationPayload> notificationCaptor =
                ArgumentCaptor.forClass(NewBookingNotificationPayload.class);
        ArgumentCaptor<DispatchOutbox> outboxCaptor = ArgumentCaptor.forClass(DispatchOutbox.class);

        assertThat(response.getBookingId()).isNotBlank();
        assertThat(response.getCaseId()).isNotBlank();
        assertThat(response.getTtsMessage()).isNotBlank();
        assertThat(response.getTtsMessage()).contains("오전 10시 30분");
        verify(dispatchOutboxRepository).save(outboxCaptor.capture());
        verify(missionCommandService).createMissionForDispatch(
                any(CareCase.class),
                eq(null),
                eq("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69"),
                eq(null),
                eq(null)
        );
        verify(kafkaTemplate).send(eq(KafkaTopics.SMS_REQUESTS_TOPIC), smsCaptor.capture());
        verify(kafkaTemplate).send(
                eq(KafkaTopics.DOCTOR_NOTIFICATIONS_TOPIC),
                eq(response.getDoctor().getDoctorId()),
                notificationCaptor.capture()
        );

        DispatchOutbox savedOutbox = outboxCaptor.getValue();
        assertThat(savedOutbox.getRegionCode()).isEqualTo("ULLEUNG");
        assertThat(savedOutbox.getDestination()).isEqualTo("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69");
        assertThat(savedOutbox.getCareCase()).isNotNull();
        assertThat(savedOutbox.isCompleted()).isFalse();

        SmsRequestMessage smsEvent = smsCaptor.getValue();
        assertThat(smsEvent.correlationId()).isEqualTo(response.getBookingId());
        assertThat(smsEvent.recipientPhone()).isEqualTo("01012345678");
        assertThat(smsEvent.message()).contains(slot.getSlotDate() + ", 10:30");
        assertThat(smsEvent.message()).contains("Doctor Kim");

        NewBookingNotificationPayload payload = notificationCaptor.getValue();
        assertThat(payload.getType()).isEqualTo("NEW_BOOKING");
        assertThat(payload.getBookingId()).isEqualTo(response.getBookingId());
        assertThat(payload.getCaseId()).isEqualTo(response.getCaseId());
        assertThat(payload.getDoctorId()).isEqualTo(response.getDoctor().getDoctorId());
        assertThat(payload.getDoctorName()).isEqualTo(response.getDoctor().getName());
        assertThat(payload.getDepartmentName()).isEqualTo(response.getDoctor().getDepartmentName());
        assertThat(payload.getPatientId()).isEqualTo(response.getPatient().getPatientId());
        assertThat(payload.getPatientName()).isEqualTo(response.getPatient().getName());
        assertThat(payload.getPatientGender()).isEqualTo(PatientGender.FEMALE);
        assertThat(payload.getAppointmentDate()).isEqualTo(response.getAppointmentDate());
        assertThat(payload.getStartTime()).isEqualTo(response.getStartTime());
        assertThat(payload.getLocation()).isEqualTo("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69");
        assertThat(payload.getCreatedAt()).isEqualTo(response.getCreatedAt());
    }

    @Test
    void createBooking_rejectsStartedTodaySlot() {
        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded")
                .name("Doctor Kim")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("Internal Medicine")
                .build();

        ScheduleSlot slot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.now())
                .startTime(LocalTime.MIDNIGHT)
                .endTime(LocalTime.of(0, 30))
                .build();

        Patient patient = Patient.builder()
                .name("Patient Park")
                .birthDate(LocalDate.of(1958, 3, 15))
                .gender(PatientGender.FEMALE)
                .regionCode("ULLEUNG")
                .address("울릉군")
                .phone("01012345678")
                .build();

        IntakeSession session = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .channel(IntakeChannel.WEB_SIMULATOR)
                .build();
        Vehicle vehicle = Vehicle.builder()
                .code("ULLEUNG-01")
                .regionCode("ULLEUNG")
                .displayName("Ulleung vehicle 1")
                .build();
        session.recordSelection(
                "INTERNAL_MEDICINE",
                "Internal Medicine",
                ConfidenceLevel.HIGH,
                false,
                "department selected",
                List.of(slot.getPublicId())
        );

        CreateBookingRequest request = new CreateBookingRequest();
        ReflectionTestUtils.setField(request, "slotId", slot.getPublicId());

        when(intakeSessionRepository.findByPublicId(session.getPublicId())).thenReturn(Optional.of(session));
        when(vehicleRepository.findFirstByRegionCodeOrderByCreatedAtAsc("ULLEUNG")).thenReturn(Optional.of(vehicle));
        when(scheduleSlotRepository.findByPublicId(slot.getPublicId())).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> bookingService.createBooking(session.getPublicId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKING_SLOT_EXPIRED);

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void createBooking_doesNotAutoProvisionMissionAndSession() {
        when(waypointAddressResolver.resolve("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69"))
                .thenReturn(new WaypointAddressResolver.ResolvedTarget(null));

        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded")
                .name("Doctor Kim")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("Internal Medicine")
                .build();

        ScheduleSlot slot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        Patient patient = Patient.builder()
                .name("Patient Park")
                .birthDate(LocalDate.of(1958, 3, 15))
                .gender(PatientGender.FEMALE)
                .regionCode("ULLEUNG")
                .address("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69")
                .phone("01012345678")
                .build();

        IntakeSession session = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .channel(IntakeChannel.WEB_SIMULATOR)
                .build();
        Vehicle vehicle = Vehicle.builder()
                .code("ULLEUNG-01")
                .regionCode("ULLEUNG")
                .displayName("Ulleung vehicle 1")
                .build();
        session.recordSelection(
                "INTERNAL_MEDICINE",
                "Internal Medicine",
                ConfidenceLevel.HIGH,
                false,
                "department selected",
                List.of(slot.getPublicId())
        );

        CreateBookingRequest request = new CreateBookingRequest();
        ReflectionTestUtils.setField(request, "slotId", slot.getPublicId());

        when(intakeSessionRepository.findByPublicId(session.getPublicId())).thenReturn(Optional.of(session));
        when(vehicleRepository.findFirstByRegionCodeOrderByCreatedAtAsc("ULLEUNG")).thenReturn(Optional.of(vehicle));
        when(scheduleSlotRepository.findByPublicId(slot.getPublicId())).thenReturn(Optional.of(slot));
        when(bookingRepository.existsActiveRegionBookingConflict("ULLEUNG", slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(), com.waddoc.domain.booking.entity.BookingStatus.CANCELLED))
                .thenReturn(false);
        when(smsService.getContactNumber()).thenReturn("01049163720");
        doAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            ReflectionTestUtils.setField(booking, "createdAt", LocalDateTime.of(2026, 3, 21, 12, 0));
            return booking;
        }).when(bookingRepository).save(any(Booking.class));
        when(dispatchOutboxRepository.save(any(DispatchOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(missionCommandService.createMissionForDispatch(any(CareCase.class), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    CareCase careCase = invocation.getArgument(0);
                    Mission mission = Mission.builder()
                            .careCase(careCase)
                            .vehicleId(invocation.getArgument(1))
                            .destination(invocation.getArgument(2))
                            .dispatchedAt(invocation.getArgument(3))
                            .targetWaypointNumber(invocation.getArgument(4))
                            .build();
                    ReflectionTestUtils.setField(mission, "id", 1L);
                    return mission;
                });

        bookingService.createBooking(session.getPublicId(), request);

        ArgumentCaptor<DispatchOutbox> outboxCaptor = ArgumentCaptor.forClass(DispatchOutbox.class);

        verify(dispatchOutboxRepository).save(outboxCaptor.capture());
        verify(missionCommandService).createMissionForDispatch(
                any(CareCase.class),
                eq(null),
                eq("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69"),
                eq(null),
                eq(null)
        );
        verify(missionRepository, never()).save(any(Mission.class));
        verify(consultationLiveKitService, never()).createRoom(any());
        verify(consultationSessionRepository, never()).save(any(ConsultationSession.class));

        assertThat(outboxCaptor.getValue().isCompleted()).isFalse();
    }

    @Test
    void createBooking_assignsMappedWaypointWhenAddressIsSupported() {
        when(waypointAddressResolver.resolve("경상북도 김천시 증산면 장전4길 14"))
                .thenReturn(new WaypointAddressResolver.ResolvedTarget(59));

        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded")
                .name("Doctor Kim")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("Internal Medicine")
                .build();

        ScheduleSlot slot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        Patient patient = Patient.builder()
                .name("Patient Park")
                .birthDate(LocalDate.of(1958, 3, 15))
                .gender(PatientGender.FEMALE)
                .regionCode("GIMCHEON")
                .address("경상북도 김천시 증산면 장전4길 14")
                .phone("01012345678")
                .build();

        IntakeSession session = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .channel(IntakeChannel.WEB_SIMULATOR)
                .build();
        Vehicle vehicle = Vehicle.builder()
                .code("GIMCHEON-01")
                .regionCode("GIMCHEON")
                .displayName("Gimcheon vehicle 1")
                .build();
        session.recordSelection(
                "INTERNAL_MEDICINE",
                "Internal Medicine",
                ConfidenceLevel.HIGH,
                false,
                "department selected",
                List.of(slot.getPublicId())
        );

        CreateBookingRequest request = new CreateBookingRequest();
        ReflectionTestUtils.setField(request, "slotId", slot.getPublicId());

        when(intakeSessionRepository.findByPublicId(session.getPublicId())).thenReturn(Optional.of(session));
        when(vehicleRepository.findFirstByRegionCodeOrderByCreatedAtAsc("GIMCHEON")).thenReturn(Optional.of(vehicle));
        when(scheduleSlotRepository.findByPublicId(slot.getPublicId())).thenReturn(Optional.of(slot));
        when(bookingRepository.existsActiveRegionBookingConflict("GIMCHEON", slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(), com.waddoc.domain.booking.entity.BookingStatus.CANCELLED))
                .thenReturn(false);
        when(smsService.getContactNumber()).thenReturn("01049163720");
        doAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            ReflectionTestUtils.setField(booking, "createdAt", LocalDateTime.of(2026, 3, 25, 12, 0));
            return booking;
        }).when(bookingRepository).save(any(Booking.class));
        when(dispatchOutboxRepository.save(any(DispatchOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bookingService.createBooking(session.getPublicId(), request);

        verify(missionCommandService).createMissionForDispatch(
                any(CareCase.class),
                eq(null),
                eq("경상북도 김천시 증산면 장전4길 14"),
                eq(null),
                eq(59)
        );
    }

    @Test
    void getBookingDetail_includesCaseIdAndRegionCode() {
        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded")
                .name("Doctor Kim")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("Internal Medicine")
                .build();

        ScheduleSlot slot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.of(2026, 3, 11))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        Patient patient = Patient.builder()
                .name("Patient Park")
                .birthDate(LocalDate.of(1958, 3, 15))
                .gender(PatientGender.UNKNOWN)
                .regionCode("ULLEUNG")
                .address("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69")
                .phone("01012345678")
                .build();

        IntakeSession session = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .build();

        Booking booking = Booking.builder()
                .patient(patient)
                .intakeSession(session)
                .slot(slot)
                .doctor(doctor)
                .channel("WEB_SIMULATOR")
                .appointmentDate(LocalDate.of(2026, 3, 11))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();

        CareCase careCase = CareCase.builder()
                .booking(booking)
                .patient(patient)
                .doctor(doctor)
                .intakeSession(session)
                .build();

        when(bookingRepository.findByPublicId(booking.getPublicId())).thenReturn(Optional.of(booking));
        when(careCaseRepository.findByBooking(booking)).thenReturn(Optional.of(careCase));

        BookingDetailResponse response = bookingService.getBookingDetail(booking.getPublicId(), "usr_admin", "ADMIN");

        assertThat(response.getCaseId()).isEqualTo(careCase.getPublicId());
        assertThat(response.getIntakeSessionId()).isEqualTo(session.getPublicId());
        assertThat(response.getPatient().getRegionCode()).isEqualTo("ULLEUNG");
    }

    @Test
    void createBooking_rejectsConflictingRegionReservationBeforeSlotBooking() {
        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded")
                .name("Doctor Kim")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("Internal Medicine")
                .build();

        ScheduleSlot slot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(9, 0))
                .endTime(LocalTime.of(9, 30))
                .build();

        Patient patient = Patient.builder()
                .name("Patient Park")
                .birthDate(LocalDate.of(1958, 3, 15))
                .gender(PatientGender.FEMALE)
                .regionCode("ULLEUNG")
                .address("울릉군")
                .phone("01012345678")
                .build();

        IntakeSession session = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .channel(IntakeChannel.WEB_SIMULATOR)
                .build();
        Vehicle vehicle = Vehicle.builder()
                .code("ULLEUNG-01")
                .regionCode("ULLEUNG")
                .displayName("Ulleung vehicle 1")
                .build();
        session.recordSelection(
                "INTERNAL_MEDICINE",
                "Internal Medicine",
                ConfidenceLevel.HIGH,
                false,
                "department selected",
                List.of(slot.getPublicId())
        );

        CreateBookingRequest request = new CreateBookingRequest();
        ReflectionTestUtils.setField(request, "slotId", slot.getPublicId());

        when(intakeSessionRepository.findByPublicId(session.getPublicId())).thenReturn(Optional.of(session));
        when(vehicleRepository.findFirstByRegionCodeOrderByCreatedAtAsc("ULLEUNG")).thenReturn(Optional.of(vehicle));
        when(scheduleSlotRepository.findByPublicId(slot.getPublicId())).thenReturn(Optional.of(slot));
        when(bookingRepository.existsActiveRegionBookingConflict("ULLEUNG", slot.getSlotDate(), slot.getStartTime(), slot.getEndTime(), com.waddoc.domain.booking.entity.BookingStatus.CANCELLED))
                .thenReturn(true);

        assertThatThrownBy(() -> bookingService.createBooking(session.getPublicId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.BOOKING_VEHICLE_CONFLICT);

        assertThat(slot.isBooked()).isFalse();
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void cancelBooking_releasesSlotForFutureRegionCapacityReuse() {
        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded")
                .name("Doctor Kim")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("Internal Medicine")
                .build();

        ScheduleSlot slot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.now().plusDays(1))
                .startTime(LocalTime.of(10, 0))
                .endTime(LocalTime.of(10, 30))
                .build();
        slot.markBooked();

        Patient patient = Patient.builder()
                .name("Patient Park")
                .birthDate(LocalDate.of(1958, 3, 15))
                .gender(PatientGender.FEMALE)
                .regionCode("ULLEUNG")
                .address("울릉군")
                .phone("01012345678")
                .build();

        Booking booking = Booking.builder()
                .patient(patient)
                .slot(slot)
                .doctor(doctor)
                .channel("WEB_SIMULATOR")
                .appointmentDate(slot.getSlotDate())
                .regionCode("ULLEUNG")
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .build();

        when(bookingRepository.findByPublicId(booking.getPublicId())).thenReturn(Optional.of(booking));
        when(careCaseRepository.findByBooking(booking)).thenReturn(Optional.empty());

        bookingService.cancelBooking(booking.getPublicId(), null, "usr_admin", "ADMIN");

        assertThat(booking.getStatus()).isEqualTo(com.waddoc.domain.booking.entity.BookingStatus.CANCELLED);
        assertThat(slot.isBooked()).isFalse();
    }

    @Test
    void getExistingBookings_returnsOnlyUpcomingConfirmedBookingsByDefault() {
        ReflectionTestUtils.setField(
                bookingService,
                "clock",
                Clock.fixed(Instant.parse("2026-04-01T01:30:00Z"), ZoneId.of("Asia/Seoul"))
        );

        Patient patient = Patient.builder()
                .name("Patient Park")
                .birthDate(LocalDate.of(1958, 3, 15))
                .gender(PatientGender.FEMALE)
                .regionCode("ULLEUNG")
                .address("울릉군")
                .phone("01012345678")
                .build();

        IntakeSession session = IntakeSession.builder()
                .patient(patient)
                .callerNumber("01012345678")
                .channel(IntakeChannel.WEB_SIMULATOR)
                .build();

        User doctorUser = User.builder()
                .username("doctor")
                .passwordHash("encoded")
                .name("Doctor Kim")
                .role(Role.DOCTOR)
                .build();

        DoctorProfile doctor = DoctorProfile.builder()
                .user(doctorUser)
                .department("INTERNAL_MEDICINE")
                .departmentName("Internal Medicine")
                .build();

        ScheduleSlot slot = ScheduleSlot.builder()
                .doctor(doctor)
                .slotDate(LocalDate.of(2026, 4, 1))
                .startTime(LocalTime.of(11, 0))
                .endTime(LocalTime.of(11, 30))
                .build();

        Booking booking = Booking.builder()
                .patient(patient)
                .intakeSession(session)
                .slot(slot)
                .doctor(doctor)
                .channel("WEB_SIMULATOR")
                .appointmentDate(LocalDate.of(2026, 4, 1))
                .regionCode("ULLEUNG")
                .startTime(LocalTime.of(11, 0))
                .endTime(LocalTime.of(11, 30))
                .build();
        ReflectionTestUtils.setField(booking, "createdAt", LocalDateTime.of(2026, 3, 31, 9, 0));

        when(intakeSessionRepository.findByPublicId(session.getPublicId())).thenReturn(Optional.of(session));
        when(bookingRepository.findUpcomingBookingsByPatientAndStatus(
                patient,
                com.waddoc.domain.booking.entity.BookingStatus.CONFIRMED,
                LocalDate.of(2026, 4, 1),
                LocalTime.of(10, 30)
        )).thenReturn(List.of(booking));

        var response = bookingService.getExistingBookings(session.getPublicId(), null);

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getBookings()).hasSize(1);
        assertThat(response.getBookings().get(0).getBookingId()).isEqualTo(booking.getPublicId());
        verify(bookingRepository).findUpcomingBookingsByPatientAndStatus(
                patient,
                com.waddoc.domain.booking.entity.BookingStatus.CONFIRMED,
                LocalDate.of(2026, 4, 1),
                LocalTime.of(10, 30)
        );
    }
}
