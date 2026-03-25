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
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.config.DemoModePolicy;
import com.waddoc.global.config.DispatchAssignmentPolicy;
import com.waddoc.global.config.KafkaTopics;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
    private DemoModePolicy demoModePolicy;

    @Mock
    private DispatchAssignmentPolicy dispatchAssignmentPolicy;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void createBooking_publishesKafkaMessagesAndCreatesDispatchOutbox() {
        when(demoModePolicy.isSameDayAutoProvisionEnabled()).thenReturn(true);
        when(dispatchAssignmentPolicy.getDefaultVehicleId()).thenReturn("veh_GIMCHEON_01");

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
        when(scheduleSlotRepository.findByPublicId(slot.getPublicId())).thenReturn(Optional.of(slot));
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
                eq("veh_GIMCHEON_01"),
                eq("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69"),
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
    void createBooking_sameDayProvisionImmediateMissionAndSession() {
        when(demoModePolicy.isSameDayAutoProvisionEnabled()).thenReturn(true);
        when(dispatchAssignmentPolicy.getDefaultVehicleId()).thenReturn("veh_GIMCHEON_01");

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
        when(scheduleSlotRepository.findByPublicId(slot.getPublicId())).thenReturn(Optional.of(slot));
        when(smsService.getContactNumber()).thenReturn("01049163720");
        doAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            ReflectionTestUtils.setField(booking, "createdAt", LocalDateTime.of(2026, 3, 21, 12, 0));
            return booking;
        }).when(bookingRepository).save(any(Booking.class));
        when(dispatchOutboxRepository.save(any(DispatchOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(missionCommandService.createMissionForDispatch(any(CareCase.class), any(), any(), any()))
                .thenAnswer(invocation -> {
                    CareCase careCase = invocation.getArgument(0);
                    Mission mission = Mission.builder()
                            .careCase(careCase)
                            .vehicleId(invocation.getArgument(1))
                            .destination(invocation.getArgument(2))
                            .dispatchedAt(invocation.getArgument(3))
                            .build();
                    ReflectionTestUtils.setField(mission, "id", 1L);
                    return mission;
                });
        when(missionRepository.save(any(Mission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(consultationSessionRepository.findByCareCase(any(CareCase.class))).thenReturn(Optional.empty());
        when(consultationLiveKitService.getLivekitUrl()).thenReturn("wss://livekit.example");
        when(consultationSessionRepository.save(any(ConsultationSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bookingService.createBooking(session.getPublicId(), request);

        ArgumentCaptor<DispatchOutbox> outboxCaptor = ArgumentCaptor.forClass(DispatchOutbox.class);
        ArgumentCaptor<Mission> missionCaptor = ArgumentCaptor.forClass(Mission.class);
        ArgumentCaptor<ConsultationSession> sessionCaptor = ArgumentCaptor.forClass(ConsultationSession.class);

        verify(dispatchOutboxRepository).save(outboxCaptor.capture());
        verify(missionCommandService).createMissionForDispatch(
                any(CareCase.class),
                eq("veh_GIMCHEON_01"),
                eq("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69"),
                eq(null)
        );
        verify(missionRepository).save(missionCaptor.capture());
        verify(consultationLiveKitService).createRoom(any());
        verify(consultationSessionRepository).save(sessionCaptor.capture());

        assertThat(outboxCaptor.getValue().isCompleted()).isTrue();
        assertThat(missionCaptor.getValue().getPhase()).isEqualTo(MissionPhase.ARRIVED);
        assertThat(sessionCaptor.getValue().getStatus().name()).isEqualTo("READY");
    }

    @Test
    void createBooking_sameDaySkipsImmediateProvisionWhenDemoModeIsEnabled() {
        when(demoModePolicy.isSameDayAutoProvisionEnabled()).thenReturn(false);
        when(dispatchAssignmentPolicy.getDefaultVehicleId()).thenReturn("veh_GIMCHEON_01");

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
        when(scheduleSlotRepository.findByPublicId(slot.getPublicId())).thenReturn(Optional.of(slot));
        when(smsService.getContactNumber()).thenReturn("01049163720");
        doAnswer(invocation -> {
            Booking booking = invocation.getArgument(0);
            ReflectionTestUtils.setField(booking, "createdAt", LocalDateTime.of(2026, 3, 21, 12, 0));
            return booking;
        }).when(bookingRepository).save(any(Booking.class));
        when(dispatchOutboxRepository.save(any(DispatchOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bookingService.createBooking(session.getPublicId(), request);

        ArgumentCaptor<DispatchOutbox> outboxCaptor = ArgumentCaptor.forClass(DispatchOutbox.class);

        verify(dispatchOutboxRepository).save(outboxCaptor.capture());
        verify(missionCommandService).createMissionForDispatch(
                any(CareCase.class),
                eq("veh_GIMCHEON_01"),
                eq("Gyeongbuk Gimcheon-si Jeungsan-myeon Jangjeon 1-gil 69"),
                eq(null)
        );
        verify(missionRepository, never()).save(any(Mission.class));
        verify(consultationLiveKitService, never()).createRoom(any());
        verify(consultationSessionRepository, never()).save(any(ConsultationSession.class));

        assertThat(outboxCaptor.getValue().isCompleted()).isFalse();
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
}
