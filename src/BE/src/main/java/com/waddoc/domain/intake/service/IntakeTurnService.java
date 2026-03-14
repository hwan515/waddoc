package com.waddoc.domain.intake.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.intake.dto.*;
import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.entity.IntakeTurn;
import com.waddoc.domain.intake.entity.TurnType;
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
import com.waddoc.domain.intake.repository.IntakeTurnRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IntakeTurnService {

    private final IntakeSessionRepository intakeSessionRepository;
    private final IntakeTurnRepository intakeTurnRepository;
    private final AuditLogService auditLogService;

    @Value("${file.storage-root}")
    private String storageRoot;

    @Transactional
    public IntakeTurnResponse recordDtmfTurn(String sessionId, RecordDtmfTurnRequest request) {
        IntakeSession session = findActiveSession(sessionId);

        int nextOrder = getNextTurnOrder(session);

        IntakeTurn turn = IntakeTurn.builder()
                .intakeSession(session)
                .turnOrder(nextOrder)
                .turnType(TurnType.DTMF)
                .dtmfInput(request.getDtmfInput())
                .prompt(request.getPrompt())
                .nextAction(request.getNextAction())
                .ttsMessage(request.getTtsMessage())
                .build();

        intakeTurnRepository.save(turn);
        session.touch();

        String correlationId = "corr_ints_" + session.getPublicId();
        auditLogService.log(
                "INTAKE_TURN_RECORDED",
                "INTAKE_SESSION",
                session.getPublicId(),
                correlationId,
                Map.of("turnId", turn.getPublicId(),
                       "turnOrder", nextOrder,
                       "turnType", "DTMF")
        );

        return IntakeTurnResponse.from(turn);
    }

    @Transactional
    public IntakeTurnResponse recordVoiceTurn(String sessionId, MultipartFile audioFile,
                                              String prompt, String nextAction, String ttsMessage) {
        IntakeSession session = findActiveSession(sessionId);

        String audioFilePath = saveAudioFile(session.getPublicId(), audioFile);

        // STT Mock: 실제 AI 호출 없이 stub 응답
        String sttText = "음성 인식 결과 (Mock)";
        BigDecimal sttConfidence = new BigDecimal("0.95");

        int nextOrder = getNextTurnOrder(session);

        IntakeTurn turn = IntakeTurn.builder()
                .intakeSession(session)
                .turnOrder(nextOrder)
                .turnType(TurnType.VOICE)
                .prompt(prompt)
                .sttText(sttText)
                .sttConfidence(sttConfidence)
                .nextAction(nextAction)
                .ttsMessage(ttsMessage)
                .audioFilePath(audioFilePath)
                .build();

        intakeTurnRepository.save(turn);
        session.touch();

        String correlationId = "corr_ints_" + session.getPublicId();
        auditLogService.log(
                "INTAKE_TURN_RECORDED",
                "INTAKE_SESSION",
                session.getPublicId(),
                correlationId,
                Map.of("turnId", turn.getPublicId(),
                       "turnOrder", nextOrder,
                       "turnType", "VOICE",
                       "audioFilePath", audioFilePath)
        );

        return IntakeTurnResponse.from(turn);
    }

    @Transactional
    public CompleteSessionResponse completeSession(String sessionId, CompleteSessionRequest request) {
        IntakeSession session = findActiveSession(sessionId);

        session.complete(request.getCompletionReason());

        String correlationId = "corr_ints_" + session.getPublicId();
        auditLogService.log(
                "INTAKE_SESSION_COMPLETED",
                "INTAKE_SESSION",
                session.getPublicId(),
                correlationId,
                Map.of("completionReason", request.getCompletionReason().name())
        );

        return CompleteSessionResponse.from(session);
    }

    private IntakeSession findActiveSession(String sessionId) {
        IntakeSession session = intakeSessionRepository.findByPublicId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTAKE_SESSION_NOT_FOUND));

        if (!session.isActive()) {
            throw new BusinessException(ErrorCode.SESSION_STATE_INVALID);
        }

        return session;
    }

    private int getNextTurnOrder(IntakeSession session) {
        return intakeTurnRepository.findTopByIntakeSessionOrderByTurnOrderDesc(session)
                .map(t -> t.getTurnOrder() + 1)
                .orElse(1);
    }

    private String saveAudioFile(String sessionPublicId, MultipartFile audioFile) {
        String originalFilename = audioFile.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }

        String relativePath = "audio/intake/" + sessionPublicId + "/" + UUID.randomUUID() + ext;
        Path fullPath = Paths.get(storageRoot, relativePath);

        try {
            Files.createDirectories(fullPath.getParent());
            audioFile.transferTo(fullPath.toFile());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_SAVE_FAILED);
        }

        return relativePath;
    }
}
