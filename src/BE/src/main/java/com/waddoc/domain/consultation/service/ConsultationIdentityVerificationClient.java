package com.waddoc.domain.consultation.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.waddoc.domain.consultation.dto.IdentityVerificationResult;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class ConsultationIdentityVerificationClient {

    private final WebClient webClient;
    private final long timeoutMs;

    public ConsultationIdentityVerificationClient(
            WebClient.Builder webClientBuilder,
            @Value("${ai.idv-url}") String idvUrl,
            @Value("${ai.idv-timeout-ms:5000}") long timeoutMs
    ) {
        this.webClient = webClientBuilder.baseUrl(idvUrl).build();
        this.timeoutMs = timeoutMs;
    }

    public IdentityVerificationResult verify(
            String patientId,
            byte[] referenceImage,
            String referenceImageFilename,
            byte[] faceImage,
            String faceImageFilename,
            byte[] idCardImage,
            String idCardImageFilename
    ) {
        String verificationId = "vrf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("verificationId", verificationId);
        bodyBuilder.part("patientId", patientId);
        bodyBuilder.part("verificationMode", "FACE_AND_IDCARD");
        // GPU 서버 계약에 맞춰 기준 이미지, 실시간 얼굴, 신분증 이미지를 모두 보낸다.
        addFilePart(bodyBuilder, "referenceImage", referenceImage, referenceImageFilename);
        addFilePart(bodyBuilder, "faceImage", faceImage, faceImageFilename);
        addFilePart(bodyBuilder, "idCardImage", idCardImage, idCardImageFilename);

        try {
            IdentityVerificationApiResponse response = webClient.post()
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .bodyToMono(IdentityVerificationApiResponse.class)
                    .block(Duration.ofMillis(timeoutMs));

            if (response == null) {
                log.error("Identity verification returned empty response. verificationId={}, patientId={}", verificationId, patientId);
                throw new BusinessException(ErrorCode.AI_IDV_REQUEST_FAILED);
            }
            return response.toResult();
        } catch (BusinessException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("Identity verification request failed. verificationId={}, patientId={}", verificationId, patientId, e);
            throw new BusinessException(ErrorCode.AI_IDV_REQUEST_FAILED);
        }
    }

    private void addFilePart(MultipartBodyBuilder bodyBuilder, String partName, byte[] content, String filename) {
        bodyBuilder.part(partName, new NamedByteArrayResource(content, filename))
                .contentType(MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static Double firstNonNull(Double... values) {
        for (Double value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String extractBirthDate6(String rrnLike) {
        if (rrnLike == null || rrnLike.isBlank()) {
            return null;
        }
        String digits = rrnLike.replaceAll("\\D", "");
        return digits.length() >= 6 ? digits.substring(0, 6) : null;
    }

    private static String maskRrn(String rrn) {
        if (rrn == null || rrn.isBlank()) {
            return null;
        }
        String digits = rrn.replaceAll("\\D", "");
        if (digits.length() < 7) {
            return null;
        }
        return digits.substring(0, 6) + "-" + digits.charAt(6) + "******";
    }

    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class IdentityVerificationApiResponse {
        private String status;
        private Boolean matched;
        private Double similarityScore;
        private Double faceSimilarityScore;
        private Double idCardFaceSimilarityScore;
        private List<String> reasonCodes;
        private Ocr ocr;
        private Matches matches;

        IdentityVerificationResult toResult() {
            boolean resolvedMatched = matched != null
                    ? matched
                    : "SUCCEEDED".equalsIgnoreCase(status);
            String rrnMasked = ocr != null ? firstNonBlank(ocr.getRrnMasked(), maskRrn(ocr.getRrn())) : null;
            String birthDate6 = ocr != null ? firstNonBlank(extractBirthDate6(ocr.getRrn()), extractBirthDate6(ocr.getRrnMasked())) : null;

            // GPU 서버 응답 포맷 차이를 흡수해 서비스 계층은 고정된 DTO만 보도록 맞춘다.
            return IdentityVerificationResult.builder()
                    .matched(resolvedMatched)
                    .faceSimilarityScore(firstNonNull(faceSimilarityScore, similarityScore, matches != null ? matches.getLiveVsRegisteredScore() : null))
                    .idCardFaceSimilarityScore(firstNonNull(
                            idCardFaceSimilarityScore,
                            matches != null ? matches.getLiveVsIdCardFaceScore() : null
                    ))
                    .reasonCodes(reasonCodes != null ? reasonCodes : List.of())
                    .ocr(IdentityVerificationResult.OcrData.builder()
                            .name(ocr != null ? ocr.getName() : null)
                            .rrnMasked(rrnMasked)
                            .birthDate6(birthDate6)
                            .address(ocr != null ? ocr.getAddress() : null)
                            .build())
                    .build();
        }
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Matches {
        private Double liveVsRegisteredScore;
        private Double liveVsIdCardFaceScore;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Ocr {
        private String name;
        private String rrn;
        private String rrnMasked;
        private String address;
    }
}
