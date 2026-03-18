package com.waddoc.domain.consultation.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class IdentityVerificationResult {

    private boolean matched;
    private Double faceSimilarityScore;
    private Double idCardFaceSimilarityScore;
    private List<String> reasonCodes;
    private OcrData ocr;

    @Getter
    @Builder
    public static class OcrData {
        private String name;
        private String rrnMasked;
        private String birthDate6;
        private String address;
    }
}
