package com.waddoc.domain.booking.service;

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

    @InjectMocks
    private BookingNotificationListener bookingNotificationListener;

    @Test
    void handleBookingCreatedSmsEvent_sendsSms() {
        BookingCreatedSmsEvent event = new BookingCreatedSmsEvent("bk_test123", "01012345678", "예약 확인");

        bookingNotificationListener.handleBookingCreatedSmsEvent(event);

        verify(smsService).send("01012345678", "예약 확인");
    }

    @Test
    void handleBookingCreatedSmsEvent_swallowsSmsFailure() {
        BookingCreatedSmsEvent event = new BookingCreatedSmsEvent("bk_test123", "01012345678", "예약 확인");
        doThrow(new IllegalStateException("gateway down")).when(smsService).send("01012345678", "예약 확인");

        bookingNotificationListener.handleBookingCreatedSmsEvent(event);

        verify(smsService).send("01012345678", "예약 확인");
    }
}
