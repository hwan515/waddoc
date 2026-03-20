package com.waddoc.domain.consultation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.consultation.dto.ConsultationSessionStatusResponse;
import com.waddoc.domain.consultation.dto.ConsultationSummaryResponse;
import com.waddoc.domain.consultation.dto.IssuePatientTokenResponse;
import com.waddoc.domain.consultation.dto.ReissueConsultationTokenResponse;
import com.waddoc.domain.consultation.entity.ConnectionState;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.service.ConsultationPatientTokenService;
import com.waddoc.domain.consultation.service.ConsultationSessionQueryService;
import com.waddoc.domain.consultation.service.ConsultationSessionTokenService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private ConsultationSessionQueryService consultationSessionQueryService;

    @MockBean
    private ConsultationSessionTokenService consultationSessionTokenService;

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
    void issuePatientToken_usesDocumentedJsonPath() throws Exception {
        String sessionId = "ses_test123";

        when(consultationPatientTokenService.issuePatientToken(eq(sessionId), eq("pat_test123"), any()))
                .thenReturn(IssuePatientTokenResponse.builder()
                        .sessionId(sessionId)
                        .patientToken("patient-token")
                        .expiresIn(7200)
                        .room(IssuePatientTokenResponse.RoomDetail.builder()
                                .roomId("room_ses_test123")
                                .livekitUrl("wss://livekit.example.com")
                                .build())
                        .build());

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/participants/patient/token", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientId": "pat_test123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.patientToken").value("patient-token"))
                .andExpect(jsonPath("$.identityCheck").doesNotExist())
                .andExpect(jsonPath("$.room.roomId").value("room_ses_test123"));
    }

    @Test
    void getSessionStatus_usesDocumentedGetPath() throws Exception {
        String sessionId = "ses_test123";

        when(consultationSessionQueryService.getSessionStatus(eq(sessionId), any()))
                .thenReturn(ConsultationSessionStatusResponse.builder()
                        .sessionId(sessionId)
                        .caseId("case_test123")
                        .status(ConsultationSessionStatus.IN_PROGRESS)
                        .room(ConsultationSessionStatusResponse.RoomDetail.builder()
                                .roomId("room_ses_test123")
                                .livekitUrl("wss://livekit.example.com")
                                .build())
                        .doctor(ConsultationSessionStatusResponse.DoctorDetail.builder()
                                .doctorId("doc_test123")
                                .name("이국종")
                                .connectionState(ConnectionState.CONNECTED)
                                .joinedAt(OffsetDateTime.parse("2026-03-18T10:00:30+09:00"))
                                .build())
                        .patient(ConsultationSessionStatusResponse.PatientDetail.builder()
                                .patientId("pat_test123")
                                .name("홍길동")
                                .connectionState(ConnectionState.CONNECTED)
                                .joinedAt(OffsetDateTime.parse("2026-03-18T10:01:00+09:00"))
                                .build())
                        .reconnectCount(0)
                        .startedAt(OffsetDateTime.parse("2026-03-18T10:00:00+09:00"))
                        .build());

        mockMvc.perform(get("/api/v1/sessions/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.doctor.connectionState").value("CONNECTED"))
                .andExpect(jsonPath("$.patient.patientId").value("pat_test123"))
                .andExpect(jsonPath("$.reconnectCount").value(0));
    }

    @Test
    void reissueToken_usesDocumentedPostPath() throws Exception {
        String sessionId = "ses_test123";

        when(consultationSessionTokenService.reissueToken(eq(sessionId), any(), any()))
                .thenReturn(ReissueConsultationTokenResponse.builder()
                        .token("reissued-token")
                        .expiresIn(7200)
                        .build());

        mockMvc.perform(post("/api/v1/sessions/{sessionId}/token", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "participantType": "DOCTOR",
                                  "patientId": null
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("reissued-token"))
                .andExpect(jsonPath("$.expiresIn").value(7200));
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
