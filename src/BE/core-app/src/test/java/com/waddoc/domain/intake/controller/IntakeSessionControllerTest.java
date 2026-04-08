package com.waddoc.domain.intake.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.intake.dto.BindPatientResponse;
import com.waddoc.domain.intake.dto.CompleteSessionRequest;
import com.waddoc.domain.intake.dto.CompleteSessionResponse;
import com.waddoc.domain.intake.dto.BindPatientRequest;
import com.waddoc.domain.intake.entity.CompletionReason;
import com.waddoc.domain.intake.entity.IntakeStatus;
import com.waddoc.domain.intake.service.IntakeSessionService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IntakeSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class IntakeSessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IntakeSessionService intakeSessionService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void updateSession_bindsPatientWithDocumentedPatchContract() throws Exception {
        String intakeSessionId = "ints_test123";

        when(intakeSessionService.bindPatient(eq(intakeSessionId), any(BindPatientRequest.class)))
                .thenReturn(BindPatientResponse.builder()
                        .intakeSessionId(intakeSessionId)
                        .patientId("pat_test123")
                        .status(IntakeStatus.IN_PROGRESS)
                        .lastActivityAt(OffsetDateTime.parse("2026-03-17T10:00:00+09:00"))
                        .build());

        mockMvc.perform(patch("/api/v1/intake/sessions/{intakeSessionId}", intakeSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientId": "pat_test123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intakeSessionId").value(intakeSessionId))
                .andExpect(jsonPath("$.patientId").value("pat_test123"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void updateSession_completesSessionWithDocumentedPatchContract() throws Exception {
        String intakeSessionId = "ints_test123";

        when(intakeSessionService.completeSession(eq(intakeSessionId), any(CompleteSessionRequest.class)))
                .thenReturn(CompleteSessionResponse.builder()
                        .intakeSessionId(intakeSessionId)
                        .status(IntakeStatus.COMPLETED)
                        .endedAt(OffsetDateTime.parse("2026-03-17T10:08:00+09:00"))
                        .build());

        mockMvc.perform(patch("/api/v1/intake/sessions/{intakeSessionId}", intakeSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new PatchCompleteRequest(
                                IntakeStatus.COMPLETED,
                                CompletionReason.BOOKING_CREATED
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intakeSessionId").value(intakeSessionId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.endedAt").exists());
    }

    @Test
    void updateSession_rejectsMixedPatchPayload() throws Exception {
        String intakeSessionId = "ints_test123";

        mockMvc.perform(patch("/api/v1/intake/sessions/{intakeSessionId}", intakeSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "patientId": "pat_test123",
                                  "status": "COMPLETED",
                                  "completionReason": "BOOKING_CREATED"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PATCH_REQUEST"));

        verifyNoInteractions(intakeSessionService);
    }

    private record PatchCompleteRequest(
            IntakeStatus status,
            CompletionReason completionReason
    ) {
    }
}
