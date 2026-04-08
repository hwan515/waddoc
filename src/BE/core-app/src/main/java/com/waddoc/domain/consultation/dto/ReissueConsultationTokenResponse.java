package com.waddoc.domain.consultation.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ReissueConsultationTokenResponse {

    private String token;
    private int expiresIn;

    public static ReissueConsultationTokenResponse of(String token, int expiresIn) {
        return ReissueConsultationTokenResponse.builder()
                .token(token)
                .expiresIn(expiresIn)
                .build();
    }
}
