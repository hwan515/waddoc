package com.waddoc.domain.consultation.dto;

import com.waddoc.domain.consultation.entity.ConsultationSession;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IssuePatientTokenResponse {

    private String sessionId;
    private String patientToken;
    private int expiresIn;
    private RoomDetail room;

    public static IssuePatientTokenResponse of(
            ConsultationSession session,
            String patientToken,
            int expiresIn
    ) {
        return IssuePatientTokenResponse.builder()
                .sessionId(session.getPublicId())
                .patientToken(patientToken)
                .expiresIn(expiresIn)
                .room(RoomDetail.builder()
                        .roomId(session.getRoomId())
                        .livekitUrl(session.getLivekitUrl())
                        .build())
                .build();
    }

    @Getter
    @Builder
    public static class RoomDetail {
        private String roomId;
        private String livekitUrl;
    }
}
