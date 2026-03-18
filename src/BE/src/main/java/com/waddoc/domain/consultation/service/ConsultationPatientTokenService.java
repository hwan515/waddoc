package com.waddoc.domain.consultation.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.consultation.dto.IdentityVerificationResult;
import com.waddoc.domain.consultation.dto.IssuePatientTokenResponse;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationPatientTokenService {

    private static final EnumSet<MissionPhase> READY_MISSION_PHASES =
            EnumSet.of(MissionPhase.VERIFYING, MissionPhase.CONSULTING);

    private final ConsultationSessionRepository consultationSessionRepository;
    private final MissionRepository missionRepository;
    private final AccessControlService accessControlService;
    private final ConsultationIdentityVerificationClient consultationIdentityVerificationClient;
    private final ConsultationLiveKitService consultationLiveKitService;
    private final AuditLogService auditLogService;

    @Value("${file.storage-root}")
    private String fileStorageRoot;

    @Transactional(readOnly = true)
    public IssuePatientTokenResponse issuePatientToken(
            String sessionId,
            String patientId,
            MultipartFile faceImage,
            MultipartFile idCardImage,
            AuthenticatedUser authenticatedUser
    ) {
        accessControlService.assertAdmin(authenticatedUser);

        ConsultationSession session = consultationSessionRepository.findWithParticipantsByPublicId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        if (isTerminal(session.getStatus())) {
            throw new BusinessException(ErrorCode.SESSION_STATE_INVALID);
        }

        Patient patient = session.getCareCase().getPatient();
        if (!patient.getPublicId().equals(patientId)) {
            throw new BusinessException(ErrorCode.PATIENT_MISMATCH);
        }

        Mission mission = missionRepository.findByCareCase(session.getCareCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_READY));
        if (!READY_MISSION_PHASES.contains(mission.getPhase())) {
            throw new BusinessException(ErrorCode.MISSION_NOT_READY);
        }

        // 기준 이미지와 차량 촬영 이미지를 함께 보내 AI 본인 확인을 수행한다.
        byte[] referenceImage = readReferenceImage(patient);
        IdentityVerificationResult identityVerificationResult = consultationIdentityVerificationClient.verify(
                patientId,
                referenceImage,
                extractFilename(patient.getReferenceImagePath()),
                getBytes(faceImage),
                fallbackFilename(faceImage, "face-image.jpg"),
                getBytes(idCardImage),
                fallbackFilename(idCardImage, "id-card-image.jpg")
        );

        validateIdentityVerification(patient, identityVerificationResult, sessionId);

        String patientToken = consultationLiveKitService.issuePatientToken(session, patient);
        auditLogService.log(
                "CONSULTATION_PATIENT_TOKEN_ISSUED",
                "CONSULTATION_SESSION",
                session.getPublicId(),
                "corr_ses_" + session.getPublicId(),
                authenticatedUser.userId(),
                authenticatedUser.role().name(),
                Map.of(
                        "patientId", patient.getPublicId(),
                        "missionPhase", mission.getPhase().name()
                )
        );

        return IssuePatientTokenResponse.of(
                session,
                patientToken,
                consultationLiveKitService.getParticipantTokenExpiresInSeconds(),
                identityVerificationResult
        );
    }

    private boolean isTerminal(ConsultationSessionStatus status) {
        return status == ConsultationSessionStatus.COMPLETED
                || status == ConsultationSessionStatus.FAILED
                || status == ConsultationSessionStatus.ABANDONED;
    }

    private byte[] readReferenceImage(Patient patient) {
        if (!patient.hasReferenceImage()) {
            throw new BusinessException(ErrorCode.REFERENCE_IMAGE_MISSING);
        }

        // 저장소 루트 밖으로 벗어나는 상대 경로 접근을 막고, 실제 파일만 허용한다.
        Path rootPath = Path.of(fileStorageRoot).toAbsolutePath().normalize();
        Path resolvedPath = rootPath.resolve(patient.getReferenceImagePath()).normalize();
        if (!resolvedPath.startsWith(rootPath) || !Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
            throw new BusinessException(ErrorCode.REFERENCE_IMAGE_MISSING);
        }

        try {
            return Files.readAllBytes(resolvedPath);
        } catch (IOException e) {
            log.error("Failed to read reference image. patientId={}, path={}", patient.getPublicId(), resolvedPath, e);
            throw new BusinessException(ErrorCode.AI_IDV_REQUEST_FAILED);
        }
    }

    private byte[] getBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded verification file. originalFilename={}", file.getOriginalFilename(), e);
            throw new BusinessException(ErrorCode.AI_IDV_REQUEST_FAILED);
        }
    }

    private String fallbackFilename(MultipartFile file, String fallback) {
        return file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
                ? file.getOriginalFilename()
                : fallback;
    }

    private String extractFilename(String relativePath) {
        return Path.of(relativePath).getFileName().toString();
    }

    private void validateIdentityVerification(Patient patient, IdentityVerificationResult result, String sessionId) {
        IdentityVerificationResult.OcrData ocr = result.getOcr();
        // AI 판정뿐 아니라 OCR 결과를 환자 원본 정보와 다시 대조해 오탐을 줄인다.
        boolean verified = result.isMatched()
                && ocr != null
                && equalsNormalized(patient.getName(), ocr.getName())
                && equalsNormalized(patient.getBirthDate6(), ocr.getBirthDate6())
                && addressMatches(patient.getAddress(), ocr.getAddress());

        if (verified) {
            return;
        }

        log.warn(
                "Identity verification failed. sessionId={}, patientId={}, matched={}, reasonCodes={}",
                sessionId,
                patient.getPublicId(),
                result.isMatched(),
                result.getReasonCodes()
        );
        auditLogService.log(
                "CONSULTATION_IDENTITY_CHECK_FAILED",
                "CONSULTATION_SESSION",
                sessionId,
                "corr_ses_" + sessionId,
                Map.of(
                        "patientId", patient.getPublicId(),
                        "matched", result.isMatched(),
                        "reasonCodes", result.getReasonCodes()
                )
        );
        throw new BusinessException(ErrorCode.IDENTITY_CHECK_FAILED);
    }

    private boolean equalsNormalized(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return normalize(left).equals(normalize(right));
    }

    private boolean addressMatches(String patientAddress, String ocrAddress) {
        if (patientAddress == null || ocrAddress == null) {
            return false;
        }
        String normalizedPatientAddress = normalize(patientAddress);
        String normalizedOcrAddress = normalize(ocrAddress);
        return normalizedPatientAddress.contains(normalizedOcrAddress)
                || normalizedOcrAddress.contains(normalizedPatientAddress);
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", "").trim();
    }
}
