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
        // 기준 이미지가 있으면 등록 얼굴과 실시간 얼굴까지 함께 검증하고, 없으면 실시간 얼굴-신분증 얼굴 및 OCR만 검증한다.
        addOptionalFilePart(bodyBuilder, "referenceImage", referenceImage, referenceImageFilename);
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
            IdentityVerificationResult result = response.toResult();
            logIdentityVerificationResponse(verificationId, patientId, response, result);
            return result;
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

    private void addOptionalFilePart(MultipartBodyBuilder bodyBuilder, String partName, byte[] content, String filename) {
        if (content == null || filename == null || filename.isBlank()) {
            return;
        }
        addFilePart(bodyBuilder, partName, content, filename);
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

    private void logIdentityVerificationResponse(
            String verificationId,
            String patientId,
            IdentityVerificationApiResponse response,
            IdentityVerificationResult result
    ) {
        IdentityVerificationResult.OcrData ocr = result.getOcr();
        log.info(
                "Identity verification response received. verificationId={}, patientId={}, status={}, matched={}, faceSimilarityScore={}, idCardFaceSimilarityScore={}, reasonCodes={}, ocrNameMasked={}, ocrRrnMasked={}, ocrBirthDate6Masked={}, ocrAddressMasked={}",
                verificationId,
                patientId,
                response.getStatus(),
                result.isMatched(),
                result.getFaceSimilarityScore(),
                result.getIdCardFaceSimilarityScore(),
                result.getReasonCodes(),
                maskName(ocr != null ? ocr.getName() : null),
                ocr != null ? ocr.getRrnMasked() : null,
                maskBirthDate6(ocr != null ? ocr.getBirthDate6() : null),
                maskAddress(ocr != null ? ocr.getAddress() : null)
        );
    }

    private static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (name.length() == 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*" + name.charAt(name.length() - 1);
    }

    private static String maskBirthDate6(String birthDate6) {
        if (birthDate6 == null || birthDate6.isBlank()) {
            return null;
        }
        return birthDate6.length() >= 2 ? birthDate6.substring(0, 2) + "****" : "**";
    }

    private static String maskAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String normalized = address.replaceAll("\\s+", " ").trim();
        String[] tokens = normalized.split(" ");
        if (tokens.length >= 2) {
            return tokens[0] + " " + tokens[1] + " ...";
        }
        return tokens[0] + " ...";
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
