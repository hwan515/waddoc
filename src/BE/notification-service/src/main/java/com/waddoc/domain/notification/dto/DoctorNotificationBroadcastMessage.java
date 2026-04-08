package com.waddoc.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DoctorNotificationBroadcastMessage {

    @JsonAlias("doctorId")
    private String doctorUserId;
    private String eventName;
    private NewBookingNotificationPayload payload;
}
