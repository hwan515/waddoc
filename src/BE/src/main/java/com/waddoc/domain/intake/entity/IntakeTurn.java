package com.waddoc.domain.intake.entity;

import com.waddoc.global.audit.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 인테이크 턴별 기록. DTMF 키입력 또는 VOICE(STT) 입력과 TTS 응답을 저장.
 */
@Entity
@Table(name = "intake_turn")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntakeTurn extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "turn_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intake_session_id", nullable = false)
    private IntakeSession intakeSession;

    @Column(name = "turn_order", nullable = false)
    private int turnOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "turn_type", nullable = false, length = 10)
    private TurnType turnType;

    @Column(name = "dtmf_input", length = 10)
    private String dtmfInput;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "stt_text", columnDefinition = "TEXT")
    private String sttText;

    @Column(name = "stt_confidence", precision = 5, scale = 4)
    private BigDecimal sttConfidence;

    @Column(name = "exception_code", length = 30)
    private String exceptionCode;

    @Column(name = "next_action", length = 30)
    private String nextAction;

    @Column(name = "tts_message", columnDefinition = "TEXT")
    private String ttsMessage;

    @Column(name = "audio_file_path", length = 500)
    private String audioFilePath;

    @Builder
    public IntakeTurn(IntakeSession intakeSession, int turnOrder, TurnType turnType,
                      String dtmfInput, String prompt, String sttText, BigDecimal sttConfidence,
                      String exceptionCode, String nextAction, String ttsMessage, String audioFilePath) {
        this.intakeSession = intakeSession;
        this.turnOrder = turnOrder;
        this.turnType = turnType;
        this.dtmfInput = dtmfInput;
        this.prompt = prompt;
        this.sttText = sttText;
        this.sttConfidence = sttConfidence;
        this.exceptionCode = exceptionCode;
        this.nextAction = nextAction;
        this.ttsMessage = ttsMessage;
        this.audioFilePath = audioFilePath;
    }
}
