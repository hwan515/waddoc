package com.waddoc.domain.booking.service;

import com.waddoc.global.sms.SmsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingNotificationListener {

    private final SmsService smsService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBookingCreatedSmsEvent(BookingCreatedSmsEvent event) {
        // 외부 SMS 게이트웨이 호출은 커밋 이후 비동기로 분리해 예약 API 응답 시간을 늘리지 않게 한다.
        try {
            smsService.send(event.recipientPhone(), event.message());
        } catch (Exception e) {
            log.error("SMS send failed. eventType=BOOKING_CREATED, bookingId={}, recipientPhone={}",
                    event.bookingId(), event.recipientPhone(), e);
        }
    }
}
