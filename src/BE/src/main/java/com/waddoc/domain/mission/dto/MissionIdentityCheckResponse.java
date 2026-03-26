package com.waddoc.domain.mission.dto;

import com.waddoc.domain.consultation.dto.IdentityVerificationResult;
import com.waddoc.domain.mission.entity.Mission;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Builder
public class MissionIdentityCheckResponse {

    private String missionId;
    private String patientId;
    private String status;
    private OffsetDateTime verifiedAt;
    private long expiresInSeconds;
    private IdentityCheckDetail identityCheck;
    private String nextStep;

    public static MissionIdentityCheckResponse of(
            Mission mission,
            String patientId,
            OffsetDateTime verifiedAt,
            long expiresInSeconds,
            IdentityVerificationResult identityVerificationResult
    ) {
        return MissionIdentityCheckResponse.builder()
                .missionId(mission.getPublicId())
                .patientId(patientId)
                .status("VERIFIED")
                .verifiedAt(verifiedAt)
                .expiresInSeconds(expiresInSeconds)
                .identityCheck(IdentityCheckDetail.from(identityVerificationResult))
                .nextStep("VITALS")
                .build();
    }

    @Getter
    @Builder
    public static class IdentityCheckDetail {
        private boolean matched;
        private Double faceSimilarityScore;
        private Double idCardFaceSimilarityScore;
        private List<String> reasonCodes;
        private OcrDetail ocr;

        static IdentityCheckDetail from(IdentityVerificationResult identityVerificationResult) {
            return IdentityCheckDetail.builder()
                    .matched(identityVerificationResult.isMatched())
                    .faceSimilarityScore(identityVerificationResult.getFaceSimilarityScore())
                    .idCardFaceSimilarityScore(identityVerificationResult.getIdCardFaceSimilarityScore())
                    .reasonCodes(identityVerificationResult.getReasonCodes())
                    .ocr(OcrDetail.from(identityVerificationResult.getOcr()))
                    .build();
        }
    }

    @Getter
    @Builder
    public static class OcrDetail {
        private String name;
        private String rrnMasked;
        private String address;

        static OcrDetail from(IdentityVerificationResult.OcrData ocrData) {
            if (ocrData == null) {
                return null;
            }
            return OcrDetail.builder()
                    .name(ocrData.getName())
                    .rrnMasked(ocrData.getRrnMasked())
                    .address(ocrData.getAddress())
                    .build();
        }
    }
}
