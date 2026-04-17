package com.waddoc.domain.consultation.dto;

import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import lombok.Builder;
import lombok.Getter;

import com.waddoc.global.util.KstTime;

import java.time.OffsetDateTime;

@Getter
@Builder
public class CreateConsultationSessionResponse {

    private String sessionId;
    private String caseId;
    private ConsultationSessionStatus status;
    private RoomDetail room;
    private String doctorToken;
    private OffsetDateTime createdAt;

    public static CreateConsultationSessionResponse of(ConsultationSession session, String doctorToken) {
        return CreateConsultationSessionResponse.builder()
                .sessionId(session.getPublicId())
                .caseId(session.getCareCase().getPublicId())
                .status(session.getStatus())
                .room(RoomDetail.builder()
                        .roomId(session.getRoomId())
                        .livekitUrl(session.getLivekitUrl())
                        .build())
                .doctorToken(doctorToken)
                .createdAt(session.getCreatedAt() != null
                        ? session.getCreatedAt().atZone(KstTime.ZONE).toOffsetDateTime()
                        : null)
                .build();
    }

    @Getter
    @Builder
    public static class RoomDetail {
        private String roomId;
        private String livekitUrl;
    }
}
