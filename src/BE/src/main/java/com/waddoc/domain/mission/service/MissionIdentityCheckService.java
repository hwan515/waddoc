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

/**
 * 환자 기준 이미지와 업로드된 자료를 비교해 미션 본인 확인을 확정한다.
 */
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

    /**
     * 관리자 또는 미션 단말 권한을 확인한 뒤 신원 확인을 수행한다.
     * 성공 결과는 캐시에 저장되어 이후 환자 토큰 발급에서 재사용된다.
     */
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

        byte[] referenceImage = readReferenceImageIfPresent(patient);
        IdentityVerificationResult identityVerificationResult = consultationIdentityVerificationClient.verify(
                patient.getPublicId(),
                referenceImage,
                extractFilenameIfPresent(patient.getReferenceImagePath()),
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
        return buildBypassedIdentityCheckResponse(
                mission,
                patient,
                actor,
                "MISSION_IDENTITY_CHECK_BYPASSED",
                "BYPASSED",
                "Identity check bypass enabled."
        );
    }

    private MissionIdentityCheckResponse buildBypassedIdentityCheckResponse(
            Mission mission,
            Patient patient,
            AccessActor actor,
            String auditAction,
            String reasonCode,
            String logMessage
    ) {
        log.warn(
                "{} missionId={}, patientId={}",
                logMessage,
                mission.getPublicId(),
                patient.getPublicId()
        );

        MissionIdentityCheckCacheService.VerifiedIdentityCheck verifiedIdentityCheck =
                missionIdentityCheckCacheService.saveVerified(mission.getPublicId(), patient.getPublicId());

        auditLogService.log(
                auditAction,
                "MISSION",
                mission.getPublicId(),
                "corr_mis_" + mission.getPublicId(),
                actor.actorId(),
                actor.actorRole(),
                Map.of(
                        "patientId", patient.getPublicId(),
                        "missionPhase", mission.getPhase().name(),
                        "reasonCode", reasonCode
                )
        );

        IdentityVerificationResult bypassResult = IdentityVerificationResult.builder()
                .matched(true)
                .faceSimilarityScore(1.0)
                .idCardFaceSimilarityScore(1.0)
                .reasonCodes(List.of(reasonCode))
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

    private byte[] readReferenceImageIfPresent(Patient patient) {
        if (!patient.hasReferenceImage()) {
            return null;
        }

        Path rootPath = Path.of(fileStorageRoot).toAbsolutePath().normalize();
        Path resolvedPath = rootPath.resolve(patient.getReferenceImagePath()).normalize();
        if (!resolvedPath.startsWith(rootPath) || !Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
            return null;
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

    private String extractFilenameIfPresent(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return null;
        }
        return extractFilename(relativePath);
    }

    private void validateIdentityVerification(Patient patient, IdentityVerificationResult result, String missionId) {
        IdentityVerificationResult.OcrData ocr = result.getOcr();
        boolean nameMatched = ocr != null && equalsNormalized(patient.getName(), ocr.getName());
        boolean birthDateMatched = ocr != null && birthDate6Matches(patient.getBirthDate6(), ocr);
        boolean addressMatched = ocr != null && addressMatches(patient.getAddress(), ocr.getAddress());
        boolean anyOcrFieldMatched = nameMatched || birthDateMatched || addressMatched;
        // TODO: 촬영 품질과 얼굴 검출 안정화 이후에는 AI matched를 다시 필수 조건으로 복구해야 한다.
        boolean verified = ocr != null && anyOcrFieldMatched;

        if (verified) {
            return;
        }

        log.warn(
                "Identity verification failed. missionId={}, patientId={}, matched={}, anyOcrFieldMatched={}, nameMatched={}, birthDateMatched={}, addressMatched={}, reasonCodes={}",
                missionId,
                patient.getPublicId(),
                result.isMatched(),
                anyOcrFieldMatched,
                nameMatched,
                birthDateMatched,
                addressMatched,
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
                        "anyOcrFieldMatched", anyOcrFieldMatched,
                        "nameMatched", nameMatched,
                        "birthDateMatched", birthDateMatched,
                        "addressMatched", addressMatched,
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
        String normalizedPatientAddress = normalizeAddress(patientAddress);
        String normalizedOcrAddress = normalizeAddress(ocrAddress);
        return normalizedPatientAddress.contains(normalizedOcrAddress)
                || normalizedOcrAddress.contains(normalizedPatientAddress);
    }

    private boolean birthDate6Matches(String patientBirthDate6, IdentityVerificationResult.OcrData ocr) {
        if (patientBirthDate6 == null || ocr == null) {
            return false;
        }
        String resolvedBirthDate6 = firstNonBlank(
                ocr.getBirthDate6(),
                extractBirthDate6(ocr.getRrnMasked())
        );
        return equalsNormalized(patientBirthDate6, resolvedBirthDate6);
    }

    private String extractBirthDate6(String rrnMasked) {
        if (rrnMasked == null || rrnMasked.isBlank()) {
            return null;
        }
        String digits = rrnMasked.replaceAll("\\D", "");
        return digits.length() >= 6 ? digits.substring(0, 6) : null;
    }

    private String normalizeAddress(String value) {
        return normalize(
                value
                        .replace("서울특별시", "서울")
                        .replace("부산광역시", "부산")
                        .replace("대구광역시", "대구")
                        .replace("인천광역시", "인천")
                        .replace("광주광역시", "광주")
                        .replace("대전광역시", "대전")
                        .replace("울산광역시", "울산")
                        .replace("세종특별자치시", "세종")
                        .replace("경상북도", "경북")
                        .replace("경상남도", "경남")
                        .replace("전라북도", "전북")
                        .replace("전라남도", "전남")
                        .replace("충청북도", "충북")
                        .replace("충청남도", "충남")
                        .replace("강원특별자치도", "강원")
                        .replace("강원도", "강원")
                        .replace("제주특별자치도", "제주")
                        .replace("제주도", "제주")
                        .replaceAll("[,()\\-]", "")
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value.replaceAll("\\s+", "").trim();
    }
}
