package com.waddoc.domain.consultation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PostConsultationTokenRequest {

    @NotNull(message = "participantType은 필수입니다.")
    private ParticipantType participantType;

    private String patientId;

    public enum ParticipantType {
        DOCTOR,
        PATIENT
    }
}
