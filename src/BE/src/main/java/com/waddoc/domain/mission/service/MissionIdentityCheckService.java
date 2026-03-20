package com.waddoc.domain.mission.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.consultation.dto.IdentityVerificationResult;
import com.waddoc.domain.consultation.service.ConsultationIdentityVerificationClient;
import com.waddoc.domain.mission.dto.MissionIdentityCheckResponse;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.authorization.AccessActor;
import com.waddoc.global.security.authorization.AccessControlService;
import com.waddoc.global.security.jwt.MissionTerminalScopes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MissionIdentityCheckService {

    private static final EnumSet<MissionPhase> READY_MISSION_PHASES =
            EnumSet.of(MissionPhase.ARRIVED, MissionPhase.VERIFYING);

    private final MissionRepository missionRepository;
    private final AccessControlService accessControlService;
    private final ConsultationIdentityVerificationClient consultationIdentityVerificationClient;
    private final MissionIdentityCheckCacheService missionIdentityCheckCacheService;
    private final AuditLogService auditLogService;

    @Value("${file.storage-root}")
    private String fileStorageRoot;

    @Value("${consultation.identity-check-bypass-enabled:false}")
    private boolean identityCheckBypassEnabled;

    public MissionIdentityCheckResponse verify(
            String missionId,
            MultipartFile faceImage,
            MultipartFile idCardImage,
            Authentication authentication
    ) {
        AccessActor actor = accessControlService.assertAdminOrMissionTerminal(
                authentication,
                missionId,
                MissionTerminalScopes.IDENTITY_CHECK
        );

        Mission mission = missionRepository.findWithDetailsByPublicId(missionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MISSION_NOT_FOUND));

        if (!READY_MISSION_PHASES.contains(mission.getPhase())) {
            throw new BusinessException(ErrorCode.MISSION_NOT_READY);
        }

        Patient patient = mission.getCareCase().getPatient();

        if (mission.getPhase() == MissionPhase.ARRIVED) {
            mission.updatePhase(MissionPhase.VERIFYING);
            missionRepository.save(mission);
        }

        if (identityCheckBypassEnabled) {
            return bypassIdentityCheck(mission, patient, actor);
        }

        byte[] referenceImage = readReferenceImage(patient);
        IdentityVerificationResult identityVerificationResult = consultationIdentityVerificationClient.verify(
                patient.getPublicId(),
                referenceImage,
                extractFilename(patient.getReferenceImagePath()),
                getBytes(faceImage),
                fallbackFilename(faceImage, "face-image.jpg"),
                getBytes(idCardImage),
                fallbackFilename(idCardImage, "id-card-image.jpg")
        );

        validateIdentityVerification(patient, identityVerificationResult, mission.getPublicId());

        MissionIdentityCheckCacheService.VerifiedIdentityCheck verifiedIdentityCheck =
                missionIdentityCheckCacheService.saveVerified(mission.getPublicId(), patient.getPublicId());

        auditLogService.log(
                "MISSION_IDENTITY_CHECK_VERIFIED",
                "MISSION",
                mission.getPublicId(),
                "corr_mis_" + mission.getPublicId(),
                actor.actorId(),
                actor.actorRole(),
                Map.of(
                        "patientId", patient.getPublicId(),
                        "missionPhase", mission.getPhase().name(),
                        "expiresInSeconds", verifiedIdentityCheck.expiresInSeconds()
                )
        );

        return MissionIdentityCheckResponse.of(
                mission,
                patient.getPublicId(),
                verifiedIdentityCheck.verifiedAt(),
                verifiedIdentityCheck.expiresInSeconds(),
                identityVerificationResult
        );
    }

    private MissionIdentityCheckResponse bypassIdentityCheck(
            Mission mission,
            Patient patient,
            AccessActor actor
    ) {
        // TODO: Remove this bypass once the AI IDV server is stable and all environments use the real verification flow.
        log.warn(
                "Identity check bypass enabled. missionId={}, patientId={}",
                mission.getPublicId(),
                patient.getPublicId()
        );

        MissionIdentityCheckCacheService.VerifiedIdentityCheck verifiedIdentityCheck =
                missionIdentityCheckCacheService.saveVerified(mission.getPublicId(), patient.getPublicId());

        auditLogService.log(
                "MISSION_IDENTITY_CHECK_BYPASSED",
                "MISSION",
                mission.getPublicId(),
                "corr_mis_" + mission.getPublicId(),
                actor.actorId(),
                actor.actorRole(),
                Map.of(
                        "patientId", patient.getPublicId(),
                        "missionPhase", mission.getPhase().name()
                )
        );

        IdentityVerificationResult bypassResult = IdentityVerificationResult.builder()
                .matched(true)
                .faceSimilarityScore(1.0)
                .idCardFaceSimilarityScore(1.0)
                .reasonCodes(List.of("BYPASSED"))
                .ocr(IdentityVerificationResult.OcrData.builder()
                        .name(patient.getName())
                        .birthDate6(patient.getBirthDate6())
                        .address(patient.getAddress())
                        .build())
                .build();

        return MissionIdentityCheckResponse.of(
                mission,
                patient.getPublicId(),
                verifiedIdentityCheck.verifiedAt(),
                verifiedIdentityCheck.expiresInSeconds(),
                bypassResult
        );
    }

    private byte[] readReferenceImage(Patient patient) {
        if (!patient.hasReferenceImage()) {
            throw new BusinessException(ErrorCode.REFERENCE_IMAGE_MISSING);
        }

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

    private void validateIdentityVerification(Patient patient, IdentityVerificationResult result, String missionId) {
        IdentityVerificationResult.OcrData ocr = result.getOcr();
        boolean verified = result.isMatched()
                && ocr != null
                && equalsNormalized(patient.getName(), ocr.getName())
                && equalsNormalized(patient.getBirthDate6(), ocr.getBirthDate6())
                && addressMatches(patient.getAddress(), ocr.getAddress());

        if (verified) {
            return;
        }

        log.warn(
                "Identity verification failed. missionId={}, patientId={}, matched={}, reasonCodes={}",
                missionId,
                patient.getPublicId(),
                result.isMatched(),
                result.getReasonCodes()
        );
        auditLogService.log(
                "MISSION_IDENTITY_CHECK_FAILED",
                "MISSION",
                missionId,
                "corr_mis_" + missionId,
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
