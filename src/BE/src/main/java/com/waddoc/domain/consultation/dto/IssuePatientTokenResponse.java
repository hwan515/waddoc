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
    private IdentityCheckDetail identityCheck;
    private RoomDetail room;

    public static IssuePatientTokenResponse of(
            ConsultationSession session,
            String patientToken,
            int expiresIn,
            IdentityVerificationResult identityVerificationResult
    ) {
        return IssuePatientTokenResponse.builder()
                .sessionId(session.getPublicId())
                .patientToken(patientToken)
                .expiresIn(expiresIn)
                .identityCheck(IdentityCheckDetail.from(identityVerificationResult))
                .room(RoomDetail.builder()
                        .roomId(session.getRoomId())
                        .livekitUrl(session.getLivekitUrl())
                        .build())
                .build();
    }

    @Getter
    @Builder
    public static class IdentityCheckDetail {
        private boolean matched;
        private Double faceSimilarityScore;
        private Double idCardFaceSimilarityScore;
        private java.util.List<String> reasonCodes;
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

    @Getter
    @Builder
    public static class RoomDetail {
        private String roomId;
        private String livekitUrl;
    }
}
