package com.waddoc.domain.consultation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.consultation.dto.ConsultationSummaryResponse;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
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
    private JwtTokenProvider jwtTokenProvider;

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
