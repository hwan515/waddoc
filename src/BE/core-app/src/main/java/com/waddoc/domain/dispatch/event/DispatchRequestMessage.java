package com.waddoc.domain.dispatch.event;

import com.waddoc.domain.dispatch.entity.DispatchOutbox;

public record DispatchRequestMessage(
        String caseId,
        String regionCode,
        String destination
) {

    public static DispatchRequestMessage from(DispatchOutbox outbox) {
        return new DispatchRequestMessage(
                outbox.getCareCase().getPublicId(),
                outbox.getRegionCode(),
                outbox.getDestination()
        );
    }
}
