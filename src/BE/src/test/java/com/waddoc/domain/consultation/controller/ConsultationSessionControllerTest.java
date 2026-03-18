package com.waddoc.domain.consultation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.consultation.dto.ConsultationSummaryResponse;
import com.waddoc.domain.consultation.dto.IssuePatientTokenResponse;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.service.ConsultationPatientTokenService;
import com.waddoc.domain.consultation.service.ConsultationWebhookService;
import com.waddoc.domain.consultation.service.ConsultationSummaryService;
import com.waddoc.global.error.GlobalExceptionHandler;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsultationSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ConsultationSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConsultationSummaryService consultationSummaryService;

    @MockBean
    private ConsultationWebhookService consultationWebhookService;

    @MockBean
    private ConsultationPatientTokenService consultationPatientTokenService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void handleLiveKitWebhook_usesDocumentedWebhookPath() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/webhook/livekit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "signed-webhook-token")
                        .content("""
                                {
                                  "event": "room_finished",
                                  "room": {
                                    "name": "room_ses_test123"
                                  }
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void getSummary_usesDocumentedGetPath() throws Exception {
        String sessionId = "ses_test123";

        when(consultationSummaryService.getSummary(eq(sessionId), any()))
                .thenReturn(ConsultationSummaryResponse.builder()
                        .sessionId(sessionId)
                        .caseId("case_test123")
                        .status(ConsultationSessionStatus.COMPLETED)
                        .summary(ConsultationSummaryResponse.SummaryDetail.builder()
                                .summaryNote("편두통 소견")
                                .prescriptionIssued(true)
                                .prescriptionNote("타이레놀 500mg")
                                .needsFollowUp(true)
                                .build())
                        .endedAt(OffsetDateTime.parse("2026-03-18T10:25:00+09:00"))
                        .durationMinutes(25)
                        .build());

        mockMvc.perform(get("/api/v1/sessions/{sessionId}/summary", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.summary.summaryNote").value("편두통 소견"))
                .andExpect(jsonPath("$.summary.isPrescriptionIssued").value(true))
                .andExpect(jsonPath("$.durationMinutes").value(25));
    }

    @Test
    void issuePatientToken_usesDocumentedMultipartPath() throws Exception {
        String sessionId = "ses_test123";

        when(consultationPatientTokenService.issuePatientToken(eq(sessionId), eq("pat_test123"), any(), any(), any()))
                .thenReturn(IssuePatientTokenResponse.builder()
                        .sessionId(sessionId)
                        .patientToken("patient-token")
                        .expiresIn(7200)
                        .identityCheck(IssuePatientTokenResponse.IdentityCheckDetail.builder()
                                .matched(true)
                                .faceSimilarityScore(0.94)
                                .idCardFaceSimilarityScore(0.91)
                                .reasonCodes(java.util.List.of())
                                .ocr(IssuePatientTokenResponse.OcrDetail.builder()
                                        .name("홍길동")
                                        .rrnMasked("580315-1******")
                                        .address("김천시 증산면 장전1길 69")
                                        .build())
                                .build())
                        .room(IssuePatientTokenResponse.RoomDetail.builder()
                                .roomId("room_ses_test123")
                                .livekitUrl("wss://livekit.example.com")
                                .build())
                        .build());

        MockMultipartFile patientId = new MockMultipartFile("patientId", "", MediaType.TEXT_PLAIN_VALUE, "pat_test123".getBytes());
        MockMultipartFile faceImage = new MockMultipartFile("faceImage", "face.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});
        MockMultipartFile idCardImage = new MockMultipartFile("idCardImage", "id-card.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{4, 5, 6});

        mockMvc.perform(multipart("/api/v1/sessions/{sessionId}/participants/patient/token", sessionId)
                        .file(patientId)
                        .file(faceImage)
                        .file(idCardImage))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.patientToken").value("patient-token"))
                .andExpect(jsonPath("$.identityCheck.matched").value(true))
                .andExpect(jsonPath("$.room.roomId").value("room_ses_test123"));
    }

    @Test
    void saveSummary_usesDocumentedPutPath() throws Exception {
        String sessionId = "ses_test123";

        when(consultationSummaryService.saveSummary(eq(sessionId), any(), any()))
                .thenReturn(ConsultationSummaryResponse.builder()
                        .sessionId(sessionId)
                        .caseId("case_test123")
                        .status(ConsultationSessionStatus.COMPLETED)
                        .summary(ConsultationSummaryResponse.SummaryDetail.builder()
                                .summaryNote("편두통 소견")
                                .prescriptionIssued(true)
                                .prescriptionNote("타이레놀 500mg")
                                .needsFollowUp(true)
                                .build())
                        .endedAt(OffsetDateTime.parse("2026-03-18T10:25:00+09:00"))
                        .durationMinutes(25)
                        .build());

        mockMvc.perform(put("/api/v1/sessions/{sessionId}/summary", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "summaryNote": "편두통 소견",
                                  "isPrescriptionIssued": true,
                                  "prescriptionNote": "타이레놀 500mg",
                                  "needsFollowUp": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.summary.summaryNote").value("편두통 소견"))
                .andExpect(jsonPath("$.summary.isPrescriptionIssued").value(true));
    }

    @Test
    void saveSummary_rejectsBlankSummaryNote() throws Exception {
        String sessionId = "ses_test123";

        mockMvc.perform(put("/api/v1/sessions/{sessionId}/summary", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new InvalidRequest(" "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));

        verifyNoInteractions(consultationSummaryService);
    }

    private record InvalidRequest(String summaryNote) {
    }
}
