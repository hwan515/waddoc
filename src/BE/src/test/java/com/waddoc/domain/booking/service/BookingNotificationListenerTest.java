package com.waddoc.domain.booking.service;

import com.waddoc.domain.notification.dto.NewBookingNotificationPayload;
import com.waddoc.domain.notification.service.DoctorNotificationSseService;
import com.waddoc.global.sms.SmsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingNotificationListenerTest {

    @Mock
    private SmsService smsService;

    @Mock
    private DoctorNotificationSseService doctorNotificationSseService;

    @InjectMocks
    private BookingNotificationListener bookingNotificationListener;

    @Test
    void handleBookingCreatedSmsEvent_sendsSms() {
        BookingCreatedSmsEvent event = new BookingCreatedSmsEvent("bk_test123", "01012345678", "booking confirmed");

        bookingNotificationListener.handleBookingCreatedSmsEvent(event);

        verify(smsService).send("01012345678", "booking confirmed");
    }

    @Test
    void handleBookingCreatedSmsEvent_swallowsSmsFailure() {
        BookingCreatedSmsEvent event = new BookingCreatedSmsEvent("bk_test123", "01012345678", "booking confirmed");
        doThrow(new IllegalStateException("gateway down")).when(smsService).send("01012345678", "booking confirmed");

        bookingNotificationListener.handleBookingCreatedSmsEvent(event);

        verify(smsService).send("01012345678", "booking confirmed");
    }

    @Test
    void handleBookingCreatedDoctorNotificationEvent_sendsSseNotification() {
        NewBookingNotificationPayload payload = NewBookingNotificationPayload.builder()
                .type("NEW_BOOKING")
                .bookingId("bk_test123")
                .caseId("case_test123")
                .doctorId("doc_test123")
                .doctorName("Doctor Kim")
                .departmentName("Internal Medicine")
                .patientName("Patient Park")
                .location("Gyeongbuk Gimcheon-si Jeungsan-myeon")
                .build();
        BookingCreatedDoctorNotificationEvent event =
                new BookingCreatedDoctorNotificationEvent("doc_test123", payload);

        bookingNotificationListener.handleBookingCreatedDoctorNotificationEvent(event);

        verify(doctorNotificationSseService).sendToDoctor("doc_test123", "notification", payload);
    }

    @Test
    void handleBookingCreatedDoctorNotificationEvent_swallowsSseFailure() {
        NewBookingNotificationPayload payload = NewBookingNotificationPayload.builder()
                .type("NEW_BOOKING")
                .bookingId("bk_test123")
                .caseId("case_test123")
                .doctorId("doc_test123")
                .build();
        BookingCreatedDoctorNotificationEvent event =
                new BookingCreatedDoctorNotificationEvent("doc_test123", payload);
        doThrow(new IllegalStateException("sse down"))
                .when(doctorNotificationSseService)
                .sendToDoctor("doc_test123", "notification", payload);

        bookingNotificationListener.handleBookingCreatedDoctorNotificationEvent(event);

        verify(doctorNotificationSseService).sendToDoctor("doc_test123", "notification", payload);
    }
}
