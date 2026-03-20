package com.waddoc.domain.carecase.controller;

import com.waddoc.domain.carecase.dto.CaseDetailResponse;
import com.waddoc.domain.carecase.dto.DoctorCaseListResponse;
import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.consultation.dto.CreateConsultationSessionResponse;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.service.ConsultationSessionCommandService;
import com.waddoc.domain.carecase.service.CareCaseQueryService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CareCaseController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class CareCaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CareCaseQueryService careCaseQueryService;

    @MockBean
    private ConsultationSessionCommandService consultationSessionCommandService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void getCaseDetail_usesDocumentedCasePath() throws Exception {
        when(careCaseQueryService.getCaseDetail(eq("case_test123"), any()))
                .thenReturn(CaseDetailResponse.builder()
                        .caseId("case_test123")
                        .status(CaseStatus.PREPARING)
                        .build());

        mockMvc.perform(get("/api/v1/cases/{caseId}", "case_test123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.caseId").value("case_test123"));
    }

    @Test
    void getAssignedCases_usesDocumentedCasesPath() throws Exception {
        when(careCaseQueryService.getAssignedCases(any(), any(), any()))
                .thenReturn(DoctorCaseListResponse.of(java.util.List.of()));

        mockMvc.perform(get("/api/v1/cases"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void createConsultationSession_returnsCreatedWhenSessionIsNew() throws Exception {
        CreateConsultationSessionResponse response = CreateConsultationSessionResponse.builder()
                .sessionId("ses_test123")
                .caseId("case_test123")
                .status(ConsultationSessionStatus.CREATED)
                .room(CreateConsultationSessionResponse.RoomDetail.builder()
                        .roomId("room_ses_test123")
                        .livekitUrl("wss://livekit.example.com")
                        .build())
                .doctorToken("doctor-token")
                .createdAt(OffsetDateTime.parse("2026-03-18T15:00:00+09:00"))
                .build();
        when(consultationSessionCommandService.createOrReuseSession(eq("case_test123"), any()))
                .thenReturn(new ConsultationSessionCommandService.CreateSessionResult(true, response));

        mockMvc.perform(post("/api/v1/cases/{caseId}/sessions", "case_test123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value("ses_test123"))
                .andExpect(jsonPath("$.room.roomId").value("room_ses_test123"))
                .andExpect(jsonPath("$.doctorToken").value("doctor-token"));
    }

    @Test
    void createConsultationSession_returnsOkWhenSessionIsReused() throws Exception {
        CreateConsultationSessionResponse response = CreateConsultationSessionResponse.builder()
                .sessionId("ses_existing")
                .caseId("case_test123")
                .status(ConsultationSessionStatus.READY)
                .room(CreateConsultationSessionResponse.RoomDetail.builder()
                        .roomId("room_ses_existing")
                        .livekitUrl("wss://livekit.example.com")
                        .build())
                .doctorToken("doctor-token-2")
                .createdAt(OffsetDateTime.parse("2026-03-18T15:00:00+09:00"))
                .build();
        when(consultationSessionCommandService.createOrReuseSession(eq("case_test123"), any()))
                .thenReturn(new ConsultationSessionCommandService.CreateSessionResult(false, response));

        mockMvc.perform(post("/api/v1/cases/{caseId}/sessions", "case_test123")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("ses_existing"))
                .andExpect(jsonPath("$.status").value("READY"));
    }
}
