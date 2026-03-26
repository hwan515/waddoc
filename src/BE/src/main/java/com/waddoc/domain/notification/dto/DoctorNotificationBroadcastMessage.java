package com.waddoc.domain.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DoctorNotificationBroadcastMessage {

    private String doctorId;
    private String eventName;
    private NewBookingNotificationPayload payload;
}
